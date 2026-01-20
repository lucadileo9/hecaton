package com.hecaton.scheduler;

import com.hecaton.rmi.NodeService;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskScheduler manages the lifecycle of tasks in a distributed job execution system.
 * 
 * Architecture:
 * Uses a simplified 2-map design with JobContext aggregation:
 *   - jobs: Map< String, JobContext> ; - The "truth" of the system
 *   - workerIndex: Map < String, Set<String>>; - Worker failure lookup table
 * 
 * JobContext Structure (per job):
 *   - assignments: "Cassaforte" - immutable task assignments for recovery
 *   - taskStatus: "Lavagna" - current state (WORKING/COMPLETED/FAILED/etc)
 *   - pendingTasks: "Contatore" - atomic countdown to job completion (O(1))
 * 
 * Lifecycle:
 * 1. JobManager calls scheduleTasks() with task assignments
 * 2. TaskScheduler dispatches tasks to workers via executeTasks() RMI
 * 3. Workers execute and call submitResults() on Leader
 * 4. TaskScheduler updates task states and notifies JobManager on completion (O(1) check)
 * 
 * N.B.: If a worker fails, TaskScheduler is notified by FailureDetector to reassign orphaned tasks.
 * 
 * @see JobManager
 * @see com.hecaton.monitor.FailureDetector
 */
public class TaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    
    /**
     * Job execution context - aggregates all state for a single job.
     * 
     * Design Philosophy:
     *   - Cassaforte (assignments): Immutable "backup" for worker failure recovery
     *   - Lavagna (taskStatus): Mutable current state, updated constantly
     *   - Contatore (pendingTasks): Atomic counter for O(1) completion check
     *   - Terminated flag: Tracks early termination state
     */
    private static class JobContext {
        // LA "CASSAFORTE" (Read-Only after creation)
        // Contains the original tasks grouped by worker.
        // Used ONLY if a worker dies. Otherwise, it is never read.
        // Key: workerId, Value: list of tasks assigned to that worker
        final Map<String, List<Task>> assignments;
        
        // LA "LAVAGNA" (Write-Heavy)
        // Contains the current state of each task.
        // WORKING → task dispatched, in execution
        // SUCCESS/NOT_FOUND/FAILURE/CANCELLED → task completed
        // Key: taskId, Value: current TaskResult
        final Map<String, TaskResult> taskStatus;
        // Ideally here we could use a simple list, but using a map with taskId as key
        // allows O(1) access to individual task status when updating the result.
        
        // IL "CONTATORE VELOCE" (Atomic Decrement)
        // When it reaches 0, the job is finished.
        // Avoids having to count how many "COMPLETED" there are on the board each time.
        final AtomicInteger pendingTasks;
        
        // EARLY TERMINATION FLAG
        // Set to true when a task result is found and job supports early termination.
        // Prevents double-triggering of termination logic and blocks task reassignment.
        volatile boolean terminated = false;
        
        JobContext(Map<String, List<Task>> assignments, int totalTaskCount) {
            this.assignments = new HashMap<>(assignments);  // Defensive copy
            this.taskStatus = new ConcurrentHashMap<>();
            this.pendingTasks = new AtomicInteger(totalTaskCount);
        }
    }
    
    // ==================== State Maps ====================
    
    // THE JOB DATABASE
    // Map<JobId, JobContext>
    // It is the "truth" of the system. All state for every job is here
    // Since the TaskScheduler could manage multiple jobs simultaneously,
    // we need to keep track of each job's context separately.
    private final Map<String, JobContext> jobs = new ConcurrentHashMap<>();


    // THE ROUTING INDEX
    // Map<WorkerId, Set<JobId>>
    // Technical lookup table. Answers: "If worker X fails, which jobs to check?"
    private final Map<String, Set<String>> workerIndex = new ConcurrentHashMap<>();
    
    // ==================== Dependencies ====================
    
    private final JobManager jobManager;  // callback for job completion
    
    /**
     * Constructs a TaskScheduler with required dependencies.
     * 
     * @param jobManager callback interface for job completion notifications
     */
    public TaskScheduler(JobManager jobManager) {
        this.jobManager = Objects.requireNonNull(jobManager, "jobManager cannot be null");
        logger.info("TaskScheduler initialized");
    }
    
    /**
     * Schedules tasks for execution by dispatching them to assigned workers.
     * 
     * This method:
     *   - Creates JobContext with assignments (Cassaforte)
     *   - Initializes taskStatus map with WORKING state (Lavagna)
     *   - Sets pendingTasks counter (Contatore)
     *   - Updates workerIndex for failure recovery
     *   - Dispatches tasks to workers via RMI executeTasks()
    
     * 
     * @param jobId unique job identifier
     * @param taskAssignments map of workerId → list of tasks assigned to that worker
     * @throws IllegalArgumentException if jobId is null/empty or taskAssignments is null
     */
    public void scheduleTasks(String jobId, Map<String, List<Task>> taskAssignments) {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("jobId cannot be null or empty");
        }
        if (taskAssignments == null) {
            throw new IllegalArgumentException("taskAssignments cannot be null");
        }
        
        // Count total tasks
        int totalTasks = taskAssignments.values().stream()
                                        .mapToInt(List::size)
                                        .sum();
        
        logger.info("Scheduling job={}, workers={}, totalTasks={}", 
                    jobId, taskAssignments.size(), totalTasks);
        
        // Create JobContext (Cassaforte + Contatore) (Lavagna initialized next)
        JobContext ctx = new JobContext(taskAssignments, totalTasks);
        
        // Initialize Lavagna: all tasks start as WORKING
        // Remember that taskAssignments is Map<workerId, List<Task>>, 
        for (Map.Entry<String, List<Task>> entry : taskAssignments.entrySet()) { // so we iterate over every worker
            for (Task task : entry.getValue()) { // then over every task assigned to that worker
                ctx.taskStatus.put(task.getTaskId(), TaskResult.working(jobId, task.getTaskId()));
            }
        }
        
        // Store JobContext
        jobs.put(jobId, ctx);
        
        // Update workerIndex (for failure recovery lookup)
        for (String workerId : taskAssignments.keySet()) {
            workerIndex.computeIfAbsent(workerId, k -> ConcurrentHashMap.newKeySet())
                       .add(jobId);
        }
        
        logger.debug("Job {} registered: {} tasks WORKING, pendingCounter={}", 
                     jobId, totalTasks, totalTasks);
        
        // Dispatch tasks to workers (NO LOCK - RMI can be slow)
        for (Map.Entry<String, List<Task>> entry : taskAssignments.entrySet()) {
            String workerId = entry.getKey();
            List<Task> tasks = entry.getValue();
            dispatchTasksToWorker(workerId, tasks);
        }
    }
    
    /**
     * Dispatches a batch of tasks to a specific worker via RMI.
     * 
     * Tasks are already marked as WORKING in scheduleTasks(), so this method
     * only performs the RMI call. If RMI fails, tasks remain WORKING (will be
     * reassigned when worker is detected as dead by FailureDetector).
     * 
     * @param workerId target worker node ID
     * @param tasks list of tasks to dispatch
     */
    private void dispatchTasksToWorker(String workerId, List<Task> tasks) {
        if (tasks.isEmpty()) {
            logger.debug("No tasks to dispatch to worker={}", workerId);
            return;
        }
        
        logger.info("Dispatching {} tasks to worker={}", tasks.size(), workerId);
        
        try {
            // Parse workerId to get host:port
            // Expected format: "node-host-port-timestamp"
            String[] parts = workerId.split("-");
            if (parts.length < 3) {
                logger.error("Invalid workerId format: {} (expected node-host-port-timestamp)", workerId);
                return;
            }
            
            String host = parts[1];
            int port = Integer.parseInt(parts[2]);
            
            // Connect to worker's RMI registry
            Registry registry = LocateRegistry.getRegistry(host, port);
            NodeService worker = (NodeService) registry.lookup("node");
            
            // RMI call to dispatch tasks
            worker.executeTasks(tasks);
            logger.debug("Successfully dispatched {} tasks to worker={} via RMI", tasks.size(), workerId);
            
        } catch (RemoteException e) {
            logger.error("RMI error dispatching tasks to worker={}: {}", workerId, e.getMessage());
            // Tasks remain WORKING - will be reassigned when worker detected as dead
        } catch (Exception e) {
            logger.error("Error dispatching tasks to worker={}", workerId, e);
        }
    }
    
    /**
     * Handles task completion notifications from a worker.
     * 
     * Worker sends ALL its results in one batch. This method:
     *   - Updates Lavagna (taskStatus) with completed results
     *   - Decrements Contatore (pendingTasks) for each task
     *   - Checks if pendingTasks == 0 → notifies JobManager (O(1) check!)
     * 
     * Assumption: All results in the batch belong to the SAME job.
     * Workers receive tasks from a single job via executeTasks(), and submit
     * all results together. This assumption simplifies the implementation
     * (no grouping needed).
     * 
     * @param results list of task execution results from worker
     * @throws IllegalArgumentException if results is null or empty
     */
    public void submitResults(List<TaskResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results cannot be null or empty");
        }
        
        // ASSUMPTION: All results belong to the same job (one batch = one job)
        String jobId = results.get(0).getJobId();
        logger.info("Received {} results for job={}", results.size(), jobId);
        
        // NOTE: If in the future a worker can process multiple jobs simultaneously,
        // uncomment this code to group results by jobId:
        //
        // Map<String, List<TaskResult>> resultsByJob = new HashMap<>();
        // for (TaskResult result : results) {
        //     resultsByJob.computeIfAbsent(result.getJobId(), k -> new ArrayList<>())
        //                 .add(result);
        // }
        // for (Map.Entry<String, List<TaskResult>> entry : resultsByJob.entrySet()) {
        //     String jobId = entry.getKey();
        //     List<TaskResult> jobResults = entry.getValue();
        //     processJobResults(jobId, jobResults);
        // }
        
        JobContext ctx = jobs.get(jobId);
        if (ctx == null) {
            logger.warn("Received results for unknown job={}", jobId);
            return;
        }
        
        // Process all results (single job)
        boolean earlyTerminationTriggered = false;
        
        for (TaskResult result : results) {
            String taskId = result.getTaskId();
            
            // Paranoid validation: verify all results belong to the expected job
            if (!result.getJobId().equals(jobId)) {
                logger.error("INVARIANT VIOLATION: result taskId={} belongs to job={}, expected job={}",
                           taskId, result.getJobId(), jobId);
                continue;
            }
            
            // Check if task already completed (duplicate submission)
            TaskResult current = ctx.taskStatus.get(taskId);
            if (current != null && !current.isWorking()) {
                logger.warn("Received duplicate result for task={} (already {})", 
                           taskId, current.getStatus());
                continue;
            }
            
            // Update Lavagna: WORKING → SUCCESS/NOT_FOUND/FAILURE/CANCELLED
            ctx.taskStatus.put(taskId, result);
            logger.debug("Task {} → {}", taskId, result.getStatus());
            
            // Decrement Contatore (atomic, lock-free!)
            int remaining = ctx.pendingTasks.decrementAndGet();
            logger.debug("Job {} pendingTasks: {} remaining", jobId, remaining);
            
            // EARLY TERMINATION DETECTION
            // Check if result contains data (e.g., password found) and job supports early termination
            if (!earlyTerminationTriggered && result.hasResult()) {
                logger.info("Result found in task={}, checking early termination support", taskId);
                if (supportsEarlyTermination(jobId)) {
                    logger.info("Early termination supported, triggering job={} cancellation", jobId);
                    terminateJob(jobId, ctx);
                    earlyTerminationTriggered = true;
                    // Continue processing remaining results (might be in-flight)
                }
            }
            
            // O(1) Check: job finito?
            if (remaining == 0) {
                logger.info("Job {} COMPLETE (all tasks finished)", jobId);
                notifyJobComplete(jobId, ctx);
            }
        }
    }
    
    /**
     * Triggers early termination for a job.
     * 
     * This method:
     *   1. Marks job as terminated (prevents double-triggering)
     *   2. Marks all WORKING tasks as CANCELLED in taskStatus
     *   3. Broadcasts cancelJob() RMI to all workers involved in this job
     * 
     * Called when a task result is found and the job supports early termination.
     * 
     * @param jobId job identifier
     * @param ctx JobContext for the job
     */
    private void terminateJob(String jobId, JobContext ctx) {
        // Check if already terminated (idempotency)
        if (ctx.terminated) {
            logger.debug("Job {} already terminated, ignoring duplicate trigger", jobId);
            return;
        }
        
        logger.info("[EARLY TERMINATION] Terminating job={}", jobId);
        ctx.terminated = true;
        
        // Broadcast cancellation to all workers involved in this job
        Set<String> workers = getWorkersForJob(jobId);
        logger.info("[EARLY TERMINATION] Broadcasting cancelJob({}) to {} workers", jobId, workers.size());
        
        for (String workerId : workers) {
            notifyCancellationToWorker(workerId, jobId);
        }
        
        // Mark all WORKING tasks as CANCELLED in Lavagna
        int cancelledCount = 0;
        for (Map.Entry<String, TaskResult> entry : ctx.taskStatus.entrySet()) {
            TaskResult status = entry.getValue();
            if (status.isWorking()) {
                String taskId = entry.getKey();
                entry.setValue(TaskResult.cancelled(jobId, taskId));
                cancelledCount++;
            }
        }
        
        logger.info("[EARLY TERMINATION] Marked {} tasks as CANCELLED for job={}", cancelledCount, jobId);
        
        // Decrement pending counter by number of cancelled tasks
        // This unblocks waitForCompletion() in JobManager
        int remaining = ctx.pendingTasks.addAndGet(-cancelledCount);
        logger.debug("[EARLY TERMINATION] Decremented pendingTasks by {}, remaining={}", cancelledCount, remaining);
        
        // Check if job is now complete (all tasks accounted for)
        if (remaining == 0) {
            logger.info("[EARLY TERMINATION] Job {} COMPLETE (all tasks finished/cancelled)", jobId);
            notifyJobComplete(jobId, ctx);
        } 
    }
    
    /**
     * Checks if a job supports early termination.
     * Delegates to JobManager which maintains Job references.
     * 
     * @param jobId job identifier
     * @return true if job supports early termination
     */
    private boolean supportsEarlyTermination(String jobId) {
        return jobManager.supportsEarlyTermination(jobId);
    }
    
    /**
     * Finds all workers assigned to a specific job.
     * Inverts the workerIndex lookup: searches all workers for those containing this jobId.
     * 
     * @param jobId job identifier
     * @return set of worker IDs involved in this job
     */
    private Set<String> getWorkersForJob(String jobId) {
        Set<String> workers = new HashSet<>();
        
        // workerIndex is Map<WorkerId, Set<JobId>>
        // We need to find all workers that have this jobId in their set
        for (Map.Entry<String, Set<String>> entry : workerIndex.entrySet()) {
            String workerId = entry.getKey();
            Set<String> jobIds = entry.getValue();
            
            if (jobIds.contains(jobId)) {
                workers.add(workerId);
            }
        }
        
        logger.debug("Found {} workers for job={}: {}", workers.size(), jobId, workers);
        return workers;
    }
    
    /**
     * Sends cancelJob() RMI call to a specific worker.
     * Uses the same connection pattern as dispatchTasksToWorker().
     * 
     * Best-effort operation: logs warning if worker unreachable, but continues.
     * Worker might be dead (will be detected by FailureDetector) or network issue.
     * 
     * @param workerId target worker node ID
     * @param jobId job to cancel
     */
    private void notifyCancellationToWorker(String workerId, String jobId) {
        try {
            // Parse workerId to get host:port
            // Expected format: "node-host-port-timestamp"
            String[] parts = workerId.split("-");
            if (parts.length < 3) {
                logger.error("Invalid workerId format: {} (expected node-host-port-timestamp)", workerId);
                return;
            }
            
            String host = parts[1];
            int port = Integer.parseInt(parts[2]);
            
            // Connect to worker's RMI registry
            Registry registry = LocateRegistry.getRegistry(host, port);
            NodeService worker = (NodeService) registry.lookup("node");
            
            // RMI call to cancel job
            worker.cancelJob(jobId);
            logger.info("[EARLY TERMINATION] Successfully sent cancelJob({}) to worker={}", jobId, workerId);
            
        } catch (RemoteException e) {
            logger.warn("[EARLY TERMINATION] RMI error sending cancelJob({}) to worker={}: {}", 
                       jobId, workerId, e.getMessage());
            // Worker might be dead - FailureDetector will handle cleanup
        } catch (Exception e) {
            logger.error("[EARLY TERMINATION] Error sending cancelJob({}) to worker={}", 
                        jobId, workerId, e);
        }
    }
    
    /**
     * Notifies JobManager that all tasks in a job are complete.
     * Collects all task results from Lavagna and passes them to JobManager.
     * 
     * @param jobId job identifier
     * @param ctx JobContext containing all task results
     */
    private void notifyJobComplete(String jobId, JobContext ctx) {
        try {
            // Collect all results from Lavagna
            List<TaskResult> allResults = new ArrayList<>(ctx.taskStatus.values());
            
            logger.info("Notifying JobManager: job={}, total results={}", jobId, allResults.size());
            jobManager.onJobFinished(jobId, allResults);
            logger.info("Successfully notified JobManager of job={} completion", jobId);
            
            // Cleanup (optional - could keep for debugging/metrics)
            // jobs.remove(jobId);
            // for (String workerId : ctx.assignments.keySet()) {
            //     Set<String> jobIds = workerIndex.get(workerId);
            //     if (jobIds != null) jobIds.remove(jobId);
            // }
            
        } catch (Exception e) {
            logger.error("Error notifying JobManager for job={}", jobId, e);
        }
    }
    
    /**
     * Handles worker failure by reassigning orphaned tasks.
     * 
     * Uses workerIndex to find affected jobs (O(1) lookup), then:
     *   - Looks in Cassaforte (assignments) for worker's tasks
     *   - Checks Lavagna (taskStatus) to find tasks still WORKING
     *   - Reassigns those tasks to healthy workers
     * 
     * TODO: Implement automatic reassignment logic.
     * Currently, tasks are identified but not re-dispatched.
     * 
     * @param workerId ID of the failed worker
     */
    public void onWorkerFailed(String workerId) {
        logger.warn("Worker failed: {}, checking for orphaned tasks", workerId);
        
        // O(1) lookup: quali job aveva questo worker?
        Set<String> affectedJobs = workerIndex.get(workerId);
        if (affectedJobs == null || affectedJobs.isEmpty()) {
            logger.info("No jobs assigned to worker={}", workerId);
            return;
        }
        
        logger.warn("Worker {} was working on {} jobs", workerId, affectedJobs.size());
        
        int totalOrphaned = 0;
        
        for (String jobId : affectedJobs) {
            JobContext ctx = jobs.get(jobId);
            if (ctx == null) {
                logger.warn("Job {} no longer exists (already completed?)", jobId);
                continue;
            }
            
            // Skip reassignment if job was terminated via early termination
            if (ctx.terminated) {
                logger.info("Job {} is terminated (early termination), skipping reassignment", jobId);
                continue;
            }
            
            // Cassaforte: cosa aveva questo worker originariamente?
            List<Task> originalTasks = ctx.assignments.get(workerId);
            if (originalTasks == null) {
                logger.warn("Worker {} had no tasks in job={} (inconsistent state)", workerId, jobId);
                continue;
            }
            
            // Lavagna: quali sono ancora WORKING (non completate)?
            List<Task> orphanedTasks = new ArrayList<>();
            for (Task task : originalTasks) {
                TaskResult status = ctx.taskStatus.get(task.getTaskId());
                if (status != null && status.isWorking()) {
                    orphanedTasks.add(task);
                }
            }
            
            if (orphanedTasks.isEmpty()) {
                logger.info("All tasks from worker={} in job={} were already completed", 
                           workerId, jobId);
                continue;
            }
            
            logger.warn("Found {} orphaned tasks in job={} from worker={}", 
                       orphanedTasks.size(), jobId, workerId);
            totalOrphaned += orphanedTasks.size();
            
            // Reassign orphaned tasks using JobManager's strategy
            logger.info("Requesting task reassignment from JobManager for {} orphaned tasks", 
                       orphanedTasks.size());
            
            Map<String, List<Task>> newAssignments = jobManager.reassignTasks(orphanedTasks);
            
            if (newAssignments.isEmpty()) {
                logger.error("JobManager returned empty assignments - no healthy workers available!");
                continue;
            }
            
            // Update assignments in Cassaforte (remove from old worker, add to new workers)
            ctx.assignments.remove(workerId);
            for (Map.Entry<String, List<Task>> entry : newAssignments.entrySet()) {
                String newWorkerId = entry.getKey();
                List<Task> tasks = entry.getValue();
                
                // Add to new worker's assignment list (or merge if already has some)
                ctx.assignments.merge(newWorkerId, tasks, (existing, newTasks) -> {
                    existing.addAll(newTasks);
                    return existing;
                });
                
                logger.info("Reassigned {} tasks to worker={}", tasks.size(), newWorkerId);
            }
            
            // Dispatch tasks to new workers
            for (Map.Entry<String, List<Task>> entry : newAssignments.entrySet()) {
                String newWorkerId = entry.getKey();
                List<Task> tasks = entry.getValue();
                dispatchTasksToWorker(newWorkerId, tasks);
            }
            
            // Update workerIndex (remove old worker, add new workers)
            Set<String> jobIds = workerIndex.remove(workerId);
            for (String newWorkerId : newAssignments.keySet()) {
                workerIndex.computeIfAbsent(newWorkerId, k -> ConcurrentHashMap.newKeySet()).add(jobId);
            }
            
            logger.info("Successfully reassigned {} orphaned tasks in job={}", 
                       orphanedTasks.size(), jobId);
        }
        
        if (totalOrphaned > 0) {
            logger.error("Total orphaned tasks across all jobs: {} (MANUAL INTERVENTION REQUIRED)", 
                        totalOrphaned);
        }
    }
    
    // ==================== Query Methods (for debugging/monitoring) ====================
    
    /**
     * Gets statistics for a job.
     * 
     * @param jobId job identifier
     * @return map of status → count, or null if job not found
     */
    public Map<TaskResult.Status, Integer> getJobStatistics(String jobId) {
        JobContext ctx = jobs.get(jobId);
        if (ctx == null) {
            return null;
        }
        
        Map<TaskResult.Status, Integer> stats = new EnumMap<>(TaskResult.Status.class);
        for (TaskResult.Status status : TaskResult.Status.values()) {
            stats.put(status, 0);
        }
        
        for (TaskResult result : ctx.taskStatus.values()) {
            stats.merge(result.getStatus(), 1, Integer::sum);
        }
        
        return stats;
    }
    
    /**
     * Gets current status of a task.
     * 
     * @param taskId task identifier
     * @return TaskResult, or null if task not found
     */
    public TaskResult getTaskStatus(String taskId) {
        for (JobContext ctx : jobs.values()) {
            TaskResult result = ctx.taskStatus.get(taskId);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
    
    /**
     * Gets remaining task count for a job.
     * 
     * @param jobId job identifier
     * @return number of pending tasks, or -1 if job not found
     */
    public int getPendingTaskCount(String jobId) {
        JobContext ctx = jobs.get(jobId);
        return ctx != null ? ctx.pendingTasks.get() : -1;
    }
    
    /**
     * Gets all jobs currently tracked by the scheduler.
     * 
     * @return set of job IDs
     */
    public Set<String> getActiveJobs() {
        return new HashSet<>(jobs.keySet());
    }
}

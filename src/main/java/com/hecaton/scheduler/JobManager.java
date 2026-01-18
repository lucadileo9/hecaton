package com.hecaton.scheduler;

import com.hecaton.discovery.ClusterMembershipService;
import com.hecaton.node.NodeCapabilities;
import com.hecaton.rmi.NodeService;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import com.hecaton.task.assignment.AssignmentStrategy;
import com.hecaton.task.splitting.SplittingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for job submission and result aggregation.
 * Orchestrates the entire job execution lifecycle on the Leader node.
 * 
 * Lifecycle:
 *   1. User submits Job via submitJob()
 *   2. Job is split into Tasks (SplittingStrategy)
 *   3. Tasks are assigned to Workers (AssignmentStrategy)
 *   4. Tasks are scheduled for execution (TaskScheduler)
 *            The execution is totally managed by TaskScheduler, we will just receive the results
 *   5. Results are aggregated into JobResult
 *   6. JobResult returned to user
 * 
 * Thread-safe for concurrent job submissions.
 */
public class JobManager {
    private static final Logger log = LoggerFactory.getLogger(JobManager.class);
    
    private static final long DEFAULT_JOB_TIMEOUT_MS = 600_000;  // 10 minutes
    
    private final SplittingStrategy splittingStrategy;
    private final AssignmentStrategy assignmentStrategy;
    private final ClusterMembershipService membershipService;
    private final TaskScheduler taskScheduler;
    
    // Track pending jobs (jobId → completion latch)
    private final Map<String, CountDownLatch> pendingJobs = new ConcurrentHashMap<>();
    
    // Store results for completed jobs (jobId → results)
    private final Map<String, List<TaskResult>> jobResults = new ConcurrentHashMap<>();
    
    /**
     * Creates a new JobManager.
     * TaskScheduler is created internally as an implementation detail.
     * 
     * @param splittingStrategy strategy to divide jobs into tasks
     * @param assignmentStrategy strategy to assign tasks to workers
     * @param membershipService cluster membership service (to get worker list)
     */
    public JobManager(SplittingStrategy splittingStrategy,
                     AssignmentStrategy assignmentStrategy,
                     ClusterMembershipService membershipService) {
        this.splittingStrategy = splittingStrategy;
        this.assignmentStrategy = assignmentStrategy;
        this.membershipService = membershipService;
        
        // Create TaskScheduler internally (implementation detail)
        this.taskScheduler = new TaskScheduler(this);
        
        log.info("JobManager initialized with strategies: splitting={}, assignment={}",
                 splittingStrategy.getName(), assignmentStrategy.getName());
    }
    
    /**
     * Submits a job for distributed execution.
     * Blocks until the job completes or times out.
     * 
     * @param job the job to execute
     * @return JobResult when execution completes
     * @throws IllegalArgumentException if job is null
     * @throws IllegalStateException if no workers available or scheduling fails
     * @throws InterruptedException if waiting is interrupted
     */
    public JobResult submitJob(Job job) throws InterruptedException {
        return submitJob(job, DEFAULT_JOB_TIMEOUT_MS);
    }
    
    /**
     * Submits a job for distributed execution with custom timeout.
     * 
     * @param job the job to execute
     * @param timeoutMs maximum time to wait for job completion (milliseconds)
     * @return JobResult when execution completes
     * @throws IllegalArgumentException if job is null or timeout <= 0
     * @throws IllegalStateException if no workers available or scheduling fails
     * @throws InterruptedException if waiting is interrupted
     */
    public JobResult submitJob(Job job, long timeoutMs) throws InterruptedException {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        long startTime = System.currentTimeMillis();
        
        // 1. Generate unique job ID
        String jobId = "job-" + System.currentTimeMillis();
        job.setJobId(jobId);
        log.info("Submitting job {} (type: {})", jobId, job.getJobType());
        
        try {
            // 2. Get worker capabilities (null = use all active nodes)
            Map<String, NodeCapabilities> workerCapabilities = getWorkerCapabilities(null);
            
            if (workerCapabilities.isEmpty()) {
                throw new IllegalStateException("No workers available in the cluster");
            }
            
            log.info("Found {} workers for job {}", workerCapabilities.size(), jobId);
            
            // 3. Split job into tasks
            List<Task> tasks = splittingStrategy.split(job, workerCapabilities);
            log.info("Job {} split into {} tasks using {}", 
                     jobId, tasks.size(), splittingStrategy.getName());
            
            if (tasks.isEmpty()) {
                log.warn("Job {} produced zero tasks, returning empty result", jobId);
                return JobResult.failure(jobId, "No tasks to execute, because job split produced zero tasks", 0);
            }
            
            // 4. Assign tasks to workers
            Map<String, List<Task>> assignments = assignmentStrategy.assign(tasks, workerCapabilities);
            log.info("Tasks assigned to {} workers using {}", 
                     assignments.size(), assignmentStrategy.getName());
            
            // Log assignment distribution
            for (Map.Entry<String, List<Task>> entry : assignments.entrySet()) {
                log.debug("  Worker {}: {} tasks", entry.getKey(), entry.getValue().size());
            }
            
            // 5. Create completion latch (one count = job completion)
            CountDownLatch completionLatch = new CountDownLatch(1);
            pendingJobs.put(jobId, completionLatch);
            
            // 6. Schedule tasks for execution via TaskScheduler
            log.info("Scheduling {} tasks for job {} across {} workers via TaskScheduler", 
                     tasks.size(), jobId, assignments.size());
            taskScheduler.scheduleTasks(jobId, assignments);
            
            // 7. Wait for completion
            log.info("Waiting for job {} completion (timeout: {}ms)...", jobId, timeoutMs);
            boolean completed = completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            
            // Check for timeout
            if (!completed) {
                log.error("Job {} timed out after {}ms", jobId, timeoutMs);
                cleanup(jobId); // if timeout, clean up resources
                
                long executionTime = System.currentTimeMillis() - startTime;
                return JobResult.failure(
                    jobId,
                    "Job timed out after " + timeoutMs + "ms",
                    executionTime
                );
            }
            
            // 8. Retrieve results
            List<TaskResult> results = jobResults.remove(jobId);
            if (results == null) {
                log.error("Job {} completed but no results found", jobId);
                cleanup(jobId);
                return JobResult.failure(jobId, "No results available", 0);
            }
            
            // 9. Aggregate results
            log.info("Job {} completed with {} results, aggregating...", jobId, results.size());
            JobResult finalResult = job.aggregateResults(results);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Job {} finished in {}ms with status: {}", 
                     jobId, executionTime, finalResult.getStatus());
            
            cleanup(jobId);
            return finalResult;
            
        } catch (Exception e) {
            log.error("Job {} failed with error: {}", jobId, e.getMessage(), e);
            cleanup(jobId);
            
            long executionTime = System.currentTimeMillis() - startTime;
            return JobResult.failure(
                jobId,
                "Job execution failed: " + e.getMessage(),
                executionTime
            );
        }
    }
    
    /**
     * Called by TaskScheduler when all tasks for a job are completed.
     * This method releases the waiting submitJob() thread.
     * 
     * @param jobId ID of the completed job
     * @param results list of all task results
     */
    public void onJobFinished(String jobId, List<TaskResult> results) {
        log.info("Job {} finished callback received with {} results", jobId, results.size());
        
        // Store results
        jobResults.put(jobId, results);
        
        // Release waiting thread
        CountDownLatch latch = pendingJobs.get(jobId);
        if (latch != null) {
            latch.countDown();
            log.debug("Job {} latch released", jobId);
        } else {
            log.warn("Job {} latch not found (already cleaned up?)", jobId);
        }
    }
    
    /**
     * Retrieves worker capabilities from specified nodes or all active cluster nodes.
     * Called before job splitting, assignment, and task reassignment.
     * 
     * @param workers specific workers to query, or null to use all active cluster nodes
     * @return map of workerId → capabilities
     */
    private Map<String, NodeCapabilities> getWorkerCapabilities(List<NodeService> workers) {
        Map<String, NodeCapabilities> capabilities = new HashMap<>();
        
        // Use provided workers or fetch all from membershipService
        List<NodeService> targetWorkers = (workers != null) ? workers : membershipService.getActiveNodes();
        
        for (NodeService worker : targetWorkers) {
            try {
                String workerId = worker.getId();
                NodeCapabilities caps = worker.getCapabilities();
                capabilities.put(workerId, caps);
                
                log.debug("Worker {}: {}", workerId, caps);
                
            } catch (RemoteException e) {
                log.warn("Failed to get capabilities from worker: {}", e.getMessage());
                // Skip this worker - it might be dead
            }
        }
        
        return capabilities;
    }
    
    /**
     * Cleans up resources for a finished job.
     * 
     * @param jobId ID of the job to clean up
     */
    private void cleanup(String jobId) {
        pendingJobs.remove(jobId);
        jobResults.remove(jobId);
        log.debug("Job {} resources cleaned up", jobId);
    }
    
    /**
     * Returns the number of currently pending jobs.
     * 
     * @return count of jobs waiting for completion
     */
    public int getPendingJobCount() {
        return pendingJobs.size();
    }
    
    /**
     * Checks if a job is currently being processed.
     * 
     * @param jobId ID of the job to check
     * @return true if job is pending
     */
    public boolean isJobPending(String jobId) {
        return pendingJobs.containsKey(jobId);
    }
    
    /**
     * Reassigns orphaned tasks to healthy workers.
     * Called by TaskScheduler when a worker fails.
     * 
     * Uses the same AssignmentStrategy used for initial job distribution,
     * ensuring consistent task assignment logic throughout the job lifecycle.
     * 
     * @param orphanedTasks list of tasks that need reassignment
     * @return map of workerId → tasks to execute
     */
    public Map<String, List<Task>> reassignTasks(List<Task> orphanedTasks) {
        if (orphanedTasks == null || orphanedTasks.isEmpty()) {
            log.warn("No tasks to reassign");
            return new HashMap<>();
        }
        
        log.info("Reassigning {} orphaned tasks using {}", 
                 orphanedTasks.size(), assignmentStrategy.getClass().getSimpleName());
        
        // Get current healthy workers
        List<NodeService> healthyWorkers = membershipService.getActiveNodes();
        if (healthyWorkers.isEmpty()) {
            log.error("No healthy workers available for task reassignment!");
            return new HashMap<>();
        }
        
        // Convert workers to capabilities map (reuses existing logic)
        Map<String, NodeCapabilities> capabilities = getWorkerCapabilities(healthyWorkers);
        
        // Use the SAME strategy as initial assignment
        Map<String, List<Task>> newAssignments = assignmentStrategy.assign(orphanedTasks, capabilities);
        
        log.info("Reassigned {} tasks to {} workers", 
                 orphanedTasks.size(), newAssignments.size());
        
        return newAssignments;
    }
    
    // ==================== Facade Methods (delegated to TaskScheduler) ====================
    
    /**
     * Submits task results from workers.
     * Facade method that delegates to TaskScheduler.
     * 
     * This is the single point of entry for result submission,
     * allowing pre/post-processing, logging, and metrics collection.
     * 
     * @param results list of task results from worker execution
     */
    public void submitResults(List<TaskResult> results) {
        if (results == null || results.isEmpty()) {
            log.warn("Received empty results list");
            return;
        }
        
        log.debug("Facade: Delegating {} results to TaskScheduler", results.size());
        taskScheduler.submitResults(results);
    }
    
    /**
     * Handles worker failure by reassigning orphaned tasks.
     * Facade method that delegates to TaskScheduler.
     * 
     * This is the single point of entry for worker failure handling,
     * allowing centralized failure management and monitoring.
     * 
     * @param workerId ID of the failed worker
     */
    public void onWorkerFailed(String workerId) {
        log.warn("Facade: Worker {} failed, delegating to TaskScheduler", workerId);
        taskScheduler.onWorkerFailed(workerId);
    }

}

package com.hecaton.node;

import com.hecaton.rmi.LeaderService;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * TaskExecutor is responsible for executing tasks on a worker node using a thread pool.
 * 
 * It receives tasks from the Leader (via RMI call to executeTasks()), submits them
 * to a local thread pool for parallel execution, and sends results back to the Leader
 * as soon as each task completes.
 * 
 * Thread Pool Sizing:
 * - Size = CPU cores × CORE_MULTIPLIER
 * - Default CORE_MULTIPLIER = 1 (one thread per core)
 * - Avoids CPU over-subscription while maximizing parallelism
 * 
 * Error Handling:
 * - Task execution exceptions are caught and converted to FAILURE results
 * - If result submission to Leader fails (RMI error), it's logged but doesn't crash the executor
 * 
 * Lifecycle:
 * - Created once per worker node
 * - Started automatically when first task batch arrives
 * - Shutdown via shutdown() method when node stops
 */
public class TaskExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);
    
    /**
     * Thread pool sizing multiplier.
     * ThreadPool size = CPU cores × CORE_MULTIPLIER
     */
    private static final int CORE_MULTIPLIER = 1;
    
    /**
     * Maximum time to wait for thread pool shutdown (in seconds).
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    
    // ==================== Dependencies ====================
    
    private final ExecutorService threadPool;
    private final LeaderService leader;
    private final boolean isLeader;
    
    // ==================== Early Termination Support ====================
    
    /**
     * Tracks running tasks for cancellation support.
     * Maps Future → Task to enable job-level cancellation.
     * Concurrent for thread-safe access from multiple threads.
     */
    private final Map<Future<?>, Task> runningTasks = new ConcurrentHashMap<>();
    
    // ==================== Constructor ====================
    
    /**
     * Creates a new TaskExecutor with a thread pool sized to CPU cores.
     * 
     * @param capabilities the worker's hardware capabilities (for thread pool sizing)
     * @param leader the Leader service to submit results to (can be null if this node IS the leader)
     * @param isLeader true if this node is the leader (will use direct call instead of RMI)
     */
    public TaskExecutor(NodeCapabilities capabilities, LeaderService leader, boolean isLeader) {
        this.leader = leader;
        this.isLeader = isLeader;
        
        // Calculate thread pool size based on CPU cores
        int poolSize = capabilities.getCpuCores() * CORE_MULTIPLIER;
        // Thanks to this we are creating a FIXED thread pool with optimal size (calculated above)
        this.threadPool = Executors.newFixedThreadPool(poolSize);
        
        log.info("TaskExecutor initialized with thread pool size: {} (cores={}, multiplier={})",
                poolSize, capabilities.getCpuCores(), CORE_MULTIPLIER);
    }
    
    // ==================== Public API ====================
    
    /**
     * Receives a batch of tasks from the Leader and executes them.
     * 
     * Uses CompletionService to process results as they arrive, enabling early termination.
     * When a task finds a result (hasResult() = true) and supports early termination,
     * this method will:
     *   1. Cancel all remaining tasks for that job
     *   2. Send ONLY the successful result to Leader (not the full batch)
     * 
     * For normal execution (no early termination), sends all results in batch.
     * 
     * @param tasks list of tasks to execute
     */
    public void receiveTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            log.warn("Received empty task list, ignoring");
            return;
        }
        
        log.info("Received {} tasks from Leader - executing with thread pool...", tasks.size());
        
        // CompletionService for processing results as they complete
        CompletionService<TaskResult> completionService = new ExecutorCompletionService<>(threadPool);
        // Thanks to CompletionService we can easily retrieve the results as soon as they are ready
        // This will be explored further in the dedicated documentation.

        // Track submitted futures for cancellation
        List<Future<TaskResult>> submittedFutures = new ArrayList<>();
        // Here we are collecting all the results
        
        // Submit all tasks to thread pool
        for (Task task : tasks) {
            // We put in "future" the result of the submission to the completion service
            Future<TaskResult> future = completionService.submit(() -> { // N.B.: the submition is still parallel
                try {
                    log.debug("Executing task: {}", task.getTaskId());
                    
                    // Execute task
                    TaskResult result = task.execute();
                    
                    log.debug("Task {} completed with status {}", task.getTaskId(), result.getStatus());
                    return result;
                    
                } catch (Exception e) {
                    log.error("Task {} failed with exception", task.getTaskId(), e);
                    
                    // If execution fails, create a FAILURE result
                    return TaskResult.failure(
                        task.getJobId(),
                        task.getTaskId(),
                        "Execution failed: " + e.getMessage()
                    );
                }
            });
            
            // Than add the future to the result list
            submittedFutures.add(future);
            runningTasks.put(future, task); // and track list (for cancellation)
        }
        //N.B.: after this line the task is officially submitted for execution
        // so they are running in background, we do not have the result yet.

        // Process results AS THEY ARRIVE (streaming instead of batch)
        // so we don't have to wait for ALL tasks to complete before starting processing
        processTaskResults(tasks.size(), submittedFutures, completionService);
    }
    
    /**
     * Processes task results as they complete, with early termination support.
     * 
     * Monitors results from CompletionService. If a result with data is found (hasResult()),
     * triggers early termination: cancels remaining tasks and sends only that result.
     * 
     * @param totalTasks total number of tasks submitted
     * @param submittedFutures list of submitted futures for tracking
     * @param completionService completion service to poll results from
     */
    private void processTaskResults(int totalTasks, 
                                    List<Future<TaskResult>> submittedFutures,
                                    CompletionService<TaskResult> completionService) {
        
        // Execute asynchronously to avoid blocking RMI thread
        CompletableFuture.runAsync(() -> {
            // here we will collect the results that we will send to the leader
            List<TaskResult> results = new ArrayList<>();
            boolean earlyTerminationTriggered = false;
            String jobId = null;
            
            try {
                // Process results as they complete
                for (int i = 0; i < totalTasks; i++) { 
                    // blocks until a task completes, and than gives us the future
                    Future<TaskResult> completedFuture = completionService.take(); 
                    TaskResult result = completedFuture.get(); // get the ACTUAL result
                    
                    // Remove from tracking
                    runningTasks.remove(completedFuture);
                    
                    if (jobId == null) {
                        jobId = result.getJobId(); // capture jobId (it is contained in TaskResult)
                    }
                    
                    // Check for early termination
                    // Only SUCCESS status triggers early termination (terminal result found)
                    // PARTIAL results do NOT trigger early termination (they are contributions)
                    if (!earlyTerminationTriggered  // the early termination has not been already triggered
                        && result.getStatus() == TaskResult.Status.SUCCESS  // and this is a TERMINAL result, not PARTIAL
                    ) {
                        log.info("[EARLY TERMINATION] Task {} found terminal result, triggering local cancellation", 
                                result.getTaskId());
                        
                        // Cancel all remaining tasks for this job
                        cancelRemainingTasks(jobId, submittedFutures);
                        earlyTerminationTriggered = true;
                        
                        // Send ONLY this result immediately
                        results.add(result);
                        submitResultsToLeader(results);
                        
                        log.info("[EARLY TERMINATION] Sent result and cancelled remaining tasks for job={}", jobId);
                        return;  // Stop processing
                    }
                    
                    results.add(result);
                }
                
                // Normal completion - all tasks finished without early termination
                log.info("All {} tasks completed normally, submitting batch results", totalTasks);
                submitResultsToLeader(results); 
                
            } catch (InterruptedException e) {
                log.warn("Task processing interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error processing task results", e);
            }
        }, threadPool);
    }
    
    /**
     * Cancels all tasks for a specific job that are still running.
     * Called during early termination to stop unnecessary computation.
     * 
     * @param jobId job to cancel tasks for
     * @param futures list of all submitted futures
     */
    private void cancelRemainingTasks(String jobId, List<Future<TaskResult>> futures) {
        int cancelledCount = 0;
        
        for (Future<TaskResult> future : futures) { // for each submitted future (aka: task, that may be running)
            Task task = runningTasks.get(future);
            
            if (task != null  // make sure task is still running
                && task.getJobId().equals(jobId) // and matches the jobId we want to cancel
            ) {
                if (!future.isDone()) { // only if not already completed
                    boolean cancelled = future.cancel(true);  // Interrupt thread
                    if (cancelled) {
                        cancelledCount++;
                        runningTasks.remove(future);
                        log.debug("Cancelled task={}", task.getTaskId());
                    }
                }
            }
        }
        
        log.info("[EARLY TERMINATION] Cancelled {} remaining tasks for job={}", cancelledCount, jobId);
    }
    
    /**
     * Submits results to Leader via RMI.
     * Handles both Leader (direct call) and Worker (RMI call) cases.
     * 
     * @param results list of task results to submit
     */
    private void submitResultsToLeader(List<TaskResult> results) {
        try {
            if (isLeader) {
                // Leader submits to itself directly (no RMI)
                leader.submitResults(results);
            } else {
                // Worker submits via RMI to Leader
                leader.submitResults(results);
            }
            
            log.info("Successfully submitted {} results to leader", results.size());
            
        } catch (RemoteException e) {
            log.error("Failed to submit results to leader", e);
        }
    }
    
    /**
     * Cancels all running tasks for a specific job (called via RMI from Leader).
     * 
     * This is invoked when the Leader detects early termination and broadcasts
     * cancelJob() to all workers. Workers interrupt threads executing tasks for this job.
     * 
     * N.B.: this will be called from the leader when another worker found a result and
     * wants to stop all other workers from continuing useless computation.
     * @param jobId job identifier to cancel
     * @return number of tasks cancelled
     */
    public int cancelJob(String jobId) {
        log.info("[CANCELLATION] Cancelling all tasks for job={}", jobId);
        
        int cancelledCount = 0;
        
        // Iterate over running tasks and cancel those matching jobId
        // It is very similar to the early termination cancellation logic, so I will not repeat comments
        for (Map.Entry<Future<?>, Task> entry : runningTasks.entrySet()) {
            Future<?> future = entry.getKey();
            Task task = entry.getValue();
            
            if (task.getJobId().equals(jobId)) {
                if (!future.isDone()) {
                    boolean cancelled = future.cancel(true);  // Interrupt the thread
                    if (cancelled) {
                        cancelledCount++;
                        log.debug("[CANCELLATION] Cancelled task={}", task.getTaskId());
                    }
                }
                // Remove from tracking regardless of cancellation success
                runningTasks.remove(future);
            }
        }
        
        log.info("[CANCELLATION] Cancelled {} tasks for job={}", cancelledCount, jobId);
        return cancelledCount;
    }
    
    /**
     * Shuts down the task executor and waits for running tasks to complete.
     * 
     * This is a graceful shutdown: it waits up to SHUTDOWN_TIMEOUT_SECONDS
     * for running tasks to finish before forcibly terminating them.
     */
    public void shutdown() {
        log.info("Shutting down TaskExecutor...");
        
        // Clear running tasks tracking
        runningTasks.clear();
        
        threadPool.shutdown();
        
        try {
            if (!threadPool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Thread pool did not terminate in {} seconds, forcing shutdown", 
                        SHUTDOWN_TIMEOUT_SECONDS);
                threadPool.shutdownNow();
            }
            
            log.info("TaskExecutor shutdown complete");
            
        } catch (InterruptedException e) {
            log.error("TaskExecutor shutdown interrupted", e);
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

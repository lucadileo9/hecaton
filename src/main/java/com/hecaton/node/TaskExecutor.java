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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
     * Accumulates ALL results and sends them in a SINGLE RMI call at the end.
     * This minimizes network overhead compared to sending one result per task.
     * 
     * @param tasks list of tasks to execute
     */
    public void receiveTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            log.warn("Received empty task list, ignoring");
            return;
        }
        
        log.info("Received {} tasks from Leader - executing with thread pool...", tasks.size());
        
        // Thread-safe list to accumulate results, because multiple threads could add results concurrently
        List<TaskResult> allResults = Collections.synchronizedList(new ArrayList<>());
        
        // Create CompletableFutures for all tasks
        List<CompletableFuture<Void>> futures = tasks.stream() // trasform the list of tasks into a stream
            .map(task -> CompletableFuture.runAsync(() -> { // map each task to a CompletableFuture that runs asynchronously
        // q: what is a CompletableFuture?
        // a: A CompletableFuture is a class in Java that represents a future result of an asynchronous computation.
        // It allows you to write non-blocking code by providing methods to handle the result once it's available.
                try {
                    log.debug("Executing task: {}", task.getTaskId());
                    
                    // Execute task
                    TaskResult result = task.execute();
                    
                    log.debug("Task {} completed with status {}", task.getTaskId(), result.getStatus());
                    // Add result to the shared list
                    allResults.add(result);
                    
                } catch (Exception e) {
                    log.error("Task {} failed with exception", task.getTaskId(), e);
                    
                    // If execution fails, create a FAILURE result, but continue processing other tasks
                    TaskResult errorResult = TaskResult.failure(
                        task.getJobId(),
                        task.getTaskId(),
                        "Execution failed: " + e.getMessage()
                    );
                    allResults.add(errorResult);
                }
            }, threadPool))
            .collect(Collectors.toList());
        
        // Wait for all tasks to complete, then send ALL results in ONE batch
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                try {
                    log.info("All {} tasks completed, submitting results to leader in single batch", allResults.size());
                    
                    if (isLeader) {
                        // Leader submits to itself directly (no RMI)
                        leader.submitResults(allResults);
                    } else {
                        // Worker submits via RMI to Leader (ONE call for all results)
                        leader.submitResults(allResults);
                    }
                    
                    log.info("Successfully submitted {} results to leader", allResults.size());
                    
                } catch (RemoteException e) {
                    log.error("Failed to submit results to leader", e);
                }
            })
            .exceptionally(ex -> {
                log.error("Unexpected error during task execution", ex);
                return null;
            });
    }
    
    /**
     * Shuts down the task executor and waits for running tasks to complete.
     * 
     * This is a graceful shutdown: it waits up to SHUTDOWN_TIMEOUT_SECONDS
     * for running tasks to finish before forcibly terminating them.
     */
    public void shutdown() {
        log.info("Shutting down TaskExecutor...");
        
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

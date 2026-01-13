package com.hecaton.task.splitting;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Job;
import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Strategy that creates a number of small tasks based on the number of available workers.
 * 
 * <p>Formula: {@code totalTasks = workerCount × tasksPerWorker}</p>
 * 
 * Advantages:
 *   - Natural auto-balancing: faster workers take more tasks
 *   - Fine granularity for progress tracking
 *   - Resilience: small tasks = less work lost on failure
 * 
 * Recommended use:
 *   - Heterogeneous clusters (different hardware)
 *   - Long-running jobs where fine-grained progress is needed
 *   - When simplicity is desired (no weighted assignment)
 * 
 */
public class DynamicSplitting implements SplittingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicSplitting.class);
    
    /** Default value for tasksPerWorker */
    public static final int DEFAULT_TASKS_PER_WORKER = 50;
    
    /** Minimum total number of tasks */
    public static final int MIN_TOTAL_TASKS = 10;
    
    /** Maximum total number of tasks */
    public static final int MAX_TOTAL_TASKS = 10000;
    
    private final int tasksPerWorker; // Number of tasks to create per worker
    
    /**
     * Creates DynamicSplitting with default value (50 tasks per worker).
     */
    public DynamicSplitting() {
        this(DEFAULT_TASKS_PER_WORKER);
    }
    
    /**
     * Creates DynamicSplitting with a specific number of tasks per worker.
     * 
     * @param tasksPerWorker number of tasks to create per worker
     * @throws IllegalArgumentException if tasksPerWorker <= 0
     */
    public DynamicSplitting(int tasksPerWorker) {
        if (tasksPerWorker <= 0) {
            throw new IllegalArgumentException("tasksPerWorker must be positive: " + tasksPerWorker);
        }
        this.tasksPerWorker = tasksPerWorker;
    }
    
    @Override
    public List<Task> split(Job job, Map<String, NodeCapabilities> workers) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("Workers map cannot be null or empty");
        }
        
        int workerCount = workers.size();
        int totalTasks = workerCount * tasksPerWorker;
        
        // Applica limiti
        totalTasks = Math.max(MIN_TOTAL_TASKS, totalTasks);
        totalTasks = Math.min(MAX_TOTAL_TASKS, totalTasks);
        
        log.info("DynamicSplitting: {} workers × {} = {} tasks for job {}",
            workerCount, tasksPerWorker, totalTasks, job.getJobId());
        
        List<Task> tasks = job.split(totalTasks);
        
        log.debug("Created {} tasks (requested {})", tasks.size(), totalTasks);
        
        return tasks;
    }
    
    @Override
    public String getName() {
        return String.format("DynamicSplitting(%d)", tasksPerWorker);
    }
    
    @Override
    public boolean isCompatibleWith(String assignmentStrategy) {
        // Dynamic works better with RoundRobin or LoadAware
        // With Targeted it doesn't make much sense (tasks have no target)
        if ("TargetedAssignment".equals(assignmentStrategy)) {
            log.warn("DynamicSplitting with TargetedAssignment: tasks have no targetWorkerId");
            return true;  // Works but not optimal
        }
        return true;
    }
    
    /**
     * @return number of tasks per worker configured
     */
    public int getTasksPerWorker() {
        return tasksPerWorker;
    }
}

package com.hecaton.task.splitting;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Job;
import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Uniform splitting strategy: 1 task per worker.
     * 
 * <p>Formula: {@code totalTasks = workerCount}</p>
 * 
 * Advantages:
 *   - Minimal overhead (few large tasks)
 *   - Simple to understand and debug
 * 
 * Disadvantages:
 *   - No balancing for heterogeneous clusters
 *   - Coarse progress tracking
 *   - Task failure = much work lost
 * 
 * Recommended use:
 *   - Homogeneous cluster (same hardware)
 *   - Short jobs
 *   - Testing and debugging
 * 
 * Recommended use:
 *   - Homogeneous cluster (same hardware)
 *   - Short jobs
 *   - Testing and debugging
 */
public class UniformSplitting implements SplittingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(UniformSplitting.class);
    
    /** Minimum number of tasks (even with 1 worker) */
    public static final int MIN_TASKS = 1;
    
    /**
     * Creates UniformSplitting.
     */
    public UniformSplitting() {
        // No configuration needed
    }
    
    @Override
    public List<Task> split(Job job, Map<String, NodeCapabilities> workers) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("Workers map cannot be null or empty");
        }
        
        int totalTasks = Math.max(MIN_TASKS, workers.size());
        
        log.info("UniformSplitting: {} workers = {} tasks for job {}",
            workers.size(), totalTasks, job.getJobId());
        
        List<Task> tasks = job.split(totalTasks);
        
        if (tasks.size() != totalTasks) {
            log.warn("Job returned {} tasks instead of requested {}", tasks.size(), totalTasks);
        }
        
        return tasks;
    }
    
    @Override
    public String getName() {
        return "UniformSplitting";
    }
}
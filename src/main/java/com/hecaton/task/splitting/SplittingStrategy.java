package com.hecaton.task.splitting;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Job;
import com.hecaton.task.Task;

import java.util.List;
import java.util.Map;

/**
 * Strategy to split a Job into multiple Tasks.
 * 
 * Responsibilities:
 *   - Decide the number of tasks to create
 *   - Call {@code job.split(numTasks)} to create the tasks
 *   - Optionally annotate tasks with targetWorkerId
 * 
 */
@FunctionalInterface
public interface SplittingStrategy {
    
    /**
     * Divide the job into tasks.
     * 
     * @param job the job to divide
     * @param workers map of workerId → capabilities (for hardware-based decisions)
     * @return list of created tasks
     * @throws IllegalArgumentException if job is null or workers is empty
     */
    List<Task> split(Job job, Map<String, NodeCapabilities> workers);
    
    /**
     * @return descriptive name of the strategy (for logging)
     */
    default String getName() {
        return getClass().getSimpleName();
    }
    
    /**
     * Checks if this strategy is compatible with the given AssignmentStrategy.
     * Override to implement specific validations.
     * 
     * I think I'll not use it, because for the initial project I'll create only two 
     * strategies that are compatible with all assignment strategies.
     * But this method can be useful in future expansions.
     * 
     * @param assignmentStrategy name of the assignment strategy
     * @return true if compatible
     */
    default boolean isCompatibleWith(String assignmentStrategy) {
        return true;  // Default: compatibile con tutto
    }
}

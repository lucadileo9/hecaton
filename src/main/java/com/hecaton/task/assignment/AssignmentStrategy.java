package com.hecaton.task.assignment;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Task;

import java.util.List;
import java.util.Map;

/**
 * Strategy to assign tasks to workers.
 * 
 * Responsibilities:
 *   - Receive list of tasks and worker capabilities
 *   - Decide which worker executes which task
 *   - Return mapping workerId → list of tasks
 * 
 * The strategy only DECIDES the assignment based on capabilities,
 * it does NOT execute RMI calls or manage worker state.
 */
@FunctionalInterface
public interface AssignmentStrategy {
    
    /**
     * Assigns tasks to workers based on their capabilities.
     * 
     * @param tasks list of tasks to assign
     * @param workerCapabilities map of workerId → node capabilities (CPU, RAM, OS)
     * @return map workerId → list of assigned tasks
     * @throws IllegalArgumentException if tasks or workerCapabilities are null/empty
     */
    Map<String, List<Task>> assign(
        List<Task> tasks, 
        Map<String, NodeCapabilities> workerCapabilities
    );
    
    /**
     * @return descriptive name of the strategy (for logging)
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}

package com.hecaton.task.assignment;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Classic round-robin assignment strategy.
 * 
 * <p>Distributes tasks cyclically among workers:
 * Task 0 → Worker 0, Task 1 → Worker 1, ..., Task N → Worker 0, ...</p>
 * 
 * <p>This strategy ignores worker capabilities and simply distributes
 * tasks evenly in a circular fashion.</p>
 * 
 * Advantages:
 * - Simple and predictable
 * - Guaranteed uniform distribution
 * - No computational overhead
 * 
 */
public class RoundRobinAssignment implements AssignmentStrategy, Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final Logger log = LoggerFactory.getLogger(RoundRobinAssignment.class);
    
    /**
     * Creates a RoundRobinAssignment strategy.
     */
    public RoundRobinAssignment() {
        // No configuration needed
    }
    
    @Override
    public Map<String, List<Task>> assign(
            List<Task> tasks, 
            Map<String, NodeCapabilities> workerCapabilities) {
        
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks list cannot be null or empty");
        }
        if (workerCapabilities == null || workerCapabilities.isEmpty()) {
            throw new IllegalArgumentException("Worker capabilities cannot be null or empty");
        }
        
        // Get worker IDs in consistent order
        List<String> workerIds = new ArrayList<>(workerCapabilities.keySet());
        Map<String, List<Task>> assignments = new LinkedHashMap<>();
        
        // Initialize empty lists for each worker
        for (String workerId : workerIds) {
            assignments.put(workerId, new ArrayList<>());
        }
        
        // Distribute round-robin
        int workerCount = workerIds.size();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String workerId = workerIds.get(i % workerCount);
            assignments.get(workerId).add(task);
        }
        
        // Log distribution
        log.info("RoundRobinAssignment: {} tasks → {} workers", tasks.size(), workerIds.size());
        for (Map.Entry<String, List<Task>> entry : assignments.entrySet()) {
            log.debug("  {} → {} tasks", entry.getKey(), entry.getValue().size());
        }
        
        return assignments;
    }
    
    @Override
    public String getName() {
        return "RoundRobinAssignment";
    }
}

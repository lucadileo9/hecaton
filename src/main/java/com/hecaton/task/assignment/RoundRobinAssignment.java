package com.hecaton.task.assignment;

import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Classic round-robin assignment strategy.
 * 
 * <p>Distributes tasks cyclically among workers:
 * Task 0 → Worker 0, Task 1 → Worker 1, ..., Task N → Worker 0, ...</p>
 * 
 * Advantages:
 * - Simple and predictable
 * - Guaranteed uniform distribution
 * - No computational overhead
 * 
 */
public class RoundRobinAssignment implements AssignmentStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(RoundRobinAssignment.class);
    
    /**
     *   RoundRobinAssignment.
     */
    public RoundRobinAssignment() {
        // No configuration needed
    }
    
    @Override
    public Map<String, List<Task>> assign(List<Task> tasks, List<WorkerInfo> workers) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks list cannot be null or empty");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("Workers list cannot be null or empty");
        }
        
        Map<String, List<Task>> assignments = new LinkedHashMap<>();
        
        // Initialize empty lists for each worker
        for (WorkerInfo worker : workers) {
            assignments.put(worker.getWorkerId(), new ArrayList<>());
        }
        
        // Distribute round-robin
        int workerCount = workers.size();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String workerId = workers.get(i % workerCount).getWorkerId();
            assignments.get(workerId).add(task);
        }
        
        // Log distribuzione
        log.info("RoundRobinAssignment: {} tasks → {} workers", tasks.size(), workers.size());
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

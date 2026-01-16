package com.hecaton.task.assignment;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This strategy assigns tasks to specific target workers when specified. 
 * Works in conjunction with WeightedSplitting strategy.
 * 
 * Functioning:
 *   - If task has targetWorkerId → assign to that worker
 *   - If targetWorkerId does not exist → fallback to round-robin
 *   - If task has no target → fallback to round-robin
 */
public class TargetedAssignment implements AssignmentStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(TargetedAssignment.class);
    
    /** Strategy fallback for tasks without target */
    private final AssignmentStrategy fallback;
    
    /**
     * Creates TargetedAssignment with round-robin fallback.
     */
    public TargetedAssignment() {
        this(new RoundRobinAssignment());
    }
    
    /**
     * Creates TargetedAssignment with custom fallback strategy.
     * 
     * @param fallback strategy for tasks without targetWorkerId
     */
    public TargetedAssignment(AssignmentStrategy fallback) {
        this.fallback = fallback;
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
        
        // Get available worker IDs
        Set<String> availableWorkers = workerCapabilities.keySet();
        
        Map<String, List<Task>> assignments = new LinkedHashMap<>();
        List<Task> unassigned = new ArrayList<>();
        
        // Initialize empty lists for each worker
        for (String workerId : availableWorkers) {
            assignments.put(workerId, new ArrayList<>());
        }
        
        // First pass: assign tasks with valid targets
        int targeted = 0;
        int fallbackCount = 0;
        
        for (Task task : tasks) {
            String target = task.getTargetWorkerId(); // null by default,
            // set by WeightedSplitting strategy
            
            if (target != null && availableWorkers.contains(target)) {
                // Assign to target
                assignments.get(target).add(task);
                targeted++;
            } else if (target != null) {
                // Target specified but worker does not exist
                log.warn("Task {} has unknown target {}, will use fallback",
                    task.getTaskId(), target);
                unassigned.add(task);
                fallbackCount++;
            } else {
                // No target
                unassigned.add(task);
                fallbackCount++;
            }
        }
        
        // Distribute unassigned tasks with fallback strategy
        if (!unassigned.isEmpty()) {
            log.debug("Using fallback for {} tasks without valid target", unassigned.size());
            Map<String, List<Task>> fallbackAssignments = 
                fallback.assign(unassigned, workerCapabilities);
            
            // Merge fallback assignments
            for (Map.Entry<String, List<Task>> entry : fallbackAssignments.entrySet()) {
                assignments.get(entry.getKey()).addAll(entry.getValue());
            }
        }
        
        log.info("TargetedAssignment: {} targeted + {} fallback = {} total tasks",
            targeted, fallbackCount, tasks.size());
        
        for (Map.Entry<String, List<Task>> entry : assignments.entrySet()) {
            log.debug("  {} → {} tasks", entry.getKey(), entry.getValue().size());
        }
        
        return assignments;
    }
    
    
    @Override
    public String getName() {
        return "TargetedAssignment";
    }
}
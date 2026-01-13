package com.hecaton.task.assignment;

import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This strategy assigns tasks to specific target workers when specified. 
 * So it is strictly tied to WeightedSplitting strategy.
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
    public Map<String, List<Task>> assign(List<Task> tasks, List<WorkerInfo> workers) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks list cannot be null or empty");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("Workers list cannot be null or empty");
        }
        
        // Mappa workerId → workerInfo per lookup veloce
        Map<String, WorkerInfo> workerMap = new HashMap<>();
        for (WorkerInfo worker : workers) {
            workerMap.put(worker.getWorkerId(), worker);
        }
        
        Map<String, List<Task>> assignments = new LinkedHashMap<>();
        List<Task> unassigned = new ArrayList<>();
        
        // Inizializza liste
        for (WorkerInfo worker : workers) {
            assignments.put(worker.getWorkerId(), new ArrayList<>());
        }
        
        // Prima passata: assegna task con target valido
        int targeted = 0;
        int fallbackCount = 0;
        
        for (Task task : tasks) {
            String target = task.getTargetWorkerId(); // this is a method of Task, 
            // default null, but for WeightedSplitting it is set
            
            if (target != null && // I have a target
                 workerMap.containsKey(target) // and it exists in my workers
                 ) {
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
        
        // Now we have unassigned tasks to distribute with fallback strategy
        if (!unassigned.isEmpty()) {
            log.debug("Using fallback for {} tasks without valid target", unassigned.size());
            Map<String, List<Task>> fallbackAssignments = fallback.assign(unassigned, workers);
            
            // Merge
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
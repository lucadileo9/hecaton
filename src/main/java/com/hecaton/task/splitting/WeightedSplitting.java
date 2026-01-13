package com.hecaton.task.splitting;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Job;
import com.hecaton.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Splitting strategy that creates tasks proportional to worker "weight",
 * 
 * Create tasks with sizes proportional to each worker's "weight",
 * calculated using {@link NodeCapabilities#calculateWeight()}.</p>
 * 
 * Operation:
 *   - Calculate total cluster weight
 *   - For each worker, calculate percentage of total weight
 *   - Create N tasks, each annotated with targetWorkerId
 *   - Tasks per worker = percentage × totalTasks
 * 
 * Recommended use:
 *   - Highly heterogeneous clusters (PC + Raspberry Pi)
 *   - CPU-intensive jobs where balancing is critical
 * 
 */
public class WeightedSplitting implements SplittingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(WeightedSplitting.class);
    
    /** Total number of tasks to create */
    private final int totalTasks;
    
    /** Minimum number of tasks per worker (avoids workers without work) */
    public static final int MIN_TASKS_PER_WORKER = 1;
    
    /**
     * Creates WeightedSplitting with specified total number of tasks.
     * 
     * @param totalTasks total number of tasks to create
     * @throws IllegalArgumentException if totalTasks <= 0
     */
    public WeightedSplitting(int totalTasks) {
        if (totalTasks <= 0) {
            throw new IllegalArgumentException("totalTasks must be positive: " + totalTasks);
        }
        this.totalTasks = totalTasks;
    }
    
    @Override
    public List<Task> split(Job job, Map<String, NodeCapabilities> workers) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("Workers map cannot be null or empty");
        }
        
        // Calculate weights
        Map<String, Double> weights = new HashMap<>();
        double totalWeight = 0;
        
        // for each worker
        for (Map.Entry<String, NodeCapabilities> entry : workers.entrySet()) {
            double weight = entry.getValue().calculateWeight(); // Take the weight
            weights.put(entry.getKey(), weight); // Store it
            totalWeight += weight; // Sum to total
        }
        // Now we have totalWeight and weights per worker
        
        // Since we have totalTasks to distribute and we know weights of each worker,
        // we can calculate how many tasks to assign to each worker.
        Map<String, Integer> tasksPerWorker = new LinkedHashMap<>();
        int assignedTasks = 0;
        
        List<String> workerIds = new ArrayList<>(workers.keySet());
        for (int i = 0; i < workerIds.size(); i++) { // for each worker
            String workerId = workerIds.get(i); // Get worker ID
            double weight = weights.get(workerId); // Get its weight
            double ratio = weight / totalWeight; // Calculate ratio
            
            int workerTasks;
            if (i == workerIds.size() - 1) {
                // Last worker takes the remainder (avoids rounding errors)
                workerTasks = totalTasks - assignedTasks;
            } else {
                workerTasks = (int) Math.round(ratio * totalTasks); // Calculate tasks
                workerTasks = Math.max(MIN_TASKS_PER_WORKER, workerTasks); // Apply minimum
            }
            
            tasksPerWorker.put(workerId, workerTasks); // Here we store how many tasks for this worker
            assignedTasks += workerTasks; // Update assigned count
            
            log.debug("Worker {} weight={:.2f} ratio={:.2%} → {} tasks",
                workerId, weight, ratio, workerTasks);
        }
        // After all this we have how many tasks to create per worker based on weight

        log.info("WeightedSplitting: {} total tasks distributed by weight for job {}",
            totalTasks, job.getJobId());
        
        // Create tasks with target
        List<Task> allTasks = job.split(totalTasks);
        // Now we have all the tasks, but we need to tie them to workers
        
        // Annotate tasks with targetWorkerId
        int taskIndex = 0;
        List<Task> annotatedTasks = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : tasksPerWorker.entrySet()) { // for each worker
            String workerId = entry.getKey(); // Get worker ID
            int count = entry.getValue(); // and how many tasks for it
            
            for (int i = 0; i < count && taskIndex < allTasks.size(); i++) {
                Task task = allTasks.get(taskIndex++); // Get next task from all tasks given by job.split()
                
                // Wrap task with target annotation, so that it will be sent to the correct worker
                annotatedTasks.add(new TargetedTaskWrapper(task, workerId));
            }
        }
        
        return annotatedTasks;
    }
    
    @Override
    public String getName() {
        return String.format("WeightedSplitting(%d)", totalTasks);
    }
    
    @Override
    public boolean isCompatibleWith(String assignmentStrategy) {
        // Weighted funziona meglio con Targeted
        if ("RoundRobinAssignment".equals(assignmentStrategy)) {
            log.warn("WeightedSplitting with RoundRobin: targetWorkerId will be ignored");
            return true;
        }
        return true;
    }
    
    /**
     * This is a wrapper of Task that adds a targetWorkerId.
     * So this will be used when I want to assign a task to a specific worker.
     * It is a way to "annotate" tasks without modifying the original Task implementation.
     */
    private static class TargetedTaskWrapper implements Task {
        
        private static final long serialVersionUID = 1L;
        
        private final Task delegate;
        private final String targetWorkerId;
        
        TargetedTaskWrapper(Task delegate, String targetWorkerId) {
            this.delegate = delegate;
            this.targetWorkerId = targetWorkerId;
        }
        
        @Override
        public String getTaskId() {
            return delegate.getTaskId();
        }
        
        @Override
        public String getJobId() {
            return delegate.getJobId();
        }
        
        @Override
        public com.hecaton.task.TaskResult execute() {
            return delegate.execute();
        }
        
        @Override
        public com.hecaton.task.TaskResult execute(com.hecaton.node.ExecutionContext context) {
            return delegate.execute(context);
        }
        
        @Override
        public String getTargetWorkerId() {
            return targetWorkerId;
        }
        
        @Override
        public void onCancel() {
            delegate.onCancel();
        }
        
        @Override
        public int getEstimatedComplexity() {
            return delegate.getEstimatedComplexity();
        }
    }
}

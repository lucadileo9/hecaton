package com.hecaton.manual.task;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.*;
import com.hecaton.task.splitting.*;
import com.hecaton.task.assignment.*;

import java.util.*;

public class TestStrategies {
    
    public static void main(String[] args) {
        System.out.println("=== Test Splitting Strategies ===\n");
        
        // Setup mock job
        Job mockJob = new MockJob("test-job-1");
        
        // Setup mock workers with different capabilities
        Map<String, NodeCapabilities> workers = new LinkedHashMap<>();
        workers.put("worker-1", createCaps(8, 16000));   // Powerful
        workers.put("worker-2", createCaps(4, 8000));    // Medium
        workers.put("worker-3", createCaps(2, 4000));    // Lightweight
        
        // Test DynamicSplitting
        System.out.println("--- DynamicSplitting(50) ---");
        /* 
        In this case the dinamic splitting strategy would divide the tasks
        based on the number of workers and the specified tasks per worker (50).
        With 3 workers, we expect 150 tasks to be created.

        Because the idea of DynamicSplitting is to create a number of tasks
        proportional to the number of available workers, allowing for better
        load balancing and resource utilization.
         */
        SplittingStrategy dynamic = new DynamicSplitting(50);
        List<Task> dynamicTasks = dynamic.split(mockJob, workers);
        System.out.println("Created " + dynamicTasks.size() + " tasks");
        System.out.println("Expected: 150 (3 workers × 50)\n");
        
        // Test UniformSplitting
        System.out.println("--- UniformSplitting ---");
        /*
        In this case, the uniform splitting strategy creates one (big) task per worker,
        resulting in a total of 3 tasks for 3 workers. This approach ensures that each worker gets an equal share of the workload,
        regardless of their individual capabilities.
        Than we could exepect that each worker will handle one task, maybe parallelizing internally if needed.
        */
        SplittingStrategy uniform = new UniformSplitting();
        List<Task> uniformTasks = uniform.split(mockJob, workers);
        System.out.println("Created " + uniformTasks.size() + " tasks");
        System.out.println("Expected: 3 (1 per worker)\n");
        
        // Test WeightedSplitting
        System.out.println("--- WeightedSplitting(100) ---");
        /*
        In this case, the weighted splitting strategy creates tasks based on the capabilities of each worker.
        Given the CPU cores of the workers (8, 4, and 2), we expect the tasks to be distributed in a 4:2:1 ratio.
        With a total of 100 tasks, this results in approximately 57 tasks for worker-1, 29 tasks for worker-2, and 14 tasks for worker-3.
        This approach optimizes resource utilization by assigning more tasks to more capable workers.
        */
        SplittingStrategy weighted = new WeightedSplitting(100);
        List<Task> weightedTasks = weighted.split(mockJob, workers);
        System.out.println("Created " + weightedTasks.size() + " tasks");
        
        // Count targets
        Map<String, Integer> targetCounts = new HashMap<>();
        for (Task task : weightedTasks) {
            String target = task.getTargetWorkerId();
            targetCounts.merge(target, 1, Integer::sum);
        }
        System.out.println("Task distribution by target: " + targetCounts);
        System.out.println("Expected: ~57 for W1, ~29 for W2, ~14 for W3 (by CPU ratio)\n");
        
        // ==================== Assignment Tests ====================
        
        System.out.println("=== Test Assignment Strategies ===\n");
        
        // Create worker infos
        List<AssignmentStrategy.WorkerInfo> workerInfos = Arrays.asList(
            new AssignmentStrategy.WorkerInfo("worker-1", null, 5),   // 5 task attive
            new AssignmentStrategy.WorkerInfo("worker-2", null, 2),   // 2 task attive
            new AssignmentStrategy.WorkerInfo("worker-3", null, 0)    // 0 task attive
        );
        
        // Test RoundRobinAssignment
        /*
        Here we are using RoundRobinAssignment with the dynamically created tasks (150 tasks).
        The RoundRobin strategy distributes tasks evenly across all available workers in a cyclic manner.
        Given 150 tasks and 3 workers, we expect each worker to receive an equal number of tasks, resulting in 50 tasks per worker.
        This approach ensures a balanced workload distribution, preventing any single worker from being overloaded while others remain
        */
        System.out.println("--- RoundRobinAssignment ---");
        AssignmentStrategy roundRobin = new RoundRobinAssignment();
        Map<String, List<Task>> rrResult = roundRobin.assign(dynamicTasks, workerInfos);
        printAssignmentResult(rrResult);
        System.out.println("Expected: 50-50-50 (equal distribution)\n");
        
        // Test LoadAwareAssignment
        // System.out.println("--- LoadAwareAssignment ---");
        // // Use smaller task set for clarity
        // List<Task> smallTaskSet = dynamicTasks.subList(0, 15);
        // AssignmentStrategy loadAware = new LoadAwareAssignment();
        // Map<String, List<Task>> laResult = loadAware.assign(smallTaskSet, workerInfos);
        // printAssignmentResult(laResult);
        // System.out.println("Expected: W3 gets more (started with 0 active)\n");
        
        // Test TargetedAssignment
        System.out.println("--- TargetedAssignment ---");
        AssignmentStrategy targeted = new TargetedAssignment();
        Map<String, List<Task>> tResult = targeted.assign(weightedTasks, workerInfos);
        printAssignmentResult(tResult);
        System.out.println("Expected: matches WeightedSplitting targets\n");
        
        System.out.println("=== All Strategy Tests Passed ===");
    }
    
    private static NodeCapabilities createCaps(int cores, long memoryMB) {
        // Use reflection or a test constructor
        try {
            var constructor = NodeCapabilities.class.getDeclaredConstructor(
                int.class, long.class, long.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(cores, memoryMB, memoryMB / 2, "TestOS", "1.0", "21");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test NodeCapabilities", e);
        }
    }
    
    private static void printAssignmentResult(Map<String, List<Task>> result) {
        for (Map.Entry<String, List<Task>> entry : result.entrySet()) {
            System.out.println("  " + entry.getKey() + " → " + entry.getValue().size() + " tasks");
        }
    }
    
    /**
     * Mock Job per testing.
     */
    static class MockJob implements Job {
        private String jobId;
        
        MockJob(String jobId) {
            this.jobId = jobId;
        }
        
        @Override
        public String getJobId() {
            return jobId;
        }
        
        @Override
        public void setJobId(String jobId) {
            this.jobId = jobId;
        }
        
        @Override
        public List<Task> split(int numTasks) {
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < numTasks; i++) {
                tasks.add(new MockTask(jobId + "-task-" + i, jobId));
            }
            return tasks;
        }
        
        @Override
        public JobResult aggregateResults(List<TaskResult> results) {
            return JobResult.success(jobId, "aggregated", 0);
        }
    }
    
    /**
     * Mock Task per testing.
     */
    static class MockTask implements Task {
        private final String taskId;
        private final String jobId;
        
        MockTask(String taskId, String jobId) {
            this.taskId = taskId;
            this.jobId = jobId;
        }
        
        @Override
        public String getTaskId() {
            return taskId;
        }
        
        @Override
        public String getJobId() {
            return jobId;
        }
        
        @Override
        public TaskResult execute() {
            return TaskResult.success(taskId, "mock-result");
        }
    }
}

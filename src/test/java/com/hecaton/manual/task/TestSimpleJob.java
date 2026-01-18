package com.hecaton.manual.task;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.scheduler.JobManager;
import com.hecaton.scheduler.TaskScheduler;
import com.hecaton.task.*;
import com.hecaton.task.assignment.AssignmentStrategy;
import com.hecaton.task.splitting.SplittingStrategy;

import java.util.*;

/**
 * Simple Job Execution
 * 
 * Tests a basic job with real SimpleAdditionTask tasks.
 * Manually simulates worker execution and result submission.
 * No RMI, no JobManager callbacks - just TaskScheduler core logic.
 * 
 * How to run:
 *   mvn clean compile exec:java '-Dexec.mainClass=com.hecaton.manual.task.TestSimpleJob'
 */
public class TestSimpleJob {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Test 2: Simple Job Execution ===\n");
        
        // Create TaskScheduler with minimal JobManager
        MinimalJobManager jobManager = new MinimalJobManager();
        TaskScheduler scheduler = new TaskScheduler(jobManager);
        System.out.println("[OK] TaskScheduler created\n");
        
        // Define a simple job: calculate 1+2, 3+4, 5+6
        String jobId = "job-addition-001";
        
        System.out.println("--- Creating Simple Addition Job ---");
        Map<String, List<Task>> assignments = new HashMap<>();
        
        // Worker 1 gets 2 tasks
        List<Task> worker1Tasks = Arrays.asList(
            new SimpleAdditionTask(jobId, "task-0", 1, 2),
            new SimpleAdditionTask(jobId, "task-1", 3, 4)
        );
        assignments.put("worker-1", worker1Tasks);
        System.out.println("  Worker 1: 1+2, 3+4");
        
        // Worker 2 gets 1 task
        List<Task> worker2Tasks = Collections.singletonList(
            new SimpleAdditionTask(jobId, "task-2", 5, 6)
        );
        assignments.put("worker-2", worker2Tasks);
        System.out.println("  Worker 2: 5+6");
        System.out.println("  Total: 3 tasks\n");
        
        // Schedule the job
        System.out.println("--- Scheduling Job ---");
        scheduler.scheduleTasks(jobId, assignments);
        int pending = scheduler.getPendingTaskCount(jobId);
        System.out.println("[OK] Job scheduled");
        System.out.println("  Pending tasks: " + pending + "\n");
        
        // Simulate Worker 1 executing its tasks
        System.out.println("--- Simulating Worker 1 Execution ---");
        List<TaskResult> worker1Results = new ArrayList<>();
        for (Task task : worker1Tasks) {
            System.out.println("  Executing: " + task);
            TaskResult result = task.execute();
            worker1Results.add(result);
        }
        System.out.println("[OK] Worker 1 completed execution\n");
        
        // Worker 1 submits results
        System.out.println("--- Worker 1 Submits Results ---");
        scheduler.submitResults(worker1Results);
        pending = scheduler.getPendingTaskCount(jobId);
        System.out.println("[OK] Results submitted");
        System.out.println("  Pending tasks: " + pending + " (expected: 1)\n");
        
        // Check intermediate statistics
        System.out.println("--- Intermediate Job Statistics ---");
        Map<TaskResult.Status, Integer> stats = scheduler.getJobStatistics(jobId);
        System.out.println("  SUCCESS: " + stats.getOrDefault(TaskResult.Status.SUCCESS, 0));
        System.out.println("  WORKING: " + stats.getOrDefault(TaskResult.Status.WORKING, 0));
        System.out.println("  (Job not yet finished)\n");
        
        // Simulate Worker 2 executing its task
        System.out.println("--- Simulating Worker 2 Execution ---");
        List<TaskResult> worker2Results = new ArrayList<>();
        for (Task task : worker2Tasks) {
            System.out.println("  Executing: " + task);
            TaskResult result = task.execute();
            worker2Results.add(result);
        }
        System.out.println("[OK] Worker 2 completed execution\n");
        
        // Worker 2 submits results (job should complete)
        System.out.println("--- Worker 2 Submits Results (Job Complete) ---");
        scheduler.submitResults(worker2Results);
        pending = scheduler.getPendingTaskCount(jobId);
        System.out.println("[OK] Results submitted");
        System.out.println("  Pending tasks: " + pending + " (expected: 0)");
        System.out.println("  Job completed!\n");
        
        // Final statistics
        System.out.println("--- Final Job Statistics ---");
        stats = scheduler.getJobStatistics(jobId);
        System.out.println("  SUCCESS: " + stats.getOrDefault(TaskResult.Status.SUCCESS, 0) + " (expected: 3)");
        System.out.println("  WORKING: " + stats.getOrDefault(TaskResult.Status.WORKING, 0) + " (expected: 0)");
        System.out.println("  NOT_FOUND: " + stats.getOrDefault(TaskResult.Status.NOT_FOUND, 0));
        System.out.println("  FAILURE: " + stats.getOrDefault(TaskResult.Status.FAILURE, 0));
        
        System.out.println("\n=== Simple Job Test Completed ===");
        System.out.println("\nNext: Run TestCompleteIntegration for full end-to-end test");
    }
    
    /**
     * Minimal JobManager that prints when job finishes.
     */
    static class MinimalJobManager extends JobManager {
        public MinimalJobManager() {
            super(new DummySplittingStrategy(), 
                  new DummyAssignmentStrategy(), 
                  null,  // No ClusterMembershipService
                  null); // No TaskScheduler
        }
        
        @Override
        public void onJobFinished(String jobId, List<TaskResult> results) {
            System.out.println("  [JobManager callback] Job " + jobId + " finished with " + results.size() + " results");
        }
    }
    
    static class DummySplittingStrategy implements SplittingStrategy {
        @Override
        public List<Task> split(Job job, Map<String, NodeCapabilities> workers) { 
            return Collections.emptyList(); 
        }
    }
    
    static class DummyAssignmentStrategy implements AssignmentStrategy {
        @Override
        public Map<String, List<Task>> assign(List<Task> tasks, Map<String, NodeCapabilities> workerCapabilities) {
            return Collections.emptyMap();
        }
    }
}

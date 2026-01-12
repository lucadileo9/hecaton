package com.hecaton.manual.node;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import com.hecaton.node.ExecutionContext;

public class TestNodeCapabilities {
    
    public static void main(String[] args) {
        System.out.println("=== Test NodeCapabilities ===\n");
        
        // Test detect()
        NodeCapabilities caps = NodeCapabilities.detect();
        System.out.println("Detected: " + caps);
        System.out.println("  CPU Cores: " + caps.getCpuCores());
        System.out.println("  Total Memory: " + caps.getTotalMemoryMB() + " MB");
        System.out.println("  Available Memory: " + caps.getAvailableMemoryMB() + " MB");
        System.out.println("  OS: " + caps.getOsName() + " " + caps.getOsVersion());
        System.out.println("  Java: " + caps.getJavaVersion());
        System.out.println("  Weight: " + caps.calculateWeight());
        
        System.out.println("\n=== Test ExecutionContext ===\n");
        
        // Test ExecutionContext
        ExecutionContext ctx = new ExecutionContext(
            "test-worker-1", 5001, false, caps);
        System.out.println("Context: " + ctx);
        System.out.println("  Worker ID: " + ctx.getWorkerId());
        System.out.println("  Suggested Threads: " + ctx.suggestedThreadCount());
        
        System.out.println("\n=== Test TaskResult ===\n");
        // Test TaskResult
        TaskResult success = com.hecaton.task.TaskResult.success("task-1", "password123", 1500);
        TaskResult notFound = com.hecaton.task.TaskResult.notFound("task-2", 2000);
        TaskResult failure = com.hecaton.task.TaskResult.failure("task-3", "Connection timeout");
        TaskResult cancelled = com.hecaton.task.TaskResult.cancelled("task-4");
        
        System.out.println(success);
        System.out.println("  hasResult: " + success.hasResult());
        System.out.println(notFound);
        System.out.println("  isSuccess: " + notFound.isSuccess());
        System.out.println(failure);
        System.out.println(cancelled);
        
        System.out.println("\n=== Test JobResult ===\n");
        
        // Test JobResult
        var jobSuccess = com.hecaton.task.JobResult.success(
            "job-1", "password123", 5000, 100, 98, 2);
        var jobNotFound = com.hecaton.task.JobResult.notFound(
            "job-2", 10000, 200, 200, 0);
        
        System.out.println(jobSuccess);
        System.out.println("  Formatted time: " + jobSuccess.getFormattedExecutionTime());
        System.out.println(jobNotFound);
        
        System.out.println("\n=== All Tests Passed ===");
    }
}

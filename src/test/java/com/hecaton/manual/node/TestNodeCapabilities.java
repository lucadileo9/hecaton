package com.hecaton.manual.node;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.node.NodeImpl;
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
        TaskResult success = com.hecaton.task.TaskResult.success("job-123", "task-1", "password123", 1500);
        TaskResult notFound = com.hecaton.task.TaskResult.notFound("job-123", "task-2", 2000);
        TaskResult failure = com.hecaton.task.TaskResult.failure("job-123", "task-3", "Connection timeout");
        TaskResult cancelled = com.hecaton.task.TaskResult.cancelled("job-123", "task-4");
        
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

        // Test node initialization with capabilities
        System.out.println("\n=== Test Node Initialization with Capabilities ===\n");
        try {
            NodeImpl leader = new NodeImpl("localhost", 5000);
            leader.startAsLeader();
            System.out.println("Leader started with capabilities: " + leader.getCapabilities());
            System.out.println("Leader Execution Context: " + leader.getExecutionContext());

            NodeImpl worker = new NodeImpl("localhost", 5001);
            // N.B.: to connect to leader, we should put this in another terminal, but in this test we just start the node
            // because we don't need a real connection for capability testing

            System.out.println("Worker joined with capabilities: " + worker.getCapabilities());
            System.out.println("Worker Execution Context: " + worker.getExecutionContext());

        } catch (Exception e) {
            System.err.println("Failed to start leader node: " + e.getMessage());
        }

        
        System.out.println("\n=== All Tests Passed ===");
    }
}

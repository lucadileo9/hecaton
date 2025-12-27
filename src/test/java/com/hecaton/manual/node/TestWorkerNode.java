package com.hecaton.manual.node;

import com.hecaton.node.NodeImpl;

/**
 * Test 2: Worker Node Joining Cluster
 * 
 * This test verifies that a Worker node can join an existing cluster
 * by connecting to the Leader.
 * 
 * How to run:
 *   Terminal 1: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode" // Start Leader first
 *   Terminal 2: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestWorkerNode" // Then start Worker
 * 
 * Prerequisites:
 *   - Leader must be running on port 5001 (see TestLeaderNode)
 * 
 * Expected behavior:
 *   - Worker creates its own RMI registry on port 5002
 *   - Connects to Leader's registry
 *   - Registers with Leader
 *   - Leader's cluster size becomes 2
 * 
 * Stop with: Ctrl+C
 */
public class TestWorkerNode {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  TEST 2: Worker Node Joining Cluster");
        System.out.println("========================================");
        System.out.println();
        
        // Create Worker on port 5002
        NodeImpl worker = new NodeImpl("localhost", 5002);
        
        // Join Leader's cluster
        worker.joinCluster("localhost", 5001);
        
        System.out.println();
        System.out.println("[OK] Worker node running on port 5002");
        System.out.println("[OK] Connected to Leader at localhost:5001");
        System.out.println("[OK] Registration complete");
        System.out.println();
        System.out.println("Check Leader's terminal for 'New node registered' message");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();
        
        // Keep process alive
        Thread.currentThread().join();
    }
}

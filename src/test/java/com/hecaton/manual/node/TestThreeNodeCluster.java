package com.hecaton.manual.node;

import com.hecaton.election.bully.BullyElection;
import com.hecaton.node.NodeImpl;

import java.util.ArrayList;

/**
 * Test 3: Three Node Cluster (1 Leader + 2 Workers)
 * 
 * This test creates a third Worker node to demonstrate a complete cluster.
 * 
 * How to run:
 *   Terminal 1: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode" // Start Leader first
 *   Terminal 2: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestWorkerNode" // Then start Worker 1
 *   Terminal 3: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestThreeNodeCluster" // Finally start Worker 2
 * 
 * Prerequisites:
 *   - Leader running on port 5001
 *   - Worker 1 running on port 5002
 * 
 * Expected behavior:
 *   - Worker 2 creates registry on port 5003
 *   - Joins cluster
 *   - Leader's cluster size becomes 3
 * 
 * Stop with: Ctrl+C
 */
public class TestThreeNodeCluster {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  TEST 3: Third Node (Worker 2)");
        System.out.println("========================================");
        System.out.println();
        
        // Create second Worker on port 5003
        // Create election strategy for Worker 2
        BullyElection electionStrategy = new BullyElection(null, 0, new ArrayList<>());
        NodeImpl worker2 = new NodeImpl("localhost", 5003, electionStrategy);
        
        // Join Leader's cluster
        worker2.joinCluster("localhost", 5001);
        
        System.out.println();
        System.out.println("[OK] Worker 2 running on port 5003");
        System.out.println("[OK] Connected to Leader at localhost:5001");
        System.out.println("[OK] Cluster now has 3 nodes (1 Leader + 2 Workers)");
        System.out.println();
        System.out.println("Check Leader's terminal for 'Total: 3 nodes' message");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();
        
        // Keep process alive
        Thread.currentThread().join();
    }
}

package com.hecaton.manual.node;

import com.hecaton.election.bully.BullyElection;
import com.hecaton.node.NodeImpl;

import java.util.ArrayList;

/**
 * Test 1: Basic Leader Node Startup
 * 
 * This test verifies that a Leader node can be started successfully
 * and creates its own RMI registry.
 * 
 * How to run:
 *   mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode"
 * 
 * Expected behavior:
 *   - Node creates RMI registry on port 5001
 *   - Binds itself as "node" and "leader"
 *   - Registers itself as first cluster member
 *   - Cluster size: 1
 * 
 * Stop with: Ctrl+C
 */
public class TestLeaderNode {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  TEST 1: Leader Node Startup");
        System.out.println("========================================");
        System.out.println();
        
        // Create and start Leader on port 5001
        // Create election strategy (empty cache initially, will be populated when Workers join)
        BullyElection electionStrategy = new BullyElection(null, 0, new ArrayList<>());
        NodeImpl leader = new NodeImpl("localhost", 5001, electionStrategy);
        leader.startAsLeader();
        
        System.out.println();
        System.out.println("[OK] Leader node running on port 5001");
        System.out.println("[OK] RMI registry created");
        System.out.println("[OK] Cluster size: " + leader.getClusterSize());
        System.out.println();
        System.out.println("Press Ctrl+C to stop");
        System.out.println();
        
        // Keep process alive
        Thread.currentThread().join();
    }
}

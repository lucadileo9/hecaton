package com.hecaton.manual.rmi;

import com.hecaton.election.ElectionStrategyFactory.Algorithm;
import com.hecaton.node.NodeImpl;
import com.hecaton.rmi.LeaderService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Test 5: Node Registration Flow
 * 
 * This test verifies the complete node registration process:
 * 1. Create a temporary Worker node
 * 2. Register it with the Leader
 * 3. Verify Leader's cluster size increased
 * 
 * How to run:
 *   Terminal 1: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode" // Start Leader first, we need it to register
 *   Terminal 2: mvn exec:java -Dexec.mainClass="com.hecaton.manual.rmi.TestNodeRegistration" // Then run this test
 * 
 * Prerequisites:
 *   - Leader must be running on port 5001
 * 
 * What this tests:
 *   - Worker node creation
 *   - RMI registry creation for Worker
 *   - LeaderService.registerNode() method
 *   - Leader's internal node list management
 * 
 * Note: This test creates a temporary node and exits (doesn't keep it running)
 * Note: This test is relatively useless, because durig the tests of the worker nodes (TestWorkerNode and TestThreeNodeCluster) we could already see
 *       the registration logs on the leader side. However, this test explicitly demonstrates the registration flow in isolation.
 */
public class TestNodeRegistration {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  TEST 5: Node Registration Flow");
        System.out.println("========================================");
        System.out.println();
        
        try {
            // Step 1: Create temporary Worker node
            System.out.println("1. Creating temporary Worker node on port 6000...");
            // Create temporary worker with election strategy
            NodeImpl tempWorker = new NodeImpl("localhost", 6000, Algorithm.BULLY);
            System.out.println("   [OK] Worker node created");
            System.out.println("   [OK] Worker ID: " + tempWorker.getId());
            System.out.println();
            
            // Step 2: Connect to Leader's registry
            System.out.println("2. Connecting to Leader's RMI registry...");
            Registry registry = LocateRegistry.getRegistry("localhost", 5001);
            LeaderService leader = (LeaderService) registry.lookup("leader");
            System.out.println("   [OK} Connected to Leader");
            System.out.println();
            
            // Step 3: Register Worker with Leader
            System.out.println("3. Registering Worker with Leader...");
            leader.registerNode(tempWorker);
            System.out.println("   [OK} Registration successful");
            System.out.println();
            
            // Step 4: Verify (check Leader's logs for confirmation)
            System.out.println("4. Verification:");
            System.out.println("   → Check Leader's terminal for:");
            System.out.println("      'New node registered: node-localhost-6000-...'");
            System.out.println("      'Total: 2 nodes' (or higher if other workers exist)");
            System.out.println();

            // The user is expected to manually verify the Leader's output, so we expect an input here
            System.out.println("  Press Enter after verification to complete the test...");
            System.in.read();
            
            System.out.println("========================================");
            System.out.println("  [OK} REGISTRATION TEST COMPLETE");
            System.out.println("========================================");
            System.out.println();
            System.out.println("Note: Temporary worker will exit now.");
            System.out.println("      Leader will still show it in cluster list");
            System.out.println("      (cleanup handled by heartbeat in Phase 1.4)");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("TEST FAILED: " + e.getMessage());
            System.err.println();
            e.printStackTrace();
            System.exit(1);
        }
    }
}

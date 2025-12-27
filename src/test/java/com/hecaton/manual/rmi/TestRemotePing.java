package com.hecaton.manual.rmi;

import com.hecaton.rmi.NodeService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Test 4: Remote RMI Ping
 * 
 * This test verifies basic RMI communication by remotely calling
 * the ping() method on the Leader node.
 * 
 * How to run:
 *   Terminal 1: mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode" // Start Leader first
 *   Terminal 2: mvn exec:java -Dexec.mainClass="com.hecaton.manual.rmi.TestRemotePing" // Then run this test
 * 
 * Prerequisites:
 *   - Leader must be running on port 5001
 * 
 * What this tests:
 *   - RMI Registry connection
 *   - Service lookup
 *   - Remote method invocation
 *   - Serialization/deserialization
 * 
 * Expected output:
 *   - Connection successful
 *   - Ping returns true
 *   - No RemoteException thrown
 */
public class TestRemotePing {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  TEST 4: Remote RMI Ping");
        System.out.println("========================================");
        System.out.println();
        
        // N.B.: here we are not using a NodeImpl, just raw RMI calls to test connectivity, so 
        // we won't see the registration logs on the leader side.
        try {
            // Step 1: Connect to Leader's RMI registry
            System.out.println("1. Connecting to Leader's RMI registry on localhost:5001...");
            Registry registry = LocateRegistry.getRegistry("localhost", 5001);
            System.out.println("   ✓ Connected to registry");
            System.out.println();
            
            // Step 2: Lookup Leader service (we are searching for "leader")
            System.out.println("2. Looking up 'leader' service...");
            NodeService leader = (NodeService) registry.lookup("leader");
            System.out.println("   ✓ Leader service found");
            System.out.println();
            
            // Now we have the LeaderService stub, so we can call ALL its methods remotely, let's try

            // Step 3: Call ping() remotely
            System.out.println("3. Calling ping() method remotely...");
            boolean result = leader.ping();
            System.out.println("   ✓ Ping successful: " + result);
            System.out.println();
            
            // Step 4: Get Leader's ID
            System.out.println("4. Getting Leader's ID...");
            String leaderId = leader.getId();
            System.out.println("   ✓ Leader ID: " + leaderId);
            System.out.println();
            
            // Step 5: Get Leader's status
            System.out.println("5. Getting Leader's status...");
            String status = leader.getStatus();
            System.out.println("   ✓ Leader status: " + status);
            System.out.println();
            
            System.out.println("========================================");
            System.out.println("  ✓ ALL RMI CALLS SUCCESSFUL");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("TEST FAILED: " + e.getMessage());
            System.err.println();
            System.err.println("Common causes:");
            System.err.println("  - Leader not running (start TestLeaderNode first)");
            System.err.println("  - Wrong port (Leader should be on 5001)");
            System.err.println("  - Firewall blocking RMI, I hope not...");
            System.err.println();
            e.printStackTrace();
            System.exit(1);
        }
    }
}

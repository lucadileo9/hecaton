package com.hecaton.manual.integration;

import com.hecaton.node.NodeImpl;

/**
 * INTEGRATION TEST - Worker without ClusterConfig
 * 
 * Verifies:
 *   - Worker can join cluster
 *   - Config consistency between Leader and Workers
 *   - Heartbeat functions correctly
 * 
 * Run:
 *   Terminal 2: mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5002'
 *   Terminal 3: mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5003'
 */
public class TestWorkerWithoutConfig {
    
    public static void main(String[] args) throws Exception {
        // Parse port from args (default 5002)
        int port = 5002;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST - Worker without ClusterConfig (Port " + port + ")            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // STEP 1: Create same config as Leader (for consistency)
        System.out.println("--- This worker is created without a config ---");
        System.out.println("--- Starting Worker Node ---");
        NodeImpl worker = new NodeImpl("localhost", port);
        System.out.println();
        
        // STEP 3: Join cluster
        System.out.println("--- Joining Cluster ---");
        worker.joinCluster("localhost", 5001);
        System.out.println();
        
        System.out.println("[OK] Worker running on port " + port);
        System.out.println("[OK] Sending heartbeats to Leader");
        System.out.println("  Press Ctrl+C to stop\n");
        
        // Keep alive
        Thread.sleep(Long.MAX_VALUE);
    }
}

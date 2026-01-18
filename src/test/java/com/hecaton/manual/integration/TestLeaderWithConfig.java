package com.hecaton.manual.integration;

import com.hecaton.node.ClusterConfig;
import com.hecaton.node.NodeImpl;
import com.hecaton.scheduler.JobManager;

/**
 * INTEGRATION TEST - Leader with ClusterConfig
 * 
 * Verifies:
 *   - ClusterConfig creation and toString()
 *   - Leader startup without parameters (uses config)
 *   - JobManager initialized with strategies from config
 *   - Workers can join
 * 
 * Run:
 *   mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestLeaderWithConfig'
 * 
 * Then start Workers in other terminals:
 *   mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithConfig' '-Dexec.args=5002'
 *   mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithConfig' '-Dexec.args=5003'
 */
public class TestLeaderWithConfig {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST - Leader with ClusterConfig                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // STEP 1: Create default config
        System.out.println("--- Creating ClusterConfig ---");
        ClusterConfig config = new ClusterConfig.Builder().build();
        System.out.println("[OK] Config created with defaults:");
        System.out.println("  " + config);
        System.out.println();
        
        // STEP 2: Start Leader with config
        System.out.println("--- Starting Leader Node ---");
        NodeImpl leader = new NodeImpl("localhost", 5001, config);
        leader.startAsLeader();  // NO parameters - uses config!
        System.out.println();
        
        // STEP 3: Verify JobManager initialized
        System.out.println("--- Verifying JobManager ---");
        try {
            JobManager jobManager = leader.getJobManager();
            if (jobManager == null) {
                throw new Exception("JobManager is null");
            }
            System.out.println("[OK] JobManager initialized");
            System.out.println("  Splitting: " + config.getSplittingStrategy().getName());
            System.out.println("  Assignment: " + config.getAssignmentStrategy().getName());
        } catch (Exception e) {
            System.out.println("[ERROR] JobManager error: " + e.getMessage());
        }
        System.out.println();
        
        // STEP 4: Monitor cluster
        System.out.println("--- Cluster Status ---");
        System.out.println("Waiting for Workers to join...");
        System.out.println("(Start Workers in other terminals with port 5002, 5003)\n");
        
    }
}

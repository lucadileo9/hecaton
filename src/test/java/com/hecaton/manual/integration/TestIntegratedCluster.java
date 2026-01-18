package com.hecaton.manual.integration;

import com.hecaton.node.ClusterConfig;
import com.hecaton.node.NodeImpl;
import com.hecaton.scheduler.JobManager;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.sum_range.SumRangeJob;

/**
 * FULL INTEGRATION TEST - Leader submits real Job
 * 
 * Tests entire pipeline:
 *   1. Leader with ClusterConfig
 *   2. Workers join cluster
 *   3. Submit SumRangeJob(1-100)
 *   4. Verify splitting (UniformSplitting)
 *   5. Verify assignment (RoundRobin)
 *   6. Verify execution (Workers execute tasks)
 *   7. Verify aggregation (Leader sums results)
 * 
 * Expected result: 1+2+...+100 = 5050
 * 
 * Run:
 *   Terminal 1 (Leader): 
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestIntegratedCluster'
 *   
 *   Terminal 2-3 (Workers):
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5002'
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5003'
 */
public class TestIntegratedCluster {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  FULL INTEGRATION TEST - Job Execution                   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // Create config with defaults
        ClusterConfig config = new ClusterConfig.Builder().build();
        System.out.println("Config: " + config + "\n");
        
        // Start Leader
        NodeImpl leader = new NodeImpl("localhost", 5001, config);
        leader.startAsLeader();
        System.out.println();
        
        // Wait for Workers to join
        System.out.println("Waiting 20 seconds for Workers to join...");
        System.out.println("(Start 2-3 Workers in other terminals)\n");
        Thread.sleep(20000);
        
        int workers = leader.getClusterSize() - 1;
        System.out.println("Cluster ready: " + workers + " worker(s)\n");
        
        if (workers == 0) {
            System.out.println("⚠ No workers! Start Workers and re-run.");
            System.exit(1);
        }
        
        // Submit Job
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Submitting Job: Sum(1-100)                              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        Job job = new SumRangeJob(100);
        JobManager jobManager = leader.getJobManager();
        
        System.out.println("Expected result: 5050 (sum of 1 to 100)\n");
        
        JobResult result = jobManager.submitJob(job);
        
        // Display result
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  JOB COMPLETED                                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println("Job ID:   " + result.getJobId());
        System.out.println("Status:   " + (result.isSuccess() ? "[OK] SUCCESS" : " [ERROR] FAILED"));

        System.out.println("Result:   " + result.getData());
        System.out.println("Expected: 5050");
        // System.out.println("Match:    " + (result.getResult().equals(5050L) ? "[OK] YES" : " [ERROR] NO"));
        System.out.println();
        
        System.out.println("Test complete. Press Ctrl+C to exit.");
        Thread.sleep(5000);
    }
}

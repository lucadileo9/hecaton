package com.hecaton.manual.integration;

import com.hecaton.node.ClusterConfig;
import com.hecaton.node.NodeImpl;
import com.hecaton.scheduler.JobManager;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.password_crack.PasswordCrackJob;
import com.hecaton.task.splitting.DynamicSplitting;

/**
 * EARLY TERMINATION TEST - Password Cracking with Job Cancellation
 * 
 * Tests early termination feature:
 *   1. Leader + 2-3 Workers
 *   2. Submit PasswordCrackJob with easy-to-find password
 *   3. One worker finds result quickly
 *   4. Worker auto-cancels its remaining tasks
 *   5. Leader broadcasts cancelJob() to other workers
 *   6. Other workers interrupt their tasks
 *   7. Job completes MUCH faster than full search
 * 
 * Expected behavior:
 *   - Password "abc" found quickly (in first portion of keyspace)
 *   - Workers receive cancelJob() and log task cancellations
 *   - Job completes in seconds instead of minutes
 *   - Logs show: [EARLY TERMINATION], [CANCELLATION], task cancelled messages
 * 
 * Setup:
 *   Target: MD5("abc") = "900150983cd24fb0d6963f7d28e17f72"
 *   Charset: lowercase alphabet (26 chars)
 *   Length: 1-3 characters
 *   Total combinations: 26 + 26^2 + 26^3 = 18,278
 *   Password "abc" is at index ~2,000 → found very early!
 * 
 * Run:
 *   Terminal 1 (Leader): 
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestEarlyTermination'
 *   
 *   Terminal 2-3 (Workers):
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5002'
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5003'
 */
public class TestEarlyTermination {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  EARLY TERMINATION TEST - Password Cracking              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // Create config with defaults
        ClusterConfig config = new ClusterConfig.Builder().splittingStrategy(new DynamicSplitting(4))
        .build();
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
        
        // Create Password Cracking Job
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Submitting Job: Crack MD5 Password                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // MD5 of "nnnnnnnn" is "6eff2276c4f875c14c569fbc11e4c2a0 "
        String passwordToFind = "nnnnnnnn";
        String targetHash = "6eff2276c4f875c14c569fbc11e4c2a0";
        String charset = PasswordCrackJob.CHARSET_LOWERCASE;
        int maxLength = passwordToFind.length(); 
                
        System.out.println("Target: MD5('" + passwordToFind + "') = " + targetHash);
        System.out.println("Charset: lowercase (a-z)");
        System.out.println("Length: 1-" + maxLength + " characters");
        System.out.println("Total combinations: " + calculateCombinations(26, maxLength));
        System.out.println("Expected password: '" + passwordToFind + "' (should be found VERY quickly!)\n");
        
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  WATCH FOR EARLY TERMINATION LOGS:                       ║");
        System.out.println("║  - [EARLY TERMINATION] triggering local cancellation     ║");
        System.out.println("║  - [EARLY TERMINATION] Broadcasting cancelJob()          ║");
        System.out.println("║  - [CANCELLATION] Received cancelJob() request           ║");
        System.out.println("║  - [CANCELLATION] Cancelled N tasks                      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        Job job = new PasswordCrackJob(targetHash, charset, maxLength);
        JobManager jobManager = leader.getJobManager();
        
        long startTime = System.currentTimeMillis();
        JobResult result = jobManager.submitJob(job);
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Display result
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  JOB COMPLETED                                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println("Job ID:        " + result.getJobId());
        System.out.println("Status:        " + (result.isSuccess() ? "[OK] SUCCESS" : "[ERROR] FAILED"));
        System.out.println("Password:      " + result.getData());
        System.out.println("Expected:      " + passwordToFind);
        System.out.println("Match:         " + (passwordToFind.equals(result.getData()) ? "[OK] YES" : "[ERROR] NO"));
        System.out.println("Time:          " + executionTime + " ms");
        System.out.println("Tasks Total:   " + result.getTotalTasks());
        System.out.println("Tasks Done:    " + result.getCompletedTasks());
        System.out.println("Tasks Failed:  " + result.getFailedTasks());
        
        // Calculate efficiency
        int cancelledTasks = result.getTotalTasks() - result.getCompletedTasks() - result.getFailedTasks();
        if (cancelledTasks > 0) {
            System.out.println("Tasks Cancelled: " + cancelledTasks + " (early termination worked!)");
            double efficiency = (double) cancelledTasks / result.getTotalTasks() * 100;
            System.out.println("Efficiency:    " + String.format("%.1f%%", efficiency) + " of work avoided");
        } else {
            System.out.println("⚠ Warning: No tasks cancelled (early termination may not have triggered)");
        }
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  VERIFICATION                                             ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        
        if (result.isSuccess() && passwordToFind.equals(result.getData())) {
            System.out.println("[OK] Password found correctly!");
        } else {
            System.out.println("[NO] Password NOT found or incorrect!");
        }
        
        if (cancelledTasks > 0) {
            System.out.println("[OK] Early termination worked (tasks cancelled)");
        } else {
            System.out.println("[NO] Early termination did NOT work (no cancellations)");
        }
        
        if (executionTime < 30000) {  // Should complete in < 30 seconds
            System.out.println("[OK] Fast execution (early termination effective)");
        } else {
            System.out.println("[NO] Slow execution (may have searched entire space)");
        }
        
        System.out.println("\nTest complete. Check logs above for early termination messages.");
        System.out.println("Press Ctrl+C to exit.");
        Thread.sleep(5000);
    }
    
    /**
     * Calculate total combinations for passwords of length 1 to maxLength.
     */
    private static long calculateCombinations(int base, int maxLength) {
        long total = 0;
        for (int len = 1; len <= maxLength; len++) {
            total += Math.pow(base, len);
        }
        return total;
    }
}

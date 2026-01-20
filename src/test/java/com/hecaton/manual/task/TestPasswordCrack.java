package com.hecaton.manual.task;

import com.hecaton.node.ClusterConfig;
import com.hecaton.node.NodeImpl;
import com.hecaton.scheduler.JobManager;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.password_crack.PasswordCrackJob;

/**
 * INTEGRATION TEST - Password Cracking with Real Cluster
 * 
 * Tests:
 *   - PasswordCrackJob splitting across workers
 *   - MD5 brute-force execution
 *   - Early termination when password found
 *   - Result aggregation
 * 
 * Test Cases:
 *   1. Short password (4 chars, fast) - Should find quickly
 *   2. Known MD5 hash - Verify correct password returned
 *   3. Early termination - Workers stop when one finds result
 * 
 * Run:
 *   Terminal 1 (Leader): 
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.task.TestPasswordCrack'
 *   
 *   Terminal 2-3 (Workers):
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5002'
 *     mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithoutConfig' '-Dexec.args=5003'
 */
public class TestPasswordCrack {
    
    public static void main(String[] args) throws Exception {
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  PASSWORD CRACKING TEST - MD5 Brute-Force               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // Start Leader
        ClusterConfig config = new ClusterConfig.Builder().build();
        NodeImpl leader = new NodeImpl("localhost", 5001, config);
        leader.startAsLeader();
        System.out.println();
        
        // Wait for Workers
        System.out.println("Waiting 15 seconds for Workers to join...");
        System.out.println("(Start 2-3 Workers in other terminals)\n");
        Thread.sleep(15000);
        
        int workers = leader.getClusterSize() - 1;
        System.out.println("Cluster ready: " + workers + " worker(s)\n");
        
        if (workers == 0) {
            System.out.println("⚠ No workers! Start Workers and re-run.");
            System.exit(1);
        }
        
        JobManager jobManager = leader.getJobManager();
        
        // ==================== TEST CASE 1: Known Short Password ====================
        
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST 1: Crack known password \"test\"                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // MD5("test") = "098f6bcd4621d373cade4e832627b4f6"
        Job job1 = new PasswordCrackJob(
            "098f6bcd4621d373cade4e832627b4f6",
            PasswordCrackJob.CHARSET_LOWERCASE,
            4  // 26^4 = 456,976 combinations (fast)
        );
        
        System.out.println("Target: MD5(\"test\")");
        System.out.println("Charset: lowercase (26 chars)");
        System.out.println("Length: 4");
        System.out.println("Combinations: 456,976");
        System.out.println("Expected result: \"test\"\n");
        
        long start1 = System.currentTimeMillis();
        JobResult result1 = jobManager.submitJob(job1);
        long elapsed1 = System.currentTimeMillis() - start1;
        
        displayResult(result1, "test", elapsed1);
        
        // ==================== TEST CASE 2: Longer Password ====================
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST 2: Crack known password \"hello\"                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // MD5("hello") = "5d41402abc4b2a76b9719d911017c592"
        Job job2 = new PasswordCrackJob(
            "5d41402abc4b2a76b9719d911017c592",
            PasswordCrackJob.CHARSET_LOWERCASE,
            5  // 26^5 = 11,881,376 combinations (moderate)
        );
        
        System.out.println("Target: MD5(\"hello\")");
        System.out.println("Charset: lowercase (26 chars)");
        System.out.println("Length: 5");
        System.out.println("Combinations: 11,881,376");
        System.out.println("Expected result: \"hello\"\n");
        
        long start2 = System.currentTimeMillis();
        JobResult result2 = jobManager.submitJob(job2);
        long elapsed2 = System.currentTimeMillis() - start2;
        
        displayResult(result2, "hello", elapsed2);
        
        // ==================== TEST CASE 3: Not Found ====================
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST 3: Password not in search space                    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // MD5("abc123") - but we search only lowercase (won't find digits)
        Job job3 = new PasswordCrackJob(
            "e99a18c428cb38d5f260853678922e03",  // MD5("abc123")
            PasswordCrackJob.CHARSET_LOWERCASE,
            6  // Only lowercase, won't find "abc123"
        );
        
        System.out.println("Target: MD5(\"abc123\")");
        System.out.println("Charset: lowercase only (no digits)");
        System.out.println("Length: 6");
        System.out.println("Expected result: NOT_FOUND\n");
        
        long start3 = System.currentTimeMillis();
        JobResult result3 = jobManager.submitJob(job3);
        long elapsed3 = System.currentTimeMillis() - start3;
        
        displayResult(result3, null, elapsed3);
        
        // ==================== SUMMARY ====================
        
        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  TEST SUMMARY                                             ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        
        System.out.println("Test 1 (\"test\"):  " + 
            (result1.isSuccess() && "test".equals(result1.getData()) ? "[OK] PASS" : "[ERROR] FAIL"));
        System.out.println("Test 2 (\"hello\"): " + 
            (result2.isSuccess() && "hello".equals(result2.getData()) ? "[OK] PASS" : "[ERROR] FAIL"));
        System.out.println("Test 3 (NOT_FOUND): " + 
            (result3.getStatus() == JobResult.Status.NOT_FOUND ? "[OK] PASS" : "[ERROR] FAIL"));
        
        System.out.println("\nAll tests complete. Press Ctrl+C to exit.");
        Thread.sleep(5000);
    }
    
    private static void displayResult(JobResult result, String expectedPassword, long elapsedMs) {
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("Status:   " + result.getStatus());
        
        if (result.isSuccess()) {
            System.out.println("Password: " + result.getData());
            if (expectedPassword != null) {
                boolean match = expectedPassword.equals(result.getData());
                System.out.println("Match:    " + (match ? "[OK] YES" : "[ERROR] NO"));
            }
        } else if (result.getStatus() == JobResult.Status.NOT_FOUND) {
            System.out.println("Result:   Password not found in search space");
        } else {
            System.out.println("Error:    " + result.getErrorMessage());
        }
        
        System.out.println("Time:     " + elapsedMs + "ms (wall clock)");
        System.out.println("Tasks:    " + result.getCompletedTasks() + "/" + result.getTotalTasks() + " completed");
        
        if (result.getFailedTasks() > 0) {
            System.out.println("Failed:   " + result.getFailedTasks() + " tasks");
        }
        
        System.out.println("─────────────────────────────────────────────────────────────");
    }
}

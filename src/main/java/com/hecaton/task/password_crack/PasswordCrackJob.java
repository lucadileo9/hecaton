package com.hecaton.task.password_crack;

import com.hecaton.task.AbstractJob;
import com.hecaton.task.JobResult;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Password cracking job using MD5 brute-force.
 * 
 * Divides the keyspace (charset^passwordLength) into equal ranges and distributes
 * them across workers. First worker to find a match triggers early termination.
 * 
 * Example:
 *   Target: MD5("hello") = "5d41402abc4b2a76b9719d911017c592"
 *   Charset: "abcdefghijklmnopqrstuvwxyz" (26 chars)
 *   Length: 5
 *   Total combinations: 26^5 = 11,881,376
 *   
 *   With 3 workers:
 *     Task 1: indices 0 - 3,960,458 ("aaaaa" - "hzzzz")
 *     Task 2: indices 3,960,459 - 7,920,917 ("iaaaa" - "pzzzz")
 *     Task 3: indices 7,920,918 - 11,881,375 ("qaaaa" - "zzzzz")
 */
public class PasswordCrackJob extends AbstractJob {
    
    private static final Logger log = LoggerFactory.getLogger(PasswordCrackJob.class);
    
    // Common charset presets for convenience
    public static final String CHARSET_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String CHARSET_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String CHARSET_DIGITS = "0123456789";
    public static final String CHARSET_ALPHANUMERIC = CHARSET_LOWERCASE + CHARSET_UPPERCASE + CHARSET_DIGITS;
    public static final String CHARSET_ALPHA = CHARSET_LOWERCASE + CHARSET_UPPERCASE;
    
    private final String targetHash;      // MD5 hash to crack (32 hex chars, lowercase)
    private final String charset;         // Character set to use for brute-force
    private final int passwordLength;     // Fixed password length
    private final long totalCombinations; // charset.length() ^ passwordLength
    
    /**
     * Creates a password cracking job.
     * 
     * @param targetHash MD5 hash (32 hex characters, case-insensitive)
     * @param charset Character set for brute-force (e.g., "abc" or CHARSET_LOWERCASE)
     * @param passwordLength Fixed password length to try
     * @throws IllegalArgumentException if parameters are invalid
     */
    public PasswordCrackJob(String targetHash, String charset, int passwordLength) {
        super();
        setJobId();
        
        // Validate input
        if (targetHash == null || !targetHash.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Invalid MD5 hash (must be 32 hex characters): " + targetHash);
        }
        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("Charset cannot be empty");
        }
        if (passwordLength < 1 || passwordLength > 8) {
            throw new IllegalArgumentException("Password length must be 1-8 (got: " + passwordLength + ")");
        }
        
        this.targetHash = targetHash.toLowerCase(); // Normalize to lowercase
        this.charset = charset;
        this.passwordLength = passwordLength;
        this.totalCombinations = calculateTotalCombinations(charset.length(), passwordLength);
        
        log.debug("PasswordCrackJob created: hash={}, charset={} chars, length={}, combinations={}", 
                  targetHash.substring(0, 8) + "...", charset.length(), passwordLength, totalCombinations);
    }
    
    /**
     * Calculates total combinations (base^length).
     * Uses Math.pow and checks for overflow.
     */
    private long calculateTotalCombinations(int base, int length) {
        double result = Math.pow(base, length);
        
        if (result > Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                String.format("Keyspace too large: %d^%d exceeds Long.MAX_VALUE", base, length));
        }
        
        return (long) result;
    }
    
    @Override
    public List<Task> split(int numTasks) {
        if (numTasks <= 0) {
            throw new IllegalArgumentException("numTasks must be positive: " + numTasks);
        }
        
        log.info("Splitting job {} into {} tasks (total combinations: {})", 
                 getJobId(), numTasks, totalCombinations);
        
        List<Task> tasks = new ArrayList<>();
        long rangeSize = totalCombinations / numTasks;
        
        for (int i = 0; i < numTasks; i++) {
            long startIndex = i * rangeSize;
            long endIndex;
            
            // Last task gets remainder
            if (i == numTasks - 1) {
                endIndex = totalCombinations - 1;
            } else {
                endIndex = (i + 1) * rangeSize - 1;
            }
            
            String taskId = "task-" + i;
            tasks.add(new PasswordCrackTask(
                getJobId(), 
                taskId, 
                targetHash, 
                charset, 
                passwordLength, 
                startIndex, 
                endIndex
            ));
            
            log.debug("  {} -> range [{}, {}] ({} combinations)", 
                      taskId, startIndex, endIndex, endIndex - startIndex + 1);
        }
        
        return tasks;
    }
    
    @Override
    public JobResult aggregateResults(List<TaskResult> results) {
        log.debug("Aggregating {} task results for job {}", results.size(), getJobId());
        
        int completedTasks = 0;
        int failedTasks = 0;
        long totalExecutionTime = 0;
        
        // Look for first successful result (password found)
        for (TaskResult result : results) {
            totalExecutionTime += result.getExecutionTimeMs();
            
            if (result.getStatus() == TaskResult.Status.SUCCESS && result.hasResult()) {
                // PASSWORD FOUND!
                String password = (String) result.getData();
                completedTasks = (int) results.stream()
                    .filter(r -> r.getStatus() == TaskResult.Status.SUCCESS || 
                                 r.getStatus() == TaskResult.Status.NOT_FOUND)
                    .count();
                
                log.info("[CRACKED] Password found: '{}' (completed {}/{} tasks)", 
                         password, completedTasks, results.size());
                
                return JobResult.success(
                    getJobId(), 
                    password, 
                    totalExecutionTime,
                    results.size(),
                    completedTasks,
                    failedTasks
                );
            }
            
            if (result.getStatus() == TaskResult.Status.FAILURE) {
                failedTasks++;
            } else if (result.isSuccess()) {
                completedTasks++;
            }
        }
        
        // No result found - exhausted search space
        log.info("[NOT_FOUND] Exhausted search space ({} combinations, {}/{} tasks completed)", 
                 totalCombinations, completedTasks, results.size());
        
        return JobResult.notFound(
            getJobId(), 
            totalExecutionTime,
            results.size(),
            completedTasks,
            failedTasks
        );
    }
    
    @Override
    public boolean supportsEarlyTermination() {
        // Stop all tasks as soon as one finds the password
        return true;
    }
    
    @Override
    public void onStart() {
        log.info("[START] PasswordCrackJob {} started", getJobId());
        log.info("  Target hash: {}...", targetHash.substring(0, 16));
        log.info("  Charset: {} characters", charset.length());
        log.info("  Password length: {}", passwordLength);
        log.info("  Total combinations: {}", String.format("%,d", totalCombinations));
    }
    
    @Override
    public void onComplete(JobResult result) {
        if (result.isSuccess()) {
            log.info("[COMPLETE] Password cracked: '{}'", result.getData());
        } else if (result.getStatus() == JobResult.Status.NOT_FOUND) {
            log.info("[COMPLETE] Password not found in search space");
        } else {
            log.error("[COMPLETE] Job failed: {}", result.getErrorMessage());
        }
        log.info("  Execution time: {}", result.getFormattedExecutionTime());
        log.info("  Tasks completed: {}/{}", result.getCompletedTasks(), result.getTotalTasks());
    }
    
    // ==================== Getters ====================
    
    public String getTargetHash() {
        return targetHash;
    }
    
    public String getCharset() {
        return charset;
    }
    
    public int getPasswordLength() {
        return passwordLength;
    }
    
    public long getTotalCombinations() {
        return totalCombinations;
    }
}

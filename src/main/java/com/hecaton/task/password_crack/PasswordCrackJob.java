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
    private final int minLength;          // Minimum password length to try
    private final int maxLength;          // Maximum password length to try
    private final long totalCombinations; // Sum of charset.length()^len for all lengths
    
    /**
     * Creates a password cracking job with length range.
     * Tries all passwords from minLength to maxLength (inclusive).
     * Shorter passwords are tried first (more efficient).
     * 
     * @param targetHash MD5 hash (32 hex characters, case-insensitive)
     * @param charset Character set for brute-force (e.g., "abc" or CHARSET_LOWERCASE)
     * @param minLength Minimum password length (1-8)
     * @param maxLength Maximum password length (1-8, >= minLength)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public PasswordCrackJob(String targetHash, String charset, int minLength, int maxLength) {
        super();
        
        // Validate input
        if (targetHash == null || !targetHash.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Invalid MD5 hash (must be 32 hex characters): " + targetHash);
        }
        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("Charset cannot be empty");
        }
        if (minLength < 1 || minLength > 8) {
            throw new IllegalArgumentException("minLength must be 1-8 (got: " + minLength + ")");
        }
        if (maxLength < 1 || maxLength > 8) {
            throw new IllegalArgumentException("maxLength must be 1-8 (got: " + maxLength + ")");
        }
        if (minLength > maxLength) {
            throw new IllegalArgumentException(
                String.format("minLength (%d) cannot exceed maxLength (%d)", minLength, maxLength));
        }
        
        this.targetHash = targetHash.toLowerCase(); // Normalize to lowercase
        this.charset = charset;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.totalCombinations = calculateTotalCombinationsRange(charset.length(), minLength, maxLength);
        
        log.debug("PasswordCrackJob created: hash={}, charset={} chars, length range=[{},{}], combinations={}", 
                  targetHash.substring(0, 8) + "...", charset.length(), minLength, maxLength, totalCombinations);
    }
    
    /**
     * Creates a password cracking job with fixed length (backward compatibility).
     * 
     * @param targetHash MD5 hash (32 hex characters, case-insensitive)
     * @param charset Character set for brute-force
     * @param passwordLength Fixed password length to try (1-8)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public PasswordCrackJob(String targetHash, String charset, int passwordLength) {
        this(targetHash, charset, passwordLength, passwordLength);
    }
    
    /**
     * Calculates total combinations across all password lengths.
     * Sum of base^minLen + base^(minLen+1) + ... + base^maxLen
     * 
     * Example: charset=26, minLen=1, maxLen=3
     *   26^1 + 26^2 + 26^3 = 26 + 676 + 17,576 = 18,278
     */
    private long calculateTotalCombinationsRange(int base, int minLen, int maxLen) {
        long total = 0;
        
        for (int len = minLen; len <= maxLen; len++) {
            double combinations = Math.pow(base, len);
            
            if (combinations > Long.MAX_VALUE) {
                throw new IllegalArgumentException(
                    String.format("Keyspace too large at length %d: %d^%d exceeds Long.MAX_VALUE", 
                                  len, base, len));
            }
            
            long lenCombinations = (long) combinations;
            
            // Check for overflow when adding
            if (total > Long.MAX_VALUE - lenCombinations) {
                throw new IllegalArgumentException(
                    String.format("Total keyspace too large: overflow at length %d", len));
            }
            
            total += lenCombinations;
        }
        
        return total;
    }
    
    @Override
    public List<Task> split(int numTasks) {
        if (numTasks <= 0) {
            throw new IllegalArgumentException("numTasks must be positive: " + numTasks);
        }
        
        log.info("Splitting job {} into tasks (total combinations: {}, length range: [{}, {}])", 
                 getJobId(), totalCombinations, minLength, maxLength);
        
        List<Task> tasks = new ArrayList<>();
        int taskCounter = 0;
        
        // Iterate through lengths from shortest to longest (more efficient)
        for (int len = minLength; len <= maxLength; len++) {
            long lenCombinations = (long) Math.pow(charset.length(), len);
            
            // Distribute this length's combinations across workers
            // Use min(numTasks, lenCombinations) to avoid empty tasks
            int tasksForLength = (int) Math.min(numTasks, lenCombinations);
            long rangeSize = lenCombinations / tasksForLength;
            
            log.debug("  Length {} -> {} combinations, {} tasks", len, lenCombinations, tasksForLength);
            
            for (int i = 0; i < tasksForLength; i++) {
                long startIndex = i * rangeSize;
                long endIndex;
                
                // Last task for this length gets remainder
                if (i == tasksForLength - 1) {
                    endIndex = lenCombinations - 1;
                } else {
                    endIndex = (i + 1) * rangeSize - 1;
                }
                
                String taskId = String.format("task-len%d-%d", len, i);
                tasks.add(new PasswordCrackTask(
                    getJobId(), 
                    taskId, 
                    targetHash, 
                    charset, 
                    len,  // Current length
                    startIndex, 
                    endIndex
                ));
                
                log.debug("    {} -> range [{}, {}] ({} combinations)", 
                          taskId, startIndex, endIndex, endIndex - startIndex + 1);
                
                taskCounter++;
            }
        }
        
        log.info("Created {} total tasks across {} password lengths", taskCounter, maxLength - minLength + 1);
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
        if (minLength == maxLength) {
            log.info("  Password length: {}", minLength);
        } else {
            log.info("  Password length range: [{}, {}]", minLength, maxLength);
        }
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
    
    public int getMinLength() {
        return minLength;
    }
    
    public int getMaxLength() {
        return maxLength;
    }
    
    /**
     * @deprecated Use getMinLength() and getMaxLength() instead
     */
    @Deprecated
    public int getPasswordLength() {
        return maxLength;  // Backward compatibility
    }
    
    public long getTotalCombinations() {
        return totalCombinations;
    }
}

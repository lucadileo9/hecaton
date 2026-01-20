package com.hecaton.task.password_crack;

import com.hecaton.task.AbstractTask;
import com.hecaton.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Task that performs brute-force password cracking on a specific range of the keyspace.
 * 
 * Converts indices to passwords using base-N conversion, computes MD5 hash,
 * and compares against target. Supports interruption for early termination.
 * 
 * Example:
 *   Charset: "abc" (base 3)
 *   Length: 2
 *   Range: indices 0-8
 *   
 *   Index → Password → MD5
 *   0 → "aa" → compute hash
 *   1 → "ab" → compute hash
 *   2 → "ac" → compute hash
 *   3 → "ba" → compute hash
 *   ...
 *   8 → "cc" → compute hash
 */
public class PasswordCrackTask extends AbstractTask {
    
    private static final Logger log = LoggerFactory.getLogger(PasswordCrackTask.class);
    
    // Interruption check frequency (balance responsiveness vs performance)
    private static final int INTERRUPTION_CHECK_INTERVAL = 1000;
    
    // Progress reporting frequency (log every N checks)
    private static final int PROGRESS_REPORT_INTERVAL = 10000;
    
    private final String targetHash;
    private final String charset;
    private final int passwordLength;
    private final long startIndex;
    private final long endIndex;
    
    // Transient: cannot serialize MessageDigest, must recreate on worker
    private transient MessageDigest md5;
    
    // Reusable buffer for password generation (performance optimization)
    private transient char[] passwordBuffer;
    
    /**
     * Creates a password cracking task for a specific range.
     * 
     * @param jobId Parent job ID
     * @param taskId Unique task ID
     * @param targetHash MD5 hash to match (lowercase)
     * @param charset Character set
     * @param passwordLength Fixed password length
     * @param startIndex Inclusive start index in keyspace
     * @param endIndex Inclusive end index in keyspace
     */
    public PasswordCrackTask(String jobId, String taskId, String targetHash, 
                            String charset, int passwordLength, 
                            long startIndex, long endIndex) {
        super(jobId, taskId);  // Pass explicit taskId
        this.targetHash = targetHash;
        this.charset = charset;
        this.passwordLength = passwordLength;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }
    
    @Override
    public TaskResult execute() {
        long startTime = System.currentTimeMillis();
        long combinationsChecked = 0;
        
        try {
            // Initialize MD5 and reusable buffer (must be done on worker, not serialized)
            this.md5 = MessageDigest.getInstance("MD5");
            this.passwordBuffer = new char[passwordLength];
            
            long totalCombinations = endIndex - startIndex + 1;
            log.debug("[EXEC] {} checking range [{}, {}] ({} combinations)", 
                      getTaskId(), startIndex, endIndex, totalCombinations);
            
            // Brute-force loop
            for (long index = startIndex; index <= endIndex; index++) {
                combinationsChecked++;
                
                // Check for interruption every N iterations (early termination)
                if (combinationsChecked % INTERRUPTION_CHECK_INTERVAL == 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        log.debug("[CANCELLED] {} interrupted after {} checks", 
                                  getTaskId(), combinationsChecked);
                        return TaskResult.cancelled(getJobId(), getTaskId());
                    }
                }
                
                // Progress reporting (useful for long-running tasks)
                if (combinationsChecked % PROGRESS_REPORT_INTERVAL == 0) {
                    double progress = (combinationsChecked * 100.0) / totalCombinations;
                    log.debug("[PROGRESS] {} - {}/{} checks ({:.1f}%)", 
                              getTaskId(), combinationsChecked, totalCombinations, progress);
                }
                
                // Convert index to password string
                String candidate = indexToPassword(index);
                
                // Compute MD5 hash
                String hash = computeHash(candidate);
                
                // Check for match
                if (hash.equals(targetHash)) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    log.info("[FOUND] {} cracked password: '{}' after {} checks in {}ms", 
                             getTaskId(), candidate, combinationsChecked, executionTime);
                    return TaskResult.success(getJobId(), getTaskId(), candidate, executionTime);
                }
            }
            
            // No match found in this range
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("[NOT_FOUND] {} completed {} checks in {}ms (no match)", 
                      getTaskId(), combinationsChecked, executionTime);
            return TaskResult.notFound(getJobId(), getTaskId(), executionTime);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("[ERROR] {} MD5 algorithm not available", getTaskId(), e);
            return TaskResult.failure(getJobId(), getTaskId(), "MD5 not available: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ERROR] {} unexpected error after {} checks", getTaskId(), combinationsChecked, e);
            return TaskResult.failure(getJobId(), getTaskId(), "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Converts a numeric index to a password string using base-N conversion.
     * Uses reusable buffer to avoid allocating new char[] on each call.
     * 
     * Algorithm:
     *   - Treat index as a number in base charset.length()
     *   - Extract digits right-to-left by repeated modulo and division
     *   - Map each digit to corresponding charset character
     * 
     * Example (charset="abc", length=3):
     *   Index 0 → "aaa"
     *   Index 1 → "aab"
     *   Index 2 → "aac"
     *   Index 3 → "aba"
     *   Index 26 → "baa"
     * 
     * @param index Position in keyspace (0 to charset.length()^passwordLength - 1)
     * @return Password string of length passwordLength
     */
    private String indexToPassword(long index) {
        int base = charset.length();
        
        for (int i = passwordLength - 1; i >= 0; i--) {
            int digit = (int) (index % base);
            passwordBuffer[i] = charset.charAt(digit);
            index /= base;
        }
        
        return new String(passwordBuffer);
    }
    
    /**
     * Computes MD5 hash of a password.
     * 
     * @param password Plain-text password
     * @return Lowercase hex string (32 characters)
     */
    private String computeHash(String password) {
        md5.reset();
        byte[] digest = md5.digest(password.getBytes());
        return bytesToHex(digest);
    }
    
    /**
     * Converts byte array to lowercase hex string.
     * 
     * @param bytes Input bytes
     * @return Hex string (2 chars per byte)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("PasswordCrackTask[id=%s, range=[%d, %d], combinations=%d]",
                             getTaskId(), startIndex, endIndex, endIndex - startIndex + 1);
    }
}

package com.hecaton.task.sum_range;

import com.hecaton.task.AbstractTask;
import com.hecaton.task.TaskResult;

/**
 * Example Task: Sum numbers in a range [start, end].
 * 
 * Example:
 *   SumRangeTask(1, 100) calculates 1+2+3+...+100 = 5050
 * 
 * Serializable to support transmission via RMI.
 */
public class SumRangeTask extends AbstractTask { 
    private static final long serialVersionUID = 1L;
    
    private final int start;
    private final int end;
    /**
     * Creates a task to sum numbers in a range.
     * 
     * @param jobId parent job ID
     * @param taskId unique task ID
     * @param start start of range (inclusive)
     * @param end end of range (inclusive)
     */
    public SumRangeTask(String jobId, int start, int end) {
        super(jobId);  // Call AbstractTask constructor
        this.start = start;
        this.end = end;
    }
    
    @Override
    public TaskResult execute() {
        long sum = 0;
        for (int i = start; i <= end; i++) {
            sum += i;
        }
        
        System.out.println("  [EXEC] " + getTaskId() + " summing [" + start + "-" + end + "] = " + sum);
        
        // Return PARTIAL result (contribution to final sum, non-terminal)
        return TaskResult.partial(
            this.getJobId(),
            this.getTaskId(),
            sum,
            0
        );
    }
        // (this.getJobId(), this.getTaskId(), TaskResult.Status.SUCCESS, sum, null, 0);
    
    // Getters for serialization
    public int getStart() { 
        return start; 
    }
    
    public int getEnd() { 
        return end; 
    }
}

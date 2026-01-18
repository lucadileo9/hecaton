package com.hecaton.task.examples;

import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;

/**
 * Example Task: Sum numbers in a range [start, end].
 * 
 * Example:
 *   SumRangeTask(1, 100) calculates 1+2+3+...+100 = 5050
 * 
 * Serializable to support transmission via RMI.
 */
public class SumRangeTask implements Task  { 
    private static final long serialVersionUID = 1L;
    
    private final int start;
    private final int end;

    private String taskId;
    private String jobId;

    @Override
    public String getTaskId() {
        return "task-" + taskId;
    }

    @Override
    public String getJobId() {
        return jobId;
    }


    
    /**
     * Creates a task to sum numbers in a range.
     * 
     * @param jobId parent job ID
     * @param taskId unique task ID
     * @param start start of range (inclusive)
     * @param end end of range (inclusive)
     */
    public SumRangeTask(String jobId, String taskId, int start, int end) {
        this.start = start;
        this.end = end;
        this.jobId = jobId;
        this.taskId = taskId;
    }
    
    @Override
    public TaskResult execute() {
        long sum = 0;
        for (int i = start; i <= end; i++) {
            sum += i;
        }
        
        System.out.println("  [EXEC] " + getTaskId() + " summing [" + start + "-" + end + "] = " + sum);
        
        return TaskResult.success(
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

package com.hecaton.task;

import java.io.Serializable;

/**
 * The result aggregated from all tasks of a Job.
 * Created by {@code Job.aggregateResults()} after all tasks are completed.
 */
public final class JobResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * As for the task {@code TaskResult.Status} there is {@code JobResult.Status}.
     * 4 possible states:
     * - success, job completed with result
     * - notFound, job completed but no result (e.g., password not in range
     * - failure, job failed with error
     * - cancelled, job was cancelled by user (probably this will not happen often)
     */
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILURE,
        CANCELLED
    }
    
    private final String jobId; // this could be use for manage multiple jobs
    private final Status status;
    private final Object data; // e.g., cracked password, it is Object so that it can be flexible
    private final String errorMessage;
    private final long executionTimeMs; // total execution time of the job, summing all tasks
    private final int totalTasks;
    private final int completedTasks;
    private final int failedTasks;
    
    /**
     * Private constructor - use factory methods.
     */
    private JobResult(String jobId, Status status, Object data, String errorMessage,
                     long executionTimeMs, int totalTasks, int completedTasks, int failedTasks) {
        this.jobId = jobId;
        this.status = status;
        this.data = data;
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
        this.totalTasks = totalTasks;
        this.completedTasks = completedTasks;
        this.failedTasks = failedTasks;
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Creates a success result.
     * 
     * @param jobId ID of the job
     * @param data result (e.g., cracked password)
     * @param executionTimeMs total execution time
     * @return JobResult with status SUCCESS
     */
    public static JobResult success(String jobId, Object data, long executionTimeMs) {
        return new JobResult(jobId, Status.SUCCESS, data, null, executionTimeMs, 0, 0, 0);
    }
    
    /**
     * Creates a success result with task statistics.
     * 
     * @param jobId ID of the job
     * @param data result
     * @param executionTimeMs total execution time
     * @param totalTasks total number of tasks
     * @param completedTasks completed tasks
     * @param failedTasks failed tasks
     * @return JobResult with status SUCCESS
     */
    public static JobResult success(String jobId, Object data, long executionTimeMs,
                                   int totalTasks, int completedTasks, int failedTasks) {
        return new JobResult(jobId, Status.SUCCESS, data, null, executionTimeMs,
                            totalTasks, completedTasks, failedTasks);
    }
    
    /**
     * Creates a "not found" result.
     * 
     * @param jobId ID of the job
     * @param executionTimeMs total execution time
     * @return JobResult with status NOT_FOUND
     */
    public static JobResult notFound(String jobId, long executionTimeMs) {
        return new JobResult(jobId, Status.NOT_FOUND, null, "Result not found in search space",
                            executionTimeMs, 0, 0, 0);
    }
    
    /**
     * Creates a "not found" result with statistics.
     */
    public static JobResult notFound(String jobId, long executionTimeMs,
                                    int totalTasks, int completedTasks, int failedTasks) {
        return new JobResult(jobId, Status.NOT_FOUND, null, "Result not found in search space",
                            executionTimeMs, totalTasks, completedTasks, failedTasks);
    }
    
    /**
     * Creates a failure result.
     * 
     * @param jobId ID of the job
     * @param errorMessage error message
     * @param executionTimeMs total execution time
     * @return JobResult with status FAILURE
     */
    public static JobResult failure(String jobId, String errorMessage, long executionTimeMs) {
        return new JobResult(jobId, Status.FAILURE, null, errorMessage, executionTimeMs, 0, 0, 0);
    }
    
    /**
     * Creates a cancelled result.
     * 
     * @param jobId ID of the job
     * @return JobResult with status CANCELLED
     */
    public static JobResult cancelled(String jobId) {
        return new JobResult(jobId, Status.CANCELLED, null, "Job cancelled by user", 0, 0, 0, 0);
    }
    
    // ==================== Getters ====================
    
    public String getJobId() {
        return jobId;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public Object getData() {
        return data;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public int getTotalTasks() {
        return totalTasks;
    }
    
    public int getCompletedTasks() {
        return completedTasks;
    }
    
    public int getFailedTasks() {
        return failedTasks;
    }
    
    // ==================== Convenience Methods ====================
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean hasResult() {
        return status == Status.SUCCESS && data != null;
    }
    
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    /**
     * @return formatted execution time (e.g., "2m 30s")
     */
    public String getFormattedExecutionTime() {
        long seconds = executionTimeMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    // ==================== Utility ====================
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("JobResult[").append(jobId).append(": ").append(status);
        
        if (data != null) {
            sb.append(", data=").append(data);
        }
        if (errorMessage != null && status == Status.FAILURE) {
            sb.append(", error=").append(errorMessage);
        }
        
        sb.append(", time=").append(getFormattedExecutionTime());
        
        if (totalTasks > 0) {
            sb.append(", tasks=").append(completedTasks).append("/").append(totalTasks);
            if (failedTasks > 0) {
                sb.append(" (").append(failedTasks).append(" failed)");
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
}

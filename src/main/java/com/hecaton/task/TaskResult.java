package com.hecaton.task;

import java.io.Serializable;

/**
 * This class represent the result of a Task execution.
 * 
 * 4 possible states:
 * - success, task completed with result
 * - notFound, task completed but no result (e.g., password not in range)
 * - failure, task failed with error
 * - cancelled, task was cancelled (early termination)
 * 
 * Created by {@code TaskExecutor} after task completion.
 */
public final class TaskResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Enum to represent the status of a TaskResult.
     */
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILURE,
        CANCELLED
    }
    
    private final String taskId;
    private final Status status;
    private final Object data;
    private final String errorMessage;
    private final long executionTimeMs;
    
    /**
     * Private constructor - use factory methods.
     */
    private TaskResult(String taskId, Status status, Object data, 
                      String errorMessage, long executionTimeMs) {
        this.taskId = taskId;
        this.status = status;
        this.data = data;
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Creates a success result with data.
     * 
     * @param taskId ID of the task
     * @param data result found (e.g., cracked password)
     * @return TaskResult with status SUCCESS
     */
    public static TaskResult success(String taskId, Object data) {
        return new TaskResult(taskId, Status.SUCCESS, data, null, 0);
    }
    
    /**
     * Creates a success result with data and execution time.
     * It could be useful for performance metrics, but I don't think I'll use it.
     * 
     * @param taskId ID of the task
     * @param data result found
     * @param executionTimeMs execution time in milliseconds
     * @return TaskResult with status SUCCESS
     */
    public static TaskResult success(String taskId, Object data, long executionTimeMs) {
        return new TaskResult(taskId, Status.SUCCESS, data, null, executionTimeMs);
    }
    
    /**
     * Creates a "not found" result (task completed, no match).
     * 
     * @param taskId ID of the task
     * @return TaskResult with status NOT_FOUND
     */
    public static TaskResult notFound(String taskId) {
        return new TaskResult(taskId, Status.NOT_FOUND, null, null, 0);
    }
    
    /**
     * Creates a "not found" result with execution time.
     * 
     * @param taskId ID of the task
     * @param executionTimeMs execution time in milliseconds
     * @return TaskResult with status NOT_FOUND
     */
    public static TaskResult notFound(String taskId, long executionTimeMs) {
        return new TaskResult(taskId, Status.NOT_FOUND, null, null, executionTimeMs);
    }
    
    /**
     * Creates a failure result.
     * 
     * @param taskId ID of the task
     * @param errorMessage error message
     * @return TaskResult with status FAILURE
     */
    public static TaskResult failure(String taskId, String errorMessage) {
        return new TaskResult(taskId, Status.FAILURE, null, errorMessage, 0);
    }
    
    /**
     * Creates a cancelled result (early termination).
     * A result task like this will be created when the leader decides to cancel ongoing tasks because
     * a result has already been found by another worker.
     * 
     * @param taskId ID of the task
     * @return TaskResult with status CANCELLED
     */
    public static TaskResult cancelled(String taskId) {
        return new TaskResult(taskId, Status.CANCELLED, null, "Task cancelled", 0);
    }
    
    // ==================== Getters ====================
    
    public String getTaskId() {
        return taskId;
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
    
    // ==================== Convenience Methods ====================
    
    /**
     * @return true if the task completed successfully (SUCCESS or NOT_FOUND)
     * N.B.: NOT_FOUND is considered a successful completion, as the task executed without errors.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.NOT_FOUND;
    }
    
    /**
     * @return true if a result was found (SUCCESS with data != null)
     */
    public boolean hasResult() {
        return status == Status.SUCCESS && data != null;
    }
    
    /**
     * @return true if the task failed
     */
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    /**
     * @return true if the task was cancelled
     */
    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }
    
    // ==================== Utility ====================
    
    @Override
    public String toString() {
        switch (status) {
            case SUCCESS:
                return String.format("TaskResult[%s: SUCCESS, data=%s, time=%dms]", 
                    taskId, data, executionTimeMs);
            case NOT_FOUND:
                return String.format("TaskResult[%s: NOT_FOUND, time=%dms]", 
                    taskId, executionTimeMs);
            case FAILURE:
                return String.format("TaskResult[%s: FAILURE, error=%s]", 
                    taskId, errorMessage);
            case CANCELLED:
                return String.format("TaskResult[%s: CANCELLED]", taskId);
            default:
                return String.format("TaskResult[%s: %s]", taskId, status);
        }
    }
}
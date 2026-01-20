package com.hecaton.task;

import java.util.UUID;

import com.hecaton.node.ExecutionContext;

/**
 * Abstract base class for Task implementations.
 * Provides default implementation for common Task interface methods.
 * 
 * Subclasses only need to implement:
 *   - execute()
 * 
 * Optional overrides:
 *   - execute(ExecutionContext) [default: calls execute()]
 *   - onCancel() [default: no-op]
 *   - getEstimatedComplexity() [default: 1]
 *   - getTargetWorkerId() [default: null]
 */
public abstract class AbstractTask implements Task {
    
    private static final long serialVersionUID = 1L;
    
    private final String jobId;
    private final String taskId;
    
    /**
     * Creates a new task with auto-generated UUID.
     * 
     * @param jobId ID of the parent job
     */
    protected AbstractTask(String jobId) {
        this.jobId = jobId;
        this.taskId = UUID.randomUUID().toString();
    }
    
    /**
     * Creates a new task with explicit taskId.
     * 
     * @param jobId ID of the parent job
     * @param taskId Unique task ID (e.g., "task-0", "task-1")
     */
    protected AbstractTask(String jobId, String taskId) {
        this.jobId = jobId;
        this.taskId = taskId;
    }
    
    @Override
    public String getJobId() {
        return jobId;
    }
    
    @Override
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public TaskResult execute(ExecutionContext context) {
        // Default: ignore context, call simple execute()
        return execute();
    }
    
    @Override
    public void onCancel() {
        // Default: no cleanup needed
    }
    
    @Override
    public int getEstimatedComplexity() {
        return 1;  // Default: all tasks have same complexity
    }
    
    @Override
    public String getTargetWorkerId() {
        return null;  // Default: no worker preference
    }
}

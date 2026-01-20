package com.hecaton.task;

import com.hecaton.node.ExecutionContext;
import java.io.Serializable;

/**
 * Interface that represents a unit of work to be executed by a worker node.
 * 
 * A Task is:
 *  - Created by the Job via {@code Job.split()}
 *  - Serializable for RMI transfer to the worker
 *  - Executed by the TaskExecutor on the worker
 *  - Returns a TaskResult upon completion
 * 
 */
public interface Task extends Serializable {
    
    /**
     * @return Id unique (e.g., "job-123-task-0")
     */
    String getTaskId();
    
    /**
     * @return ID of the job to which this task belongs
     * This could be useful when there will be multiple jobs managed simultaneously.
     */
    String getJobId();
    
    /**
     * Executes the task without execution context information.
     * Fallback method for tasks that do not require ExecutionContext.
     * 
     * @return execution result
     */
    TaskResult execute();
    
    /**
     * Executes the task with execution context information.
     * 
     * The context provides:
     *   - ID and address of the worker
     *   - Hardware capabilities (CPU, memory)
     *   - Hints for local parallelism
     * 
     * Default implementation calls {@link #execute()} for backward compatibility.
     * 
     * @param context execution context of the worker
     * @return execution result
     */
    default TaskResult execute(ExecutionContext context) {
        return execute();
    }
    
    /**
     * Called when the task is cancelled (early termination).
     * Override for custom cleanup (e.g., closing resources).
     * 
     * Default implementation does nothing.
     */
    default void onCancel() {
        // Default: no cleanup needed
    }
    
    /**
     * @return estimated relative complexity of this task (optional)
     */
    default int getEstimatedComplexity() {
        return 1;  // Default: all tasks have the same complexity
    }
    
    /**
     * @return ID of the suggested target worker (optional, for TargetedAssignment)
     */
    default String getTargetWorkerId() {
        return null;  // Default: no preference
    }
}

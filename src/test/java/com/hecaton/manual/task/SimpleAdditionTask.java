package com.hecaton.manual.task;

import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplest possible task: adds two numbers.
 * Used for testing basic task execution without complexity.
 */
public class SimpleAdditionTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(SimpleAdditionTask.class);
    
    private final String jobId;
    private final String taskId;
    private final int number1;
    private final int number2;
    
    public SimpleAdditionTask(String jobId, String taskId, int number1, int number2) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.number1 = number1;
        this.number2 = number2;
    }
    
    @Override
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public String getJobId() {
        return jobId;
    }
    
    @Override
    public TaskResult execute() {
        log.info("Executing task {}: {} + {} = ?", taskId, number1, number2);
        
        try {
            // Simulate some work
            Thread.sleep(100);
            
            int result = number1 + number2;
            log.info("Task {} completed: {} + {} = {}", taskId, number1, number2, result);
            
            return TaskResult.success(jobId, taskId, String.valueOf(result));
            
        } catch (InterruptedException e) {
            log.error("Task {} interrupted", taskId, e);
            Thread.currentThread().interrupt();
            return TaskResult.failure(jobId, taskId, "Task interrupted");
        } catch (Exception e) {
            log.error("Task {} failed", taskId, e);
            return TaskResult.failure(jobId, taskId, e.getMessage());
        }
    }
    
    @Override
    public String toString() {
        return String.format("SimpleAdditionTask[%s: %d + %d]", taskId, number1, number2);
    }
}

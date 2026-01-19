package com.hecaton.task.sum_range;

import com.hecaton.task.AbstractJob;
import com.hecaton.task.JobResult;
import com.hecaton.task.Task;
import com.hecaton.task.TaskResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Example Job: Sum all numbers from 1 to N.
 * 
 * Example:
 *   Job(100) splits into 4 tasks:
 *   - Task 0: sum(1-25) = 325
 *   - Task 1: sum(26-50) = 950
 *   - Task 2: sum(51-75) = 1575
 *   - Task 3: sum(76-100) = 2200
 *   Result: 325 + 950 + 1575 + 2200 = 5050
 * 
 * Used for testing the entire distributed execution pipeline.
 */
public class SumRangeJob extends AbstractJob {
    private final int maxNumber;

    /**
     * Creates a job to sum numbers from 1 to maxNumber.
     * 
     * @param maxNumber upper bound of the range (inclusive)
     */
    public SumRangeJob(int maxNumber) {
        this.setJobId();
        this.maxNumber = maxNumber;
    }
    
    @Override
    public List<Task> split(int numTasks) {
        List<Task> tasks = new ArrayList<>();
        int rangeSize = maxNumber / numTasks;
        
        for (int i = 0; i < numTasks; i++) {
            int start = (i * rangeSize) + 1;
            int end = (i == numTasks - 1) ? maxNumber : (i + 1) * rangeSize;
            
            tasks.add(new SumRangeTask(
                getJobId(),
                start,
                end
            ));
        }
        
        return tasks;
    }
    
    @Override
    public JobResult aggregateResults(List<TaskResult> results) {
        long sum = 0;
        for (TaskResult result : results) {
            if (result.getStatus() != TaskResult.Status.SUCCESS) {
                return JobResult.failure(this.getJobId(), "Something didn't work", 0);
            }
            sum += (Long) result.getData();
        }
        return JobResult.success(getJobId(), sum, 0, results.size(), results.size(), 0);
    }
}

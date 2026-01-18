package com.hecaton.task;

import java.io.Serializable;
import java.util.List;

/**
 * Interface that represents a distributed Job to be executed.
 * 
 * A Job is responsible for:
 *   - Defining the work to be done
 *   - Splitting itself into Tasks via {@link #split(int)}
 *   - Aggregating the results of the tasks into a final result
 * 
 * Lifecycle:
 *   1. User creates Job with parameters
 *   2. JobManager assigns jobId
 *   3. SplittingStrategy calls {@code job.split(N)}
 *   4. Tasks executed on workers
 *   5. JobManager calls {@code job.aggregateResults()}
 *   6. JobResult returned to user
 * 
 */
public interface Job extends Serializable {
    
    /**
     * @return ID of the job, unique (e.g., "job-123"), assigned by JobManager upon submission
     */
    String getJobId();
    
    /**
     * Sets the job ID. Called by JobManager after submission.
     * 
     * @param jobId unique assigned ID of the job
     */
    void setJobId();
    
    /**
     * Divides this job into a specified number of tasks.
     * 
     * Note: the actual number of tasks may differ from {@code numTasks}
     * if the job cannot be divided exactly (e.g., keyspace too small).
     * 
     * @param numTasks desired number of tasks
     * @return list of created tasks
     * @throws IllegalArgumentException if numTasks <= 0
     */
    List<Task> split(int numTasks);
    
    /**
     * Aggregates the results of all tasks into a final result.
     * 
     * Called when:
     *   - All tasks have completed normally
     *   - Early termination (result found)
     *   - Job cancelled (partial results)
     * 
     * @param results list of all received TaskResults
     * @return aggregated result of the job
     */
    JobResult aggregateResults(List<TaskResult> results);
    
    /**
     * @return descriptive name of the job type (for logging)
     */
    default String getJobType() {
        return getClass().getSimpleName();
    }
    
    /**
     * @return true if supports early termination
     */
    default boolean supportsEarlyTermination() {
        return true;  // Default: yes (e.g., search jobs)
    }
    
    /**
     * Estimates execution time in milliseconds (optional).
     * Useful for scheduling and timeouts.
     * 
     * @param workerCount number of available workers
     * @return estimated time in ms, or -1 if not estimable
     */
    default long estimateExecutionTime(int workerCount) {
        return -1;  // Default: not estimable
    }
    
    /**
     * Callback called when the job starts execution.
     * Override for custom initializations.
     */
    default void onStart() {
        // Default: no-op
    }
    
    /**
     * Callback called when the job is completed (success or failure).
     * Override for custom cleanup.
     * 
     * @param result final result of the job
     */
    default void onComplete(JobResult result) {
        // Default: no-op
    }
}

// N.B.: there are a lot of method interesting and that could be useful, but I'm not sure if
// they are really necessary. So future Luca rewatch all the methods and decide which keep and which remove.

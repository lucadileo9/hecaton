package com.hecaton.task;

/**
 * Abstract base class for Job implementations.
 * Provides default implementation for common Job interface methods.
 * 
 * Subclasses only need to implement:
 *   - split(int numTasks)
 *   - aggregateResults(List<TaskResult> results)
 * 
 * Optional overrides:
 *   - supportsEarlyTermination() [default: true]
 *   - onStart() [default: no-op]
 *   - onComplete(JobResult) [default: no-op]
 *   - estimateExecutionTime(int workerCount) [default: -1]
 */
public abstract class AbstractJob implements Job {
    
    private static final long serialVersionUID = 1L;
    
    private String jobId;
    
    public AbstractJob() {
        setJobId();
    }

    @Override
    public String getJobId() {
        return jobId;
    }
    
    @Override
    public void setJobId() {
        this.jobId = "job-" + System.currentTimeMillis();
    }
    
    @Override
    public String getJobType() {
        return getClass().getSimpleName();
    }
    
    @Override
    public boolean supportsEarlyTermination() {
        return true;  // Default: most jobs support early termination
    }
    
    @Override
    public void onStart() {
        // Default: no action needed
    }
    
    @Override
    public void onComplete(JobResult result) {
        // Default: no action needed
    }
    
    @Override
    public long estimateExecutionTime(int workerCount) {
        return -1;  // Default: unknown
    }
}

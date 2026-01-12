package com.hecaton.node;

import java.io.Serializable;

/**
 * This class represents the execution context of a task running on a compute node.
 * It includes identity information (worker ID, host, port, leadership status)
 * and the hardware capabilities of the node.
 * 
 * It is used by the TaskExecutor to parallelize work according to the node's resources.
 * 
 * Created by {@code NodeImpl.createExecutionContext()} at the time
 * of task execution.
 */
public final class ExecutionContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String workerId;
    private final int port;
    private final boolean isLeader;
    private final NodeCapabilities capabilities;
    
    /**
     * Creates a new ExecutionContext.
     * 
     * @param workerId unique ID of the worker
     * @param port RMI port of the worker
     * @param isLeader true if this node is the leader
     * @param capabilities hardware capabilities of the node
     */
    public ExecutionContext(String workerId, int port, 
                           boolean isLeader, NodeCapabilities capabilities) {
        this.workerId = workerId;
        this.port = port;
        this.isLeader = isLeader;
        this.capabilities = capabilities;
    }
    
    // ==================== Getters Identity ====================
    
    /**
     * @return unique ID of the worker executing the task
     */
    public String getWorkerId() {
        return workerId;
    }
    
    
    /**
     * @return RMI port of the worker
     */
    public int getPort() {
        return port;
    }
    
    /**
     * @return true if this node is also the leader of the cluster
     */
    public boolean isLeader() {
        return isLeader;
    }
    
    // ==================== Getters Capabilities ====================
    
    /**
     * @return Complete hardware capabilities of the node
     */
    public NodeCapabilities getCapabilities() {
        return capabilities;
    }
    
    /**
     * Shortcut to get the number of CPU cores.
     * Equivalent to {@code getCapabilities().getCpuCores()}.
     * 
     * @return number of CPU cores
     */
    public int getCpuCores() {
        return capabilities.getCpuCores();
    }
    
    /**
     * Shortcut to get the total memory.
     * Equivalent to {@code getCapabilities().getTotalMemoryMB()}.
     * 
     * @return total memory in MB
     */
    public long getTotalMemoryMB() {
        return capabilities.getTotalMemoryMB();
    }
    
    // ==================== Utility ====================
    
    @Override
    public String toString() {
        return String.format("ExecutionContext[worker=%s, %s:%d, leader=%s, %s]",
            workerId, port, isLeader, capabilities);
    }
    
    /**
     * Suggests the number of threads to use for local parallelism.
     * Leaves 1 core free for the system if possible.
     * 
     * @return recommended number of threads (minimum 1)
     */
    public int suggestedThreadCount() {
        return Math.max(1, capabilities.getCpuCores() - 1);
    }
}

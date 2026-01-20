package com.hecaton.rmi;

import com.hecaton.discovery.NodeInfo;
import com.hecaton.task.TaskResult;
import com.hecaton.task.Job;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface exposed ONLY by the Leader node.
 * Manages cluster coordination.
 */
public interface LeaderService extends Remote {
    /**
     * Called by a new node to join the cluster.
     * @param node Remote reference to the new node
     * @throws RemoteException if RMI communication fails
     */
    void registerNode(NodeService node) throws RemoteException;
    
    /**
     * Worker reports task completion.
     * @param taskId ID of the completed task
     * @param result Result (can be null if failed)
     * @throws RemoteException if RMI communication fails
     * @deprecated Use {@link #submitResult(TaskResult)} instead for type-safe result reporting
     */
    @Deprecated
    void reportTaskCompletion(String taskId, Object result) throws RemoteException;
    
    /**
     * Worker submits task result after completion.
     * Internally handled by TaskScheduler.
     * 
     * This method replaces the deprecated reportTaskCompletion() with a type-safe approach
     * using TaskResult objects that include status, data, and error information.
     * 
     * @param result the completed task result (SUCCESS, NOT_FOUND, FAILURE, or CANCELLED)
     * @throws RemoteException if RMI communication fails
     */
    void submitResults(List<TaskResult> results) throws RemoteException;
    
    /**
     * Returns the list of all nodes in the cluster.
     * Used by Workers to obtain cluster membership information for election algorithm.
     * Workers cache this list locally to continue election even if Leader dies.
     * 
     * @return List of NodeInfo objects containing metadata for each node
     * @throws RemoteException if RMI communication fails or node is not Leader
     */
    List<NodeInfo> getClusterNodes() throws RemoteException;
    
    /**
     * Heartbeat ping from worker to signal it's alive.
     * Workers call this periodically to report their health status.
     * Internally handled by FailureDetector on the Leader.
     * 
     * @param workerId unique ID of the worker sending the ping
     * @throws RemoteException if RMI communication fails
     */
    void ping(String workerId) throws RemoteException;

    /**
     * Returns the current size of the cluster (number of nodes).
     * @return Number of nodes in the cluster
     * @throws RemoteException if RMI communication fails
     */
    int getClusterSize() throws RemoteException;
}

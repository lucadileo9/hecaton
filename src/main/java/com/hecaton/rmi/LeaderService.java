package com.hecaton.rmi;

import com.hecaton.discovery.NodeInfo;

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
     */
    void reportTaskCompletion(String taskId, Object result) throws RemoteException;
    
    /**
     * Returns the list of all nodes in the cluster.
     * Used by Workers to obtain cluster membership information for election algorithm.
     * Workers cache this list locally to continue election even if Leader dies.
     * 
     * @return List of NodeInfo objects containing metadata for each node
     * @throws RemoteException if RMI communication fails or node is not Leader
     */
    List<NodeInfo> getClusterNodes() throws RemoteException;
}

package com.hecaton.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

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
     * Election request from a candidate node.
     * @param candidateId ID of the node that wants to become Leader
     * @return true if accepts the candidacy
     * @throws RemoteException if RMI communication fails
     */
    boolean requestElection(String candidateId) throws RemoteException;
}

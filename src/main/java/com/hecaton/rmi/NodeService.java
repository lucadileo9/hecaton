package com.hecaton.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import com.hecaton.node.NodeCapabilities;
import com.hecaton.task.Task;

/**
 * Remote interface exposed by each node.
 * MUST extend Remote to be usable via RMI.
 * Every method MUST declare RemoteException.
 */
public interface NodeService extends Remote {
    /**
     * Heartbeat ping to verify that the node is alive.
     * @return true if the node responds
     * @throws RemoteException if RMI communication fails
     */
    boolean ping() throws RemoteException;
    
    /**
     * Returns the unique node ID.
     * @return Unique node ID (e.g. "node-192.168.1.10-5001-1735315200000")
     * @throws RemoteException if RMI communication fails
     */
    String getId() throws RemoteException;
    
    /**
     * Returns the current node status.
     * @return Current status: "IDLE", "WORKING", "LEADER"
     * @throws RemoteException if RMI communication fails
     */
    String getStatus() throws RemoteException;
        
    /**
     * Returns the numeric election ID used for comparison during Leader election.
     * This is the timestamp value (nodeIdValue) used by the Election algorithm.
     * @return Timestamp of node creation (used for election comparison)
     * @throws RemoteException if RMI communication fails
     */
    long getElectionId() throws RemoteException;
    
    /**
     * Receives an ELECTION message from a candidate node.
     * If this node has a higher election ID, it will start its own election.
     * 
     * N.B.: Id is different from electionId. Id is string, electionId is numeric (timestamp).
     * 
     * @param candidateId String ID of the candidate node (for logging)
     * @param candidateElectionId Numeric ID for comparison (timestamp)
     * @throws RemoteException if RMI communication fails
     */
    void receiveElectionMessage(String candidateId, long candidateElectionId) 
        throws RemoteException;
    
    /**
     * Receives COORDINATOR announcement from the new Leader.
     * This node will reconnect to the new Leader and resume normal operation.
     * 
     * @param newLeaderId ID of the new Leader
     * @param leaderHost Hostname of the new Leader
     * @param leaderPort RMI registry port of the new Leader
     * @throws RemoteException if RMI communication fails
     */
    void receiveCoordinatorMessage(String newLeaderId, String leaderHost, int leaderPort) 
        throws RemoteException;

    /**
     * Returns the hardware capabilities of this node.
     * @return NodeCapabilities object describing CPU, RAM, Disk
     * @throws RemoteException if RMI communication fails
     * 
     * N.B.: This method return a sort of run-time snapshot of the node capabilities, that
     * are subsequently stored in the ExecutionContext at the time of task execution.
     * 
     */
    NodeCapabilities getCapabilities() throws RemoteException;
    
    /**
     * Receives a batch of tasks from the Leader and executes them.
     * Internally handled by TaskExecutor (thread pool execution).
     * 
     * Workers execute tasks asynchronously and call leader.submitResult() for each completion.
     * This method returns immediately after queuing tasks; actual execution happens in background.
     * 
     * @param tasks list of tasks to execute
     * @throws RemoteException if RMI communication fails
     */
    void executeTasks(List<Task> tasks) throws RemoteException;
}

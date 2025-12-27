package com.hecaton.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

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
}

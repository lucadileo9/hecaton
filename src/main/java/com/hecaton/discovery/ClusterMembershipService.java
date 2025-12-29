package com.hecaton.discovery;

import com.hecaton.rmi.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages cluster membership registry (list of active nodes).
 * Thread-safe with CopyOnWriteArrayList for concurrent access during heartbeat monitoring.
 * 
 * NOTE: This service manages the MEMBERSHIP LIST (which nodes are in the cluster).
 * For automatic DISCOVERY (finding the Leader), see UdpDiscoveryService.
 */
public class ClusterMembershipService {
    private static final Logger log = LoggerFactory.getLogger(ClusterMembershipService.class);
    
    // CopyOnWriteArrayList: safe for concurrent reads (heartbeat pings)
    // Writes create a copy, so performance trade-off acceptable for small clusters
    private final List<NodeService> activeNodes = new CopyOnWriteArrayList<>();
    
    /**
     * Adds a new node to the cluster.
     * Checks for duplicates by node ID before adding.
     * 
     * @param node The node to add to the cluster
     * @throws RemoteException if unable to retrieve node ID
     */
    public void addNode(NodeService node) throws RemoteException {
        String nodeId = node.getId();
        
        // Verify no duplicates
        for (NodeService existing : activeNodes) {
            if (existing.getId().equals(nodeId)) {
                log.warn("Node {} already registered, skipping", nodeId);
                return;
            }
        }
        
        activeNodes.add(node);
        log.info("Node {} joined cluster. Total nodes: {}", nodeId, activeNodes.size());
    }
    
    /**
     * Removes a dead node from the cluster.
     * 
     * @param node The node to remove
     */
    public void removeNode(NodeService node) {
        boolean removed = activeNodes.remove(node);
        
        if (removed) {
            String nodeId = getNodeIdSafe(node);
            log.info("Node {} removed from cluster. Remaining: {}", nodeId, activeNodes.size());
        } else {
            log.warn("Attempted to remove non-existent node");
        }
    }
    
    /**
     * Removes a node by its ID.
     * Useful when you only have the ID string and not the NodeService reference.
     * 
     * @param nodeId The ID of the node to remove
     * @return true if a node was removed, false otherwise
     */
    public boolean removeNodeById(String nodeId) {
        NodeService toRemove = null;
        
        for (NodeService node : activeNodes) {
            try {
                if (node.getId().equals(nodeId)) {
                    toRemove = node;
                    break;
                }
            } catch (RemoteException e) {
                // Node already dead, might be the one we're looking for
                log.debug("Cannot contact node during removal check: {}", e.getMessage());
            }
        }
        
        if (toRemove != null) {
            activeNodes.remove(toRemove);
            log.info("Node {} removed from cluster. Remaining: {}", nodeId, activeNodes.size());
            return true;
        }
        
        return false;
    }
    
    /**
     * Returns a read-only copy of active nodes.
     * Safe to iterate without ConcurrentModificationException.
     * 
     * @return List of active nodes (copy)
     */
    public List<NodeService> getActiveNodes() {
        return new ArrayList<>(activeNodes);
    }
    
    /**
     * Returns the number of nodes in the cluster.
     * 
     * @return Cluster size including the Leader
     */
    public int getClusterSize() {
        return activeNodes.size();
    }
    
    /**
     * Checks if a node with the given ID exists in the cluster.
     * 
     * @param nodeId The node ID to check
     * @return true if node exists, false otherwise
     */
    public boolean hasNode(String nodeId) {
        for (NodeService node : activeNodes) {
            try {
                if (node.getId().equals(nodeId)) {
                    return true;
                }
            } catch (RemoteException e) {
                // Skip unreachable nodes
            }
        }
        return false;
    }
    
    /**
     * Gets node ID safely, returning "unknown" if RMI call fails.
     */
    private String getNodeIdSafe(NodeService node) {
        try {
            return node.getId();
        } catch (RemoteException e) {
            return "unknown";
        }
    }
}

package com.hecaton.election.bully;

import com.hecaton.discovery.NodeInfo;
import com.hecaton.election.ElectionStrategy;
import com.hecaton.node.NodeImpl;
import com.hecaton.rmi.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Bully Election Algorithm implementation.
 * 
 * Rule: The node with the HIGHEST election ID always becomes the Leader.
 * 
 * Algorithm flow:
 * 1. Node detects Leader is dead
 * 2. Sends ELECTION to all nodes with higher ID
 * 3. If no response → becomes Leader (broadcasts COORDINATOR)
 * 4. If someone responds → waits for COORDINATOR from that node
 * 
 * Complexity: O(n²) messages in worst case (not scalable for >100 nodes)
 * Suitable for: Small clusters (3-20 nodes), educational purposes (aka.: this project)
 */
public class BullyElection implements ElectionStrategy {
    private static final Logger log = LoggerFactory.getLogger(BullyElection.class);
    private static final int COORDINATOR_TIMEOUT_MS = 10000;  // 10 seconds to wait for COORDINATOR
    
    private final NodeImpl selfNode;          // Reference to the node running this election
    // I'm not sure we need both selfElectionId and selfNode.getElectionId(), but whatever --- REWATCH ME
    private final long selfElectionId;        // This node's election ID (timestamp)
    private final Supplier<List<NodeInfo>> clusterNodesSupplier; // Lazy supplier for fresh cluster cache
    
    private final CountDownLatch coordinatorLatch = new CountDownLatch(1);
    
    /**
     * Creates a new BullyElection instance.
     * 
     * @param selfNode Reference to the NodeImpl running this election
     * @param selfElectionId Numeric election ID (timestamp) for comparison
     * @param clusterNodesSupplier Supplier providing fresh cluster cache on demand
     */
    public BullyElection(NodeImpl selfNode, long selfElectionId, Supplier<List<NodeInfo>> clusterNodesSupplier) {
        this.selfNode = selfNode;
        this.selfElectionId = selfElectionId;
        this.clusterNodesSupplier = clusterNodesSupplier;
    }
    
    @Override
    public void startElection() {
        log.info("Starting Bully Election (my ID: {})", selfElectionId);
        
        try {
            // STEP 1: Get fresh cluster snapshot from supplier
            List<NodeInfo> clusterNodes = clusterNodesSupplier.get();
            
            // STEP 2: Find nodes with higher election ID
            List<NodeInfo> higherNodes = clusterNodes.stream()
                .filter(node -> node.getElectionId() > selfElectionId)
                .collect(Collectors.toList());
            if (higherNodes.isEmpty()) {
                // No node with higher ID → I WIN!
                log.info("No higher nodes found. I am the new Leader!");
                becomeLeader(); // Promote and announce, see below
                return;
            }
            
            // STEP 2: If there  are higher nodes,Send ELECTION to them
            log.info("Sending ELECTION to {} higher node(s)", higherNodes.size());
            boolean receivedOK = sendElectionMessages(higherNodes);
            
            if (!receivedOK) {
                // No one responded → I WIN!  (maybe they are all dead)
                log.info("No OK received from higher nodes. I am the new Leader!");
                becomeLeader();
            } else {
                // Someone responded → WAIT for COORDINATOR
                log.info("Waiting for COORDINATOR message from higher node...");
                waitForCoordinator();
                // N.B.: I'm waiting because someone responded (where I sent ELECTION), so 
                // I expect them to send COORDINATOR sooner or later
            }
            
        } catch (Exception e) {
            log.error("Election failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends ELECTION messages to all nodes with higher ID.
     * 
     * @param higherNodes List of nodes to contact
     * @return true if at least one node responded (didn't throw RemoteException)
     */
    private boolean sendElectionMessages(List<NodeInfo> higherNodes) {
        boolean anyResponse = false;
        
        for (NodeInfo node : higherNodes) {
            try {
                // RMI call to higher node
                Registry registry = LocateRegistry.getRegistry(node.getHost(), node.getPort());
                NodeService remoteNode = (NodeService) registry.lookup("node");
                
                // I'm calling receiveElectionMessage on the REMOTE node
                remoteNode.receiveElectionMessage(
                    selfNode.getId(),
                    selfElectionId
                );
                
                // If we get here, node responded (no RemoteException = implicit OK)
                anyResponse = true;
                log.debug("Node {} acknowledged ELECTION", node.getNodeId());
                
            } catch (RemoteException e) {
                // Node is dead or unreachable, ignore, and try next
                log.warn("Node {} unreachable: {}", node.getNodeId(), e.getMessage());
            } catch (Exception e) {
                log.error("Error contacting node {}: {}", node.getNodeId(), e.getMessage());
            }
        }
        // I'll return true if at least one node responded, false if none did
        return anyResponse;
    }
    
    /**
     * Promotes this node to Leader and announces victory.
     */
    private void becomeLeader() {
        try {
            // Promote to Leader (method in NodeImpl)
            selfNode.promoteToLeader();
            
            // Announce to all nodes
            announceCoordinator();
            
        } catch (RemoteException e) {
            log.error("Failed to become Leader: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Announces to all cluster nodes that this node is the new Leader.
     * Sends COORDINATOR message via RMI.
     */
    private void announceCoordinator() {
        log.info("Announcing myself as COORDINATOR to all nodes");
        List<NodeInfo> clusterNodes = clusterNodesSupplier.get(); // fresh cache
        for (NodeInfo node : clusterNodes) { //I'l send a COORDINATOR to everyone in the cluster
            if (node.getElectionId() == selfElectionId) {
                continue;  // Skip myself
            }
            
            try {
                Registry registry = LocateRegistry.getRegistry(node.getHost(), node.getPort());
                NodeService remoteNode = (NodeService) registry.lookup("node");
                
                // Extract host and port from selfNode ID
                String selfId = selfNode.getId();
                String[] parts = selfId.split("-");
                String host = parts.length > 1 ? parts[1] : "localhost";
                int port = parts.length > 2 ? Integer.parseInt(parts[2]) : 5001;
                
                remoteNode.receiveCoordinatorMessage(selfId, host, port);
                
                log.info("Notified node {}", node.getNodeId());
                
            } catch (Exception e) {
                log.warn("Failed to notify node {}: {}", node.getNodeId(), e.getMessage());
            }
        }
    }
    
    /**
     * Waits for COORDINATOR message from a node with higher ID.
     * If timeout expires without receiving message, restarts election.
     */
    private void waitForCoordinator() {
        try {
            boolean received = coordinatorLatch.await(COORDINATOR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (!received) {
                // Timeout! No COORDINATOR received → restart election
                log.warn("COORDINATOR timeout! Higher node may have crashed. Restarting election...");
                startElection();
            } else {
                log.info("COORDINATOR received successfully");
            }
            
        } catch (InterruptedException e) {
            log.error("Election interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Called by NodeImpl when COORDINATOR message is received.
     * Releases the latch that is blocking waitForCoordinator().
     */
    public void notifyCoordinatorReceived() {
        coordinatorLatch.countDown();
    }
}

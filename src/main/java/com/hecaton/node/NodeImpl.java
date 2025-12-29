package com.hecaton.node;

import com.hecaton.discovery.ClusterMembershipService;
import com.hecaton.discovery.LeaderDiscoveryStrategy;
import com.hecaton.discovery.NodeInfo;
import com.hecaton.discovery.UdpDiscoveryService;
import com.hecaton.monitor.HeartbeatMonitor;
import com.hecaton.rmi.NodeService;
import com.hecaton.rmi.LeaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Core implementation of a Hecaton node.
 * Implements both NodeService and LeaderService interfaces.
 * Can operate as either a Worker or Leader (State Pattern).
 */
public class NodeImpl implements NodeService, LeaderService {
    private static final Logger log = LoggerFactory.getLogger(NodeImpl.class);
    
    private final String nodeId; // Unique node ID
    private final long nodeIdValue;  // Timestamp for unique ID and election comparison
    private final int port;
    private boolean isLeader;
    private Registry myRegistry;  // Each node has its own RMI registry
    
    // Cluster membership registry (only for Leader)
    private ClusterMembershipService membershipService;
    
    // Leader discovery strategy
    private LeaderDiscoveryStrategy discoveryStrategy;

    // Heartbeat monitor (only for Workers monitoring Leader)
    private HeartbeatMonitor leaderMonitor;
    // Reference to Leader (for Workers)
    private NodeService leaderNode;
    
    /**
     * Creates a new node instance.
     * Each node creates its own RMI Registry on the specified port.
     * @param host Host address (e.g. "localhost" or "192.168.1.10")
     * @param port RMI registry port
     * @throws RemoteException if RMI export fails
     */
    public NodeImpl(String host, int port) throws RemoteException {
        // This prevents "Connection refused" errors when RMI auto-detects wrong IP on multi-NIC systems
        if (System.getProperty("java.rmi.server.hostname") == null) {
            System.setProperty("java.rmi.server.hostname", "localhost");
        }
        
        this.nodeIdValue = System.currentTimeMillis(); // Use current time as unique value
        this.nodeId = "node-" + host + "-" + port + "-" + nodeIdValue;
        this.port = port;
        this.isLeader = false;
        
        // Export this object for RMI (makes it remotely callable)
        UnicastRemoteObject.exportObject(this, 0);
        
        // Every node creates its own RMI Registry
        this.myRegistry = LocateRegistry.createRegistry(port);
        this.myRegistry.rebind("node", this);  // Register as "node"
        
        log.info("Node {} initialized on port {}", nodeId, port);
    }
    
    /**
     * Starts this node as the cluster Leader.
     * Uses the existing RMI Registry and additionally binds itself as "leader".
     * @throws RemoteException if binding fails
     */
    public void startAsLeader() throws RemoteException {
        this.isLeader = true;
        
        // Initialize cluster membership service
        this.membershipService = new ClusterMembershipService();
        
        // Register itself as first node
        membershipService.addNode(this);
        
        // Bind as "leader" in addition to "node" (registry already exists from constructor)
        myRegistry.rebind("leader", this);
        
        // Start UDP broadcaster for automatic discovery
        try {
            this.discoveryStrategy = new UdpDiscoveryService(this.port, this.nodeId);
            ((UdpDiscoveryService) discoveryStrategy).startBroadcaster();
            log.info("[OK] UDP broadcaster started on port 6789");
        } catch (Exception e) {
            log.warn("UDP broadcaster disabled (port conflict): {}", e.getMessage());
            log.warn("  Workers must use manual --join host:port");
            // Continue without UDP - manual join still works
        }
        
        log.info("[OK] Node {} started as LEADER on port {}", nodeId, port);
        log.info("[OK] Cluster size: {} node(s)", membershipService.getClusterSize());
    }
    
    /**
     * Joins an existing cluster by connecting to the Leader.
     * This method is used by Worker nodes, which must know the Leader's host and port in advance.
     * Note: This node already has its own RMI registry from the constructor.
     * @param leaderHost Leader's hostname
     * @param leaderPort Leader's RMI registry port
     * @throws Exception if connection or registration fails
     */
    public void joinCluster(String leaderHost, int leaderPort) throws Exception {
        // Locate Leader's RMI Registry (different from our own)
        Registry leaderRegistry = LocateRegistry.getRegistry(leaderHost, leaderPort);
        
        // Lookup Leader service in the Leader's registry
        LeaderService leader = (LeaderService) leaderRegistry.lookup("leader");
        this.leaderNode = (NodeService) leader;  // Store reference for heartbeat
        
        // Register with Leader (Leader will store reference to our "node" binding)
        leader.registerNode(this);
        
        log.info("[OK] Node {} joined cluster via {}:{}", nodeId, leaderHost, leaderPort);
        
        // Start monitoring Leader's health
        leaderMonitor = new HeartbeatMonitor(leaderNode, this::onLeaderDied, "Leader Monitor");
        leaderMonitor.start();
        log.info("[OK] Heartbeat monitoring started for Leader");
    }
    
    /**
     * Automatically discovers and joins the cluster using the configured discovery strategy.
     * Uses UDP broadcast by default to find the Leader without manual configuration.
     * Falls back to throwing exception if discovery fails.
     * 
     * @throws Exception if Leader not found or connection fails
     */
    public void autoJoinCluster() throws Exception {
        log.info("Starting automatic Leader discovery...");
        
        // Use UDP discovery strategy by default
        this.discoveryStrategy = new UdpDiscoveryService();
        
        // Discover Leader (blocks until found or timeout)
        NodeInfo leader = discoveryStrategy.discoverLeader(5000);
        
        if (leader == null) {
            throw new Exception("No Leader found via UDP discovery (timeout 5000ms). " +
                              "Possible causes: No Leader running, firewall blocking UDP, " +
                              "not on same network. Try manual join: --join host:port");
        }
        
        log.info("[OK] Discovered Leader: {} at {}:{}", 
                 leader.getNodeId(), leader.getHost(), leader.getPort());
        
        // Use existing join method to complete connection
        joinCluster(leader.getHost(), leader.getPort());
    }
    
    // ==================== NodeService Implementation ====================
    
    @Override
    public boolean ping() throws RemoteException {
        log.debug("Ping received");
        return true;
    }
    
    @Override
    public String getId() throws RemoteException {
        return nodeId;
    }
    
    @Override
    public String getStatus() throws RemoteException {
        return isLeader ? "LEADER" : "WORKER";
    }
    
    // ==================== LeaderService Implementation ====================
    
    @Override
    public void registerNode(NodeService node) throws RemoteException {
        if (!isLeader) {
            throw new RemoteException("This node is not the leader");
        }
        
        // Delegate to membership service (handles duplicate check internally)
        membershipService.addNode(node);
        
        String newNodeId = node.getId();
        log.info("[OK] New node registered: {} (Total: {} nodes)", newNodeId, membershipService.getClusterSize());
    }
    
    @Override
    public void reportTaskCompletion(String taskId, Object result) throws RemoteException {
        log.info("Task {} completed with result: {}", taskId, result);
        // TODO
    }
    
    @Override
    public boolean requestElection(String candidateId) throws RemoteException {
        log.info("Election request from {}", candidateId);
        // TODO
        return true;
    }
    
    /**
     * Returns the cluster size (Leader only).
     * @return Number of registered nodes
     */
    public int getClusterSize() {
        if (!isLeader || membershipService == null) {
            return 0;
        }
        return membershipService.getClusterSize();
    }
    
    /**
     * Returns the list of registered nodes (Leader only).
     * Used for task distribution and cluster management.
     * @return Copy of active nodes list
     */
    public List<NodeService> getRegisteredNodes() {
        if (!isLeader || membershipService == null) {
            return new ArrayList<>();
        }
        return membershipService.getActiveNodes();
    }
    
    /**
     * Callback invoked when the Leader is detected as dead.
     * This triggers leader election protocol (to be implemented in Phase 2).
     * @param deadLeader The Leader node that died
     */
    private void onLeaderDied(NodeService deadLeader) {
        log.error("[ALERT] LEADER IS DEAD! Node {} no longer responding", getNodeIdSafe(deadLeader));
        log.error("[TODO] Leader election will be implemented in Phase 2");
        // TODO Phase 2: Start Bully Election algorithm
        // BullyElection election = new BullyElection(nodeIdValue, nodeId, ...);
        // election.startElection();
    }
    
    /**
     * Graceful shutdown of this node.
     * Stops heartbeat monitoring and cleans up RMI resources.
     * Great part of the job is handled by HeartbeatMonitor, in fact 
     * we are just calling its stop() method here and then UNBINDING from RMI.
     */
    public void shutdown() {
        log.info("Shutting down node {}...", nodeId);
        
        // Stop heartbeat monitoring if active
        if (leaderMonitor != null) {
            leaderMonitor.stop();
        }
        
        // Shutdown discovery strategy if active
        if (discoveryStrategy != null) {
            discoveryStrategy.shutdown();
        }
        
        // Unbind from RMI registry
        try {
            if (myRegistry != null) {
                myRegistry.unbind("node");
                if (isLeader) {
                    myRegistry.unbind("leader");
                }
            }
        } catch (Exception e) {
            log.warn("Error unbinding from registry: {}", e.getMessage());
        }
        
        // Unexport RMI object
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            log.warn("Error unexporting RMI object: {}", e.getMessage());
        }
        
        log.info("[OK] Node {} shut down cleanly", nodeId);
    }
    
    /**
     * Helper method to safely get node ID for logging.
     */
    private String getNodeIdSafe(NodeService node) {
        try {
            return node.getId();
        } catch (RemoteException e) {
            return "unknown-node";
        }
    }
}

package com.hecaton.node;

import com.hecaton.discovery.DiscoveryService;
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
    // Discovery service (only for Leader)
    private DiscoveryService discoveryService;
    
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
        
        // Initialize discovery service
        this.discoveryService = new DiscoveryService();
        
        // Register itself as first node
        discoveryService.addNode(this);
        
        // Bind as "leader" in addition to "node" (registry already exists from constructor)
        myRegistry.rebind("leader", this);
        
        log.info("[OK] Node {} started as LEADER on port {}", nodeId, port);
        log.info("[OK] Cluster size: {} node(s)", discoveryService.getClusterSize());
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
        
        // Register with Leader (Leader will store reference to our "node" binding)
        leader.registerNode(this);
        
        log.info("[OK] Node {} joined cluster via {}:{}", nodeId, leaderHost, leaderPort);
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
        
        // Delegate to discovery service (handles duplicate check internally)
        discoveryService.addNode(node);
        
        String newNodeId = node.getId();
        log.info("[OK] New node registered: {} (Total: {} nodes)", newNodeId, discoveryService.getClusterSize());
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
        if (!isLeader || discoveryService == null) {
            return 0;
        }
        return discoveryService.getClusterSize();
    }
    
    /**
     * Returns the list of registered nodes (Leader only).
     * Used for task distribution and cluster management.
     * @return Copy of active nodes list
     */
    public List<NodeService> getRegisteredNodes() {
        if (!isLeader || discoveryService == null) {
            return new ArrayList<>();
        }
        return discoveryService.getActiveNodes();
    }
}

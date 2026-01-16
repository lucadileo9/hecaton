package com.hecaton.node;

import com.hecaton.discovery.ClusterMembershipService;
import com.hecaton.discovery.LeaderDiscoveryStrategy;
import com.hecaton.discovery.NodeInfo;
import com.hecaton.discovery.UdpDiscoveryService;
import com.hecaton.election.ElectionStrategy;
import com.hecaton.election.ElectionStrategyFactory;
import com.hecaton.election.ElectionStrategyFactory.Algorithm;
import com.hecaton.monitor.FailureDetector;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    
    // Failure detector (only for Leader, monitors worker heartbeats)
    private FailureDetector failureDetector;
    
    // Leader discovery strategy
    private LeaderDiscoveryStrategy discoveryStrategy;

    // Heartbeat monitor (only for Workers monitoring Leader)
    private HeartbeatMonitor leaderMonitor;
    // Reference to Leader (for Workers)
    private NodeService leaderNode;
    
    private ElectionStrategy electionStrategy;
    private List<NodeInfo> clusterNodesCache;  // Cache for Worker nodes to run election

    // Node capabilities (static for now)
    public final NodeCapabilities capabilities;
    
    /**
     * Creates a new node instance.
     * Each node creates its own RMI Registry on the specified port.
     * @param host Host address (e.g. "localhost" or "192.168.1.10")
     * @param port RMI registry port
     * @param algorithm Election algorithm to use (BULLY, RAFT, RING)
     * @throws RemoteException if RMI export fails
     */
    public NodeImpl(String host, int port, Algorithm algorithm) throws RemoteException {
        // This prevents "Connection refused" errors when RMI auto-detects wrong IP on multi-NIC systems
        if (System.getProperty("java.rmi.server.hostname") == null) {
            System.setProperty("java.rmi.server.hostname", "localhost");
        }
        
        this.nodeIdValue = System.currentTimeMillis(); // Use current time as unique value
        this.nodeId = "node-" + host + "-" + port + "-" + nodeIdValue;
        this.port = port;
        this.isLeader = false;
        
        // Create election strategy via factory
        this.clusterNodesCache = new ArrayList<>();
        this.electionStrategy = ElectionStrategyFactory.create(
            algorithm,                      // Algorithm choice
            this,                           // Self reference (now fully initialized!)
            nodeIdValue,                    // Election ID
            () -> this.clusterNodesCache    // Supplier for lazy cache access
        );
        
        this.capabilities = NodeCapabilities.detect(); // Detect static capabilities

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
        
        // Initialize failure detector (monitors worker heartbeats)
        this.failureDetector = new FailureDetector(
            this::onWorkerFailedPlaceholder,  // Callback (will be TaskScheduler later)
            membershipService
        );
        failureDetector.start();
        log.info("[OK] FailureDetector started for worker health monitoring");
        
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
        
        // Phase 2: Cache cluster nodes for election
        try {
            this.clusterNodesCache = leader.getClusterNodes();
            log.debug("Cached {} cluster nodes for election", clusterNodesCache.size());
            
        } catch (RemoteException e) {
            log.warn("Failed to cache cluster nodes: {}", e.getMessage());
            // Continue anyway - election will work with empty list initially
        }
        
        log.info("[OK] Node {} joined cluster via {}:{}", nodeId, leaderHost, leaderPort);
        
        // Start monitoring Leader's health with periodic cache refresh
        leaderMonitor = new HeartbeatMonitor(
            leaderNode,
            this.nodeId,  // Pass our worker ID for heartbeat identification
            this::onLeaderDied, 
            this::updateClusterCache,  
            "Leader Monitor"
        );
        leaderMonitor.start();
        log.info("[OK] Heartbeat monitoring started for Leader (cache refresh enabled)");
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
        
    @Override
    public long getElectionId() throws RemoteException {
        return nodeIdValue;
    }
    
    @Override
    public void receiveElectionMessage(String candidateId, long candidateElectionId) 
        throws RemoteException {
        log.info("Received ELECTION from {} (ID: {})", candidateId, candidateElectionId);
        
        if (candidateElectionId < this.nodeIdValue) {
            // My ID is higher → start my own election
            log.info("My ID {} is higher. Starting own election", nodeIdValue);
            
            CompletableFuture.runAsync(() -> {
                electionStrategy.startElection();
            });
            
        } else {
            // Candidate has higher ID → accept
            log.info("Candidate has higher ID, accepting");
        }
    }
    
    @Override
    public void receiveCoordinatorMessage(String newLeaderId, String leaderHost, int leaderPort) 
        throws RemoteException {
        log.info("New Leader elected: {} at {}:{}", newLeaderId, leaderHost, leaderPort);
        
        // Notify election strategy (works for ANY implementation, not just Bully)
        electionStrategy.notifyCoordinatorReceived();
        
        try {
            // Reconnect to new Leader
            Registry leaderRegistry = LocateRegistry.getRegistry(leaderHost, leaderPort);
            LeaderService leader = (LeaderService) leaderRegistry.lookup("leader");
            this.leaderNode = (NodeService) leader;
            
            // Re-register with new Leader
            leader.registerNode(this);
            
            // Restart HeartbeatMonitor for new Leader
            if (leaderMonitor != null) {
                leaderMonitor.stop();
            }
            leaderMonitor = new HeartbeatMonitor(
                leaderNode,
                this.nodeId,  // Pass our worker ID
                this::onLeaderDied,
                this::updateClusterCache,
                "Leader Monitor"
            );
            leaderMonitor.start();
            
            log.info("Successfully reconnected to new Leader");
            
        } catch (Exception e) {
            log.error("Failed to reconnect to new Leader: {}", e.getMessage(), e);
        }
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
        log.warn("DEPRECATED: reportTaskCompletion() called. Use submitResult(TaskResult) instead.");
        log.info("Task {} completed with result: {}", taskId, result);
        // TODO: Remove this method after migration to submitResult()
    }
    
    @Override
    public void submitResults(List<com.hecaton.task.TaskResult> results) throws RemoteException {
        if (!isLeader) {
            throw new RemoteException("This node is not the leader");
        }
        
        if (results == null || results.isEmpty()) {
            log.warn("[PLACEHOLDER] Received empty results list");
            return;
        }
        
        log.info("[PLACEHOLDER] Received {} task results", results.size());
        log.warn("TaskScheduler not yet implemented - results logged but not processed");
        
        // Future implementation:
        // taskScheduler.submitResults(results);
    }
    
    @Override
    public List<NodeInfo> getClusterNodes() throws RemoteException {
        if (!isLeader || membershipService == null) {
            throw new RemoteException("Not a Leader or no membership service available");
        }
        
        // Convert NodeService list to NodeInfo DTO list
        return membershipService.getActiveNodes().stream()
            .map(node -> {
                try {
                    String id = node.getId();
                    return new NodeInfo(
                        id,
                        extractHost(id),
                        extractPort(id),
                        node.getElectionId()
                    );
                } catch (RemoteException e) {
                    log.warn("Failed to get info from node: {}", e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public void ping(String workerId) throws RemoteException {
        if (!isLeader) {
            throw new RemoteException("This node is not the leader");
        }
        
        // Delegate to FailureDetector
        if (failureDetector != null) {
            failureDetector.ping(workerId);
        } else {
            log.warn("FailureDetector not initialized, ignoring ping from {}", workerId);
        }
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
     * Triggers Bully Election algorithm (Phase 2).
     * @param deadLeader The Leader node that died
     */
    private void onLeaderDied(NodeService deadLeader) {
        log.error("[ALERT] LEADER IS DEAD! Node {} no longer responding", getNodeIdSafe(deadLeader));
        log.error("Starting Bully Election protocol...");
        // Launch election asynchronously (don't block HeartbeatMonitor thread)
        // Strategy will fetch fresh cache via supplier when needed
        CompletableFuture.runAsync(() -> {
            electionStrategy.startElection();
        });
    }
    
    /**
     * Refreshes the cluster nodes cache by fetching latest list from Leader.
     * Called periodically by HeartbeatMonitor to keep cache up-to-date.
     * Only runs for Worker nodes (Leader has no cache).
     */
    private void updateClusterCache() {
        if (isLeader || leaderNode == null) {
            return; // Leaders don't need cache, only Workers do
        }
        
        try {
            LeaderService leader = (LeaderService) leaderNode;
            List<NodeInfo> freshNodes = leader.getClusterNodes();
            
            // Update cache with fresh data
            this.clusterNodesCache.clear();
            this.clusterNodesCache.addAll(freshNodes);
            
            log.debug("Cluster cache refreshed: {} nodes", clusterNodesCache.size());
            
        } catch (RemoteException e) {
            log.warn("Failed to refresh cluster cache: {}", e.getMessage());
            // Keep old cache - better than nothing
        }
    }
    
    /**
     * Callback invoked when a worker is declared dead by FailureDetector.
     * Placeholder until TaskScheduler is implemented.
     * 
     * @param workerId ID of the dead worker
     */
    private void onWorkerFailedPlaceholder(String workerId) {
        log.warn("Worker {} declared dead by FailureDetector", workerId);
        // TODO: Implement task reassignment when TaskScheduler is ready
        // taskScheduler.onWorkerFailed(workerId);
    }
    
    /**
     * Promotes this node to Leader after winning election.
     * Called by BullyElection when this node has the highest ID among surviving nodes.
     * 
     * @throws RemoteException if RMI operations fail
     */
    public void promoteToLeader() throws RemoteException {
        log.info("Promoting to Leader...");
        
        synchronized (this) {
            if (isLeader) { // if already Leader, don't do anything
                log.warn("Already Leader, skipping promotion");
                return;
            }
            
            this.isLeader = true; //otherwise, set to Leader
            
            // Initialize cluster membership service
            this.membershipService = new ClusterMembershipService();
            membershipService.addNode(this);  // Add self
            
            // Initialize failure detector (monitors worker heartbeats)
            this.failureDetector = new FailureDetector(
                this::onWorkerFailedPlaceholder,
                membershipService
            );
            failureDetector.start();
            log.info("[OK] FailureDetector started after promotion to Leader");
            
            // Populate membership with cached nodes from cluster
            // This is critical - new Leader needs to know about other Workers!
            for (NodeInfo nodeInfo : clusterNodesCache) {
                if (nodeInfo.getElectionId() != this.nodeIdValue) {
                    try {
                        Registry registry = LocateRegistry.getRegistry(nodeInfo.getHost(), nodeInfo.getPort());
                        NodeService node = (NodeService) registry.lookup("node");
                        membershipService.addNode(node);
                        log.debug("Added node {} to new Leader's membership", nodeInfo.getNodeId());
                    } catch (Exception e) {
                        log.warn("Failed to add cached node {} to membership: {}", nodeInfo.getNodeId(), e.getMessage());
                    }
                }
            }
            
            // Stop monitoring old Leader (we ARE the Leader now)
            if (leaderMonitor != null) {
                leaderMonitor.stop();
                leaderMonitor = null;
            }
            this.leaderNode = null;
            
            // Bind as "leader" in RMI registry
            myRegistry.rebind("leader", this);
            
            log.info("Successfully promoted to Leader on port {} with {} nodes in cluster", 
                port, membershipService.getClusterSize());
        }
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
        
        // Stop failure detector if Leader
        if (failureDetector != null) {
            failureDetector.stop();
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
    
    /**
     * Helper method to extract host from node ID string.
     * Node ID format: "node-localhost-5002-1735564820000"
     * @param nodeId Node ID string
     * @return Host portion ("localhost")
     */
    private String extractHost(String nodeId) {
        String[] parts = nodeId.split("-");
        return parts.length > 1 ? parts[1] : "localhost";
    }
    
    /**
     * Helper method to extract port from node ID string.
     * Node ID format: "node-localhost-5002-1735564820000"
     * @param nodeId Node ID string
     * @return Port number (5002)
     */
    private int extractPort(String nodeId) {
        String[] parts = nodeId.split("-");
        try {
            return parts.length > 2 ? Integer.parseInt(parts[2]) : 5001;
        } catch (NumberFormatException e) {
            return 5001;  // Default port
        }
    }

    @Override
    public NodeCapabilities getCapabilities() throws RemoteException {
        return this.capabilities;
    }
    
    @Override
    public void executeTasks(java.util.List<com.hecaton.task.Task> tasks) throws RemoteException {
        if (tasks == null || tasks.isEmpty()) {
            log.warn("Received empty task list, ignoring");
            return;
        }
        
        // TODO: Delegate to TaskExecutor when implemented
        log.info("Received {} tasks from Leader", tasks.size());
        log.warn("TaskExecutor not yet implemented - tasks logged but not executed");
        
        // Log task IDs for debugging
        tasks.forEach(task -> log.debug("  Task: {}", task.getTaskId()));
        
        // Future implementation:
        // taskExecutor.receiveTasks(tasks);
    }

    public ExecutionContext getExecutionContext() {
        return new ExecutionContext(nodeId, port, isLeader, capabilities);

    }
}
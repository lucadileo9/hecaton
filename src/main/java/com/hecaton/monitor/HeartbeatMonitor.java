package com.hecaton.monitor;

import com.hecaton.rmi.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.concurrent.*;

/**
 * Monitors the health of a remote node through periodic ping operations.
 * Executes a callback when the target node is declared dead after consecutive failures.
 * 
 * Thread-safe implementation using ScheduledExecutorService for periodic execution.
 */
public class HeartbeatMonitor {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);
    
    private static final int HEARTBEAT_INTERVAL_MS = 5000;  // 5 seconds between pings
    private static final int MAX_MISSED_HEARTBEATS = 3;     // 15 seconds total before declaring dead
    private static final int CACHE_REFRESH_INTERVAL = 8;   // Refresh cache every 8 heartbeats (~40 seconds)
    
    private final ScheduledExecutorService scheduler; // thread pool for scheduling heartbeats
    private final NodeService targetNode; // the leader to monitor
    private final String myWorkerId; // ID of this worker node (for heartbeat identification)
    private final NodeFailureCallback callback; // function to call on node failure (the rielection)
    private final CacheRefreshCallback cacheRefreshCallback; // function to refresh cluster cache
    private final String monitorName; // descriptive name for logging, I'm not sure if needed
    
    private int missedHeartbeats = 0; // count of consecutive missed heartbeats
    private int heartbeatCount = 0; // total heartbeat count for periodic cache refresh
    private ScheduledFuture<?> heartbeatTask; // reference to the scheduled heartbeat task, so we can cancel it if needed
    
    /**
     * Callback interface invoked when a monitored node is declared dead.
     */
    public interface NodeFailureCallback {
        /**
         * Called when the target node fails to respond after MAX_MISSED_HEARTBEATS attempts.
         * @param deadNode The node that was declared dead
         */
        void onNodeDied(NodeService deadNode);
    }
    /**
     * Callback interface invoked periodically to refresh cluster cache.
     */
    public interface CacheRefreshCallback {
        /**
         * Called every CACHE_REFRESH_INTERVAL heartbeats to update cluster nodes cache.
         */
        void refreshCache();
    }
    
    /**
     * Creates a new HeartbeatMonitor for the specified target node.
     * 
     * @param targetNode The remote node to monitor
     * @param myWorkerId Unique ID of this worker node (sent in heartbeat pings)
     * @param callback Callback to execute when node dies
     * @param cacheRefreshCallback Optional callback to refresh cluster cache (null = no refresh)
     * @param monitorName Descriptive name for logging (e.g., "Leader Monitor")
     */
    public HeartbeatMonitor(NodeService targetNode, String myWorkerId, NodeFailureCallback callback, 
                           CacheRefreshCallback cacheRefreshCallback, String monitorName) {
        this.targetNode = targetNode;
        this.myWorkerId = myWorkerId;
        this.callback = callback;
        this.cacheRefreshCallback = cacheRefreshCallback;
        this.monitorName = monitorName;
        // Single-threaded scheduler for heartbeat tasks
        this.scheduler = Executors.newScheduledThreadPool(1, runnable -> { 
            Thread thread = new Thread(runnable); 
            thread.setName("HeartbeatMonitor-" + monitorName);
            thread.setDaemon(true);  // Don't prevent JVM shutdown
            return thread;
        });
    }
    
    /**
     * Starts the heartbeat monitoring.
     * Sends the first ping immediately, then every HEARTBEAT_INTERVAL_MS.
     */
    public void start() {
        // Prevent multiple starts
        if (heartbeatTask != null  // task already exists
            && !heartbeatTask.isDone() // and is still running
        ) { 
            log.warn("[{}] Heartbeat monitor already running", monitorName);
            return;
        }
        // else schedule new task
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat, // task to run
            0,  // Initial delay = 0 (start immediately)
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        log.info("[{}] Heartbeat monitor started for node {}", monitorName, getNodeIdSafe());
    }
    
    /**
     * Stops the heartbeat monitoring and shuts down the scheduler.
     * Safe to call multiple times.
     */
    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);  // Don't interrupt if running
        }
        
        scheduler.shutdown(); // Stop accepting new tasks
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) { // Wait for existing tasks to finish, max 2s
                scheduler.shutdownNow(); // otherwise force shutdown
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("[{}] Heartbeat monitor stopped", monitorName);
    }
    
    /**
     * Sends a single heartbeat ping to the target node.
     * Called periodically by the scheduler.
     */
    private void sendHeartbeat() {
        try {
            // RMI call with timeout (configured in JVM properties or default ~5s)
            // Cast to LeaderService to access ping(workerId) method
            if (targetNode instanceof com.hecaton.rmi.LeaderService) {
                ((com.hecaton.rmi.LeaderService) targetNode).ping(myWorkerId);
            } else {
                // Fallback for non-Leader nodes (backward compatibility)
                targetNode.ping();
            }
            
            // Successful ping
            if (true) {
                // Reset counter on successful ping
                if (missedHeartbeats > 0) { //if we had missed before
                    log.info("[{}] Node {} recovered after {} missed heartbeat(s)", 
                        monitorName, getNodeIdSafe(), missedHeartbeats);
                }
                missedHeartbeats = 0; // reset counter
                heartbeatCount++; // increment total count
                log.debug("[{}] Heartbeat OK from {} (count: {})", monitorName, getNodeIdSafe(), heartbeatCount);
                
                // Periodic cache refresh (every CACHE_REFRESH_INTERVAL heartbeats)
                if (cacheRefreshCallback != null && heartbeatCount % CACHE_REFRESH_INTERVAL == 0) {
                    log.debug("[{}] Triggering cache refresh (heartbeat #{})", monitorName, heartbeatCount);
                    try {
                        cacheRefreshCallback.refreshCache();
                    } catch (Exception e) {
                        log.warn("[{}] Cache refresh failed: {}", monitorName, e.getMessage());
                    }
                }
            } else {
                // Node responded but returned false (unusual case, literally impossible)
                log.warn("[{}] Node {} returned false for ping()", monitorName, getNodeIdSafe());
                handleMissedHeartbeat(); // probably this will never happen
            }
            
        } catch (RemoteException e) {
            // Network error or node unreachable
            log.warn("[{}] Heartbeat failed for {}: {}", 
                monitorName, getNodeIdSafe(), e.getClass().getSimpleName());
            handleMissedHeartbeat(); // THIS is the normal case for a dead node
        } catch (Exception e) {
            // Catch-all for unexpected errors
            log.error("[{}] Unexpected error during heartbeat: {}", 
                monitorName, e.getMessage(), e);
            handleMissedHeartbeat();
        }
    }
    
    /**
     * Handles a missed heartbeat by incrementing the counter.
     * Declares the node dead if MAX_MISSED_HEARTBEATS is reached.
     */
    private void handleMissedHeartbeat() {
        missedHeartbeats++;
        log.warn("[{}] Missed heartbeat {} of {} for {}", 
            monitorName, missedHeartbeats, MAX_MISSED_HEARTBEATS, getNodeIdSafe());
        
        if (missedHeartbeats >= MAX_MISSED_HEARTBEATS) {
            log.error("[{}] Node {} declared DEAD after {} missed heartbeats", 
                monitorName, getNodeIdSafe(), MAX_MISSED_HEARTBEATS);
            
            stop();  // Stop monitoring this dead node
            
            // Execute callback (e.g., start leader election)
            try {
                callback.onNodeDied(targetNode);
            } catch (Exception e) {
                log.error("[{}] Callback onNodeDied() failed: {}", 
                    monitorName, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Gets the node ID safely, returning "unknown" if RMI call fails.
     * Used for logging when the node may already be dead.
     */
    private String getNodeIdSafe() {
        try {
            return targetNode.getId();
        } catch (RemoteException e) {
            return "unknown-node";
        }
    }
    
    /**
     * @return Current count of consecutive missed heartbeats
     */
    public int getMissedHeartbeats() {
        return missedHeartbeats;
    }
}

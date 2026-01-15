package com.hecaton.monitor;

import com.hecaton.discovery.ClusterMembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Leader-side component that monitors worker health via heartbeat timestamps.
 * Workers periodically call ping(workerId) to signal they're alive.
 * Background thread checks for timeouts and declares workers dead.
 * 
 * Thread-safe implementation for concurrent ping() calls from multiple workers.
 */
public class FailureDetector {
    private static final Logger log = LoggerFactory.getLogger(FailureDetector.class);
    
    private static final long HEARTBEAT_TIMEOUT_MS = 15000;  // 15 seconds (3 missed heartbeats)
    private static final long MONITOR_INTERVAL_MS = 5000;    // Check every 5 seconds
    
    private final Map<String, Long> lastHeartbeatTable = new ConcurrentHashMap<>();
    private final WorkerFailureCallback failureCallback;
    private final ClusterMembershipService membershipService;
    private final ScheduledExecutorService monitor;
    
    private volatile boolean running = false;
    
    /**
     * Callback interface invoked when a worker is declared dead.
     */
    public interface WorkerFailureCallback {
        /**
         * Called when a worker fails to send heartbeats within the timeout period.
         * Implementations should handle task reassignment and cleanup.
         * 
         * @param workerId ID of the dead worker
         */
        void onWorkerFailed(String workerId);
    }
    
    /**
     * Creates a new FailureDetector.
     * 
     * @param failureCallback Callback to execute when worker dies (e.g., TaskScheduler.onWorkerFailed)
     * @param membershipService Cluster membership service to remove dead workers
     */
    public FailureDetector(WorkerFailureCallback failureCallback, 
                          ClusterMembershipService membershipService) {
        this.failureCallback = failureCallback;
        this.membershipService = membershipService;
        
        // Single-threaded scheduler for monitoring
        this.monitor = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("FailureDetector-Monitor");
            thread.setDaemon(true);  // Don't prevent JVM shutdown
            return thread;
        });
    }
    
    /**
     * Called by workers via RMI to signal they're alive.
     * Auto-registers workers on first ping (lazy registration).
     * 
     * @param workerId unique ID of the worker
     */
    public void ping(String workerId) {
        long now = System.currentTimeMillis();
        Long previousPing = lastHeartbeatTable.put(workerId, now);
        
        if (previousPing == null) {
            // First ping from this worker (auto-registration)
            log.info("Worker {} registered in failure detector", workerId);
        } else {
            log.debug("Heartbeat received from worker {} (last: {}ms ago)", 
                     workerId, now - previousPing);
        }
    }
    
    /**
     * Starts the background monitoring thread.
     * Checks for dead workers every MONITOR_INTERVAL_MS.
     */
    public void start() {
        if (running) {
            log.warn("FailureDetector already running");
            return;
        }
        
        running = true;
        
        monitor.scheduleAtFixedRate(
            this::checkForDeadWorkers,
            MONITOR_INTERVAL_MS,  // Initial delay (give workers time to register)
            MONITOR_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        log.info("FailureDetector started (timeout: {}ms, check interval: {}ms)", 
                 HEARTBEAT_TIMEOUT_MS, MONITOR_INTERVAL_MS);
    }
    
    /**
     * Stops the background monitoring thread and shuts down the executor.
     */
    public void stop() {
        running = false;
        
        monitor.shutdown();
        try {
            if (!monitor.awaitTermination(2, TimeUnit.SECONDS)) {
                monitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("FailureDetector stopped");
    }
    
    /**
     * Background task that checks for workers that haven't sent heartbeats.
     * Called periodically by the scheduler.
     */
    private void checkForDeadWorkers() {
        if (!running) {
            return;
        }
        
        long now = System.currentTimeMillis();
        
        // Check each worker's last heartbeat
        for (Map.Entry<String, Long> entry : lastHeartbeatTable.entrySet()) {
            String workerId = entry.getKey();
            long lastSeen = entry.getValue();
            long timeSinceLastPing = now - lastSeen;
            
            if (timeSinceLastPing > HEARTBEAT_TIMEOUT_MS) {
                log.warn("Worker {} declared DEAD (no heartbeat for {}ms, timeout: {}ms)", 
                         workerId, timeSinceLastPing, HEARTBEAT_TIMEOUT_MS);
                onWorkerDied(workerId);
            }
        }
    }
    
    /**
     * Handles worker death by removing it from tracking and notifying callbacks.
     * 
     * @param workerId ID of the dead worker
     */
    private void onWorkerDied(String workerId) {
        // 1. Remove from heartbeat tracking (prevent duplicate notifications)
        lastHeartbeatTable.remove(workerId);
        
        // 2. Notify callback (e.g., TaskScheduler to reassign orphaned tasks)
        if (failureCallback != null) {
            try {
                failureCallback.onWorkerFailed(workerId);
            } catch (Exception e) {
                log.error("Worker failure callback failed for {}: {}", 
                         workerId, e.getMessage(), e);
            }
        }
        
        // 3. Remove from cluster membership list
        boolean removed = membershipService.removeNodeById(workerId);
        if (removed) {
            log.info("Worker {} removed from cluster due to failure", workerId);
        } else {
            log.warn("Worker {} not found in membership service (already removed?)", workerId);
        }
    }
    
    /**
     * Manually removes a worker from failure detection (e.g., graceful shutdown).
     * 
     * @param workerId ID of the worker to remove
     */
    public void removeWorker(String workerId) {
        lastHeartbeatTable.remove(workerId);
        log.info("Worker {} manually removed from failure detector", workerId);
    }
    
    /**
     * @return Number of workers currently being monitored
     */
    public int getMonitoredWorkerCount() {
        return lastHeartbeatTable.size();
    }
    
    /**
     * @return True if the failure detector is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets the time since last heartbeat for a specific worker.
     * 
     * @param workerId ID of the worker
     * @return milliseconds since last heartbeat, or -1 if worker not found
     */
    public long getTimeSinceLastHeartbeat(String workerId) {
        Long lastSeen = lastHeartbeatTable.get(workerId);
        if (lastSeen == null) {
            return -1;
        }
        return System.currentTimeMillis() - lastSeen;
    }
}

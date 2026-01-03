package com.hecaton.manual.node;

import com.hecaton.election.ElectionStrategyFactory.Algorithm;
import com.hecaton.node.NodeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual test: Worker node with automatic Leader discovery via UDP broadcast.
 * 
 * Prerequisites:
 * - Leader must be running (TestLeaderNode in separate terminal)
 * - Leader must have UDP broadcaster active on port 6789
 * - Worker and Leader on same network/subnet
 * 
 * Test sequence:
 * 1. Start Leader: mvn exec:java -Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode
 * 2. Wait 2-3 seconds for Leader initialization
 * 3. Start this Worker: mvn exec:java -Dexec.mainClass=com.hecaton.manual.node.TestWorkerAutoJoin
 * 
 * Expected behavior:
 * - Worker listens on UDP port 6789
 * - Receives Leader announcement within 5 seconds (worst case)
 * - Automatically connects to Leader via RMI
 * - Starts heartbeat monitoring
 * 
 * Success indicators:
 * - "[OK] Discovered Leader: node-localhost-5001-... at localhost:5001"
 * - "[OK] Node joined cluster via localhost:5001"
 * - "[OK] Heartbeat monitoring started for Leader"
 * - Leader terminal shows: "[OK] New node registered: node-localhost-5002-... (Total: 2 nodes)"
 */
public class TestWorkerAutoJoin {
    private static final Logger log = LoggerFactory.getLogger(TestWorkerAutoJoin.class);
    
    public static void main(String[] args) {
        try {
            log.info("=== Worker Node with Auto-Discovery Test ===");
            log.info("Creating Worker on port 5002...");
            
            // Create Worker node
            // Create election strategy for Worker
            NodeImpl worker = new NodeImpl("localhost", 5002, Algorithm.BULLY);
            
            // Automatically discover and join cluster (NEW METHOD)
            log.info("Starting automatic Leader discovery (timeout: 5000ms)...");
            worker.autoJoinCluster();
            
            log.info("=== Test Success - Worker joined cluster automatically ===");
            log.info("Press Ctrl+C to stop...");
            
            // Keep alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

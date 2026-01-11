package com.hecaton.manual.monitor;

import com.hecaton.election.ElectionStrategyFactory.Algorithm;
import com.hecaton.node.NodeImpl;

/**
 * Manual test to observe Leader heartbeat monitoring.
 * 
 * This test starts a Leader and a Worker, then displays heartbeat logs
 * showing the periodic ping operations. (Part 1)
 * Than stopping the Leader will demonstrate heartbeat failure detection. (Part 2)
 * 
 * HOW TO RUN:
 * 
 * Terminal 1 (Leader):
 *   mvn clean test-compile
 *   mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode"
 * 
 * Terminal 2 (Worker with heartbeat - THIS TEST):
 *   mvn exec:java -Dexec.mainClass="com.hecaton.manual.monitor.TestLeaderHeartbeat"
 * 
 * EXPECTED BEHAVIOR:
 * - Terminal 1: Shows "[OK] New node registered" when Worker joins
 * - Terminal 2: Shows periodic "[Leader Monitor] Heartbeat OK from node-localhost-5001-..."
 *               every 5 seconds (DEBUG level logs)
 *  * 
 * WHAT TO OBSERVE:
 * - Heartbeat logs appear every 5 seconds in Worker terminal
 * - No missed heartbeats (counter stays at 0)
 * - Leader responds with ping() = true
 * 
 * Part 2:
 * - After observing heartbeats for a while, KILL THE LEADER (Ctrl+C in Terminal 1)
 * - Observe Worker terminal for heartbeat failure detection logs
 * EXPECTED BEHAVIOR AFTER KILLING LEADER:
 * ~5 seconds:
 *  [WARN] [Leader Monitor] Heartbeat failed for node-localhost-5001-...
 *  [WARN] [Leader Monitor] Missed heartbeat 1 of 3 for node-localhost-5001-...
 * This repeats every 5 seconds until 3 missed heartbeats, than:
 *  18:54:24.687 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat failed for unknown-node: ConnectException
 *  18:54:24.692 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Missed heartbeat 3 of 3 for unknown-node
 *  18:54:24.695 [HeartbeatMonitor-Leader Monitor] ERROR c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Node unknown-node declared DEAD after 3 missed heartbeats
 *  18:54:26.697 [HeartbeatMonitor-Leader Monitor] INFO  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat monitor stopped
 *  18:54:26.705 [HeartbeatMonitor-Leader Monitor] ERROR com.hecaton.node.NodeImpl - [ALERT] LEADER IS DEAD! Node unknown-node no longer responding
 *  18:54:26.707 [HeartbeatMonitor-Leader Monitor] ERROR com.hecaton.node.NodeImpl - [----] Leader election will be implemented in Phase 2 
 * 
 * Press Ctrl+C to stop.
 */
public class TestLeaderHeartbeat {
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Test: Leader Heartbeat Monitoring");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Prerequisites:");
        System.out.println("  1. Leader must be running on port 5001");
        System.out.println("     Run: mvn exec:java -Dexec.mainClass=\"com.hecaton.manual.node.TestLeaderNode\"");
        System.out.println();
        System.out.println("Starting Worker node with heartbeat monitoring...");
        System.out.println();
        
        // Create Worker node on port 5002
        NodeImpl worker = new NodeImpl("localhost", 5002, Algorithm.BULLY);
        worker.joinCluster("localhost", 5001);
        
        System.out.println("[OK] Worker joined cluster and monitoring Leader");
        System.out.println("[OK] Watch for 'Heartbeat OK' messages every 5 seconds (DEBUG logs)");
        System.out.println();
        System.out.println("Press Ctrl+C to stop this Worker node.");
        System.out.println();
        
        // Keep alive
        Thread.sleep(Long.MAX_VALUE);
    }
}

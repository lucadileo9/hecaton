package com.hecaton.election;

/**
 * Strategy interface for Leader election algorithms.
 * Allows swapping election algorithms (Bully, Ring, Raft) without modifying NodeImpl.
 * 
 * 
 * Design Pattern: Strategy Pattern for algorithm interchangeability. So if I want to
 * implement a different election algorithm in the future, I just create a new class
 */
public interface ElectionStrategy {
    /**
     * Initiates the Leader election process.
     * Called when the current Leader is detected as dead by HeartbeatMonitor.
     * 
     * This method should:
     * 1. Identify nodes with higher election IDs
     * 2. Send ELECTION messages to them
     * 3. Either become Leader (if no higher nodes) or wait for COORDINATOR
     * 
     * Implementation is algorithm-specific (Bully, Ring, Raft, etc.)
     */
    void startElection();
}

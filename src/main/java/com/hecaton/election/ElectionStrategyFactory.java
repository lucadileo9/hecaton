package com.hecaton.election;

import com.hecaton.discovery.NodeInfo;
import com.hecaton.election.bully.BullyElection;
import com.hecaton.node.NodeImpl;

import java.util.List;
import java.util.function.Supplier;

/**
 * Factory for creating election strategy instances.
 * Centralizes strategy creation logic and handles dependency injection.
 * Uses Strategy Pattern + Factory Pattern for clean architecture.
 */
public class ElectionStrategyFactory {
    
    /**
     * Available election algorithms.
     */
    public enum Algorithm {
        /**
         * Bully algorithm: Highest ID wins, O(n²) messages.
         * Best for small clusters (3-20 nodes).
         */
        BULLY,
        
        /**
         * Raft consensus algorithm (future and ipotetic implementation).
         * Leader election + log replication.
         */
        RAFT,
        
        /**
         * Ring algorithm (future and ipotetic implementation).
         * Election message circulates in ring topology.
         */
        RING
    }
    
    /**
     * Creates an election strategy based on the specified algorithm.
     * 
     * @param algorithm Algorithm type (BULLY, RAFT, RING)
     * @param selfNode Reference to the node that will use this strategy
     * @param electionId Numeric election ID for comparison
     * @param clusterNodesSupplier Supplier providing fresh cluster cache on demand
     * @return Configured ElectionStrategy instance
     * @throws UnsupportedOperationException if algorithm is not implemented yet
     */
    public static ElectionStrategy create(
            Algorithm algorithm,
            NodeImpl selfNode, 
            long electionId, 
            Supplier<List<NodeInfo>> clusterNodesSupplier) {
        
        switch (algorithm) {
            case BULLY:
                return new BullyElection(selfNode, electionId, clusterNodesSupplier);
                
            case RAFT:
                // TODO
                throw new UnsupportedOperationException("Raft election not yet implemented");
                
            case RING:
                // TODO
                throw new UnsupportedOperationException("Ring election not yet implemented");
                
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}

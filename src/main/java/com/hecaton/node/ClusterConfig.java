package com.hecaton.node;

import com.hecaton.discovery.NodeInfo;
import com.hecaton.election.ElectionStrategy;
import com.hecaton.election.bully.BullyElection;
import com.hecaton.task.assignment.AssignmentStrategy;
import com.hecaton.task.assignment.RoundRobinAssignment;
import com.hecaton.task.splitting.SplittingStrategy;
import com.hecaton.task.splitting.UniformSplitting;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Centralized configuration for cluster nodes.
 * Contains all strategic choices for both election and task execution.
 * 
 * Uses Builder pattern for clean, validated construction.
 * Immutable after creation - thread-safe and serializable.
 * 
 * Example usage:
 * ClusterConfig config = new ClusterConfig.Builder()
 *     .electionAlgorithm(Algorithm.BULLY)
 *     .splittingStrategy(new UniformSplitting())
 *     .assignmentStrategy(new RoundRobinAssignment())
 *     .build();
 * 
 * NodeImpl node = new NodeImpl("localhost", 5001, config);
 */
public class ClusterConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
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
         * Raft consensus algorithm (future implementation).
         * Leader election + log replication.
         */
        RAFT,
        
        /**
         * Ring algorithm (future implementation).
         * Election message circulates in ring topology.
         */
        RING
    }
    
    private final Algorithm electionAlgorithm;
    private final SplittingStrategy splittingStrategy;
    private final AssignmentStrategy assignmentStrategy;
    
    /**
     * Private constructor - use Builder to create instances.
     */
    private ClusterConfig(Builder builder) {
        this.electionAlgorithm = builder.electionAlgorithm;
        this.splittingStrategy = builder.splittingStrategy;
        this.assignmentStrategy = builder.assignmentStrategy;
    }
    
    /**
     * Creates an election strategy instance based on configured algorithm.
     * Factory method that encapsulates strategy creation logic.
     * 
     * @param selfNode Reference to the node that will use this strategy
     * @param electionId Numeric election ID for comparison
     * @param clusterNodesSupplier Supplier providing fresh cluster cache on demand
     * @return Configured ElectionStrategy instance
     * @throws UnsupportedOperationException if algorithm is not implemented yet
     */
    public ElectionStrategy createElectionStrategy(
            NodeImpl selfNode, 
            long electionId, 
            Supplier<List<NodeInfo>> clusterNodesSupplier) {
        
        switch (electionAlgorithm) {
            case BULLY:
                return new BullyElection(selfNode, electionId, clusterNodesSupplier);
                
            case RAFT:
                throw new UnsupportedOperationException("Raft election not yet implemented");
                
            case RING:
                throw new UnsupportedOperationException("Ring election not yet implemented");
                
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + electionAlgorithm);
        }
    }
    
    /**
     * Returns the configured election algorithm.
     */
    public Algorithm getElectionAlgorithm() {
        return electionAlgorithm;
    }
    
    /**
     * Returns the configured splitting strategy for task execution.
     */
    public SplittingStrategy getSplittingStrategy() {
        return splittingStrategy;
    }
    
    /**
     * Returns the configured assignment strategy for task execution.
     */
    public AssignmentStrategy getAssignmentStrategy() {
        return assignmentStrategy;
    }
    
    @Override
    public String toString() {
        return "ClusterConfig{" +
                "electionAlgorithm=" + electionAlgorithm +
                ", splittingStrategy=" + splittingStrategy.getName() +
                ", assignmentStrategy=" + assignmentStrategy.getName() +
                '}';
    }
    
    /**
     * Builder for creating ClusterConfig instances.
     * Provides fluent API with validation and sensible defaults.
     */
    public static class Builder {
        private Algorithm electionAlgorithm = Algorithm.BULLY;  // Default
        private SplittingStrategy splittingStrategy = new UniformSplitting();  // Default
        private AssignmentStrategy assignmentStrategy = new RoundRobinAssignment();  // Default
        
        /**
         * Sets the election algorithm.
         * Default: BULLY
         */
        public Builder electionAlgorithm(Algorithm algorithm) {
            this.electionAlgorithm = Objects.requireNonNull(algorithm, "algorithm cannot be null");
            return this;
        }
        
        /**
         * Sets the splitting strategy for dividing jobs into tasks.
         * Default: UniformSplitting
         */
        public Builder splittingStrategy(SplittingStrategy strategy) {
            this.splittingStrategy = Objects.requireNonNull(strategy, "splittingStrategy cannot be null");
            return this;
        }
        
        /**
         * Sets the assignment strategy for distributing tasks to workers.
         * Default: RoundRobinAssignment
         */
        public Builder assignmentStrategy(AssignmentStrategy strategy) {
            this.assignmentStrategy = Objects.requireNonNull(strategy, "assignmentStrategy cannot be null");
            return this;
        }
        
        /**
         * Builds the ClusterConfig instance.
         * All fields have sensible defaults if not explicitly set.
         * 
         * @return Immutable ClusterConfig instance
         */
        public ClusterConfig build() {
            // Validation already done in setters via requireNonNull
            return new ClusterConfig(this);
        }
    }
}

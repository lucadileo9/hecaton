package com.hecaton.discovery;

/**
 * Strategy interface for Leader discovery mechanisms in the Hecaton cluster.
 * 
 * This interface allows multiple discovery implementations (UDP broadcast, multicast,
 * static configuration, database lookup, etc.) to be used interchangeably without
 * modifying the core NodeImpl class.
 * 
 * Implementations:
 * - {@link UdpDiscoveryService} - UDP broadcast discovery for LAN environments
 * - Future: MulticastDiscoveryService, StaticConfigDiscoveryService, etc.
 * 
 * Strategy Pattern Benefits:
 * - Open/Closed Principle: Add new discovery methods without modifying existing code
 * - Dependency Inversion: NodeImpl depends on interface, not concrete implementations
 * - Testability: Easy to mock for unit tests
 * - Configurability: Switch discovery methods via configuration
 * 
 * @see NodeInfo
 * @see UdpDiscoveryService
 */
public interface LeaderDiscoveryStrategy {
    
    /**
     * Discovers the Leader node in the cluster.
     * 
     * Different implementations may use different mechanisms:
     * - UDP broadcast (UdpDiscoveryService)
     * - Multicast groups
     * - Static configuration files
     * - Database/Redis queries
     * - Cloud service discovery (Consul, etcd, etc.)
     * 
     * This method MAY block until discovery completes or timeout expires,
     * depending on the implementation.
     * 
     * @param timeoutMs Maximum time to wait for discovery in milliseconds.
     *                  Value of 0 means no timeout (wait indefinitely).
     *                  Negative values are invalid and may throw IllegalArgumentException.
     * @return NodeInfo object containing discovered Leader's connection details,
     *         or null if no Leader found within timeout period
     * @throws Exception if discovery fails critically (e.g., network error, 
     *                   configuration error, interrupted)
     */
    NodeInfo discoverLeader(int timeoutMs) throws Exception;
    
    /**
     * Releases resources used by this discovery strategy.
     * 
     * Implementations should:
     * - Close network sockets
     * - Shutdown executor threads
     * - Release file handles or database connections
     * - Set flags to stop background tasks
     * 
     * This method should be idempotent (safe to call multiple times).
     * After calling shutdown(), the behavior of discoverLeader() is undefined.
     */
    void shutdown();
}

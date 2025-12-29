package com.hecaton.discovery;

import java.io.Serializable;

/**
 * Metadata about a discovered node.
 * Used by UdpDiscoveryService to exchange node information via UDP broadcast.
 * Serializable for network transmission.
 * This class exists per evitare to send a lot of separate fields over the network.
 */
public class NodeInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String nodeId;
    private final String host;
    private final int port;
    private final long timestamp;
    
    /**
     * Creates node metadata for a discovered node.
     * 
     * @param nodeId Unique node identifier (e.g., "node-localhost-5001-1735...")
     * @param host Hostname or IP address (e.g., "localhost" or "192.168.1.10")
     * @param port RMI registry port
     */
    public NodeInfo(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * @return Unique node identifier (e.g., "node-localhost-5001-1735...")
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * @return Hostname or IP address of the node
     */
    public String getHost() {
        return host;
    }
    
    /**
     * @return RMI registry port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * @return Timestamp when this NodeInfo was created (milliseconds since epoch)
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * @return Age of this discovery info in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("NodeInfo{id=%s, host=%s, port=%d, age=%dms}", 
                           nodeId, host, port, getAgeMs());
    }
}

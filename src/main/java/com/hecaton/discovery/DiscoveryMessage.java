package com.hecaton.discovery;

import java.io.Serializable;

/**
 * UDP message format for automatic leader discovery.
 * Broadcasted by Leader nodes, received by Workers.
 * Uses Java serialization for simplicity (can migrate to Protocol Buffers later if needed).
 */
public class DiscoveryMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Type of discovery message.
     */
    public enum MessageType {
        /** Leader announces its presence (broadcasted periodically) */
        LEADER_ANNOUNCEMENT,
        
        /** Worker requests leader info (sent to multicast group) */
        LEADER_REQUEST,
        
        /** Leader responds to specific worker request (unicast) */
        LEADER_RESPONSE
    }
    
    private final MessageType messageType;
    private final String nodeId; // Sender's unique node ID
    private final String host;
    private final int port;
    private final long timestamp;
    
    /**
     * Creates a discovery message.
     * 
     * @param messageType Type of message (announcement, request, response)
     * @param nodeId Sender's unique node ID
     * @param host Sender's hostname/IP
     * @param port Sender's RMI registry port
     */
    public DiscoveryMessage(MessageType messageType, String nodeId, String host, int port) {
        this.messageType = messageType;
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Factory method for Leader announcement messages.
     * This metod is very simple, since it just create a DiscoveryMessage with the correct message type.
     * @param nodeId Leader's node ID
     * @param host Leader's hostname
     * @param port Leader's RMI port
     * @return Discovery message ready for broadcast
     */
    public static DiscoveryMessage createLeaderAnnouncement(String nodeId, String host, int port) {
        return new DiscoveryMessage(MessageType.LEADER_ANNOUNCEMENT, nodeId, host, port);
    }
    
    /**
     * Converts this message to NodeInfo for easier consumption.
     * 
     * @return NodeInfo representation of sender
     */
    public NodeInfo toNodeInfo() {
        return new NodeInfo(nodeId, host, port);
    }
    
    /**
     * @return Type of discovery message (LEADER_ANNOUNCEMENT, LEADER_REQUEST, or LEADER_RESPONSE)
     */
    public MessageType getMessageType() {
        return messageType;
    }
    
    /**
     * @return Unique identifier of the sender node
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * @return Hostname or IP address of the sender
     */
    public String getHost() {
        return host;
    }
    
    /**
     * @return RMI registry port of the sender
     */
    public int getPort() {
        return port;
    }
    
    /**
     * @return Timestamp when this message was created (milliseconds since epoch)
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * @return Age of this message in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("DiscoveryMessage{type=%s, nodeId=%s, host=%s, port=%d, age=%dms}",
                           messageType, nodeId, host, port, getAgeMs());
    }
}

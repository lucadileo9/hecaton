package com.hecaton.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * UDP-based automatic leader discovery service.
 * 
 * Leader Mode (Broadcaster):
 * 
 *   - Broadcasts LEADER_ANNOUNCEMENT messages every 5 seconds via UDP</li>
 *   - Uses broadcast address 255.255.255.255 for LAN-wide discovery</i>
 *   - Runs in background thread until shutdown</i>
 * 
 * Worker Mode (Listener):
 * 
 *   - Listens for LEADER_ANNOUNCEMENT messages on UDP broadcast port
 *   - Returns first discovered leader within timeout period
 *   - Blocks until discovery succeeds or timeout expires
 * 
 * Fallback Strategy:
 * If UDP discovery fails (firewall, port conflict), manual join via `--join host:port` still works.
 * 
 * @see LeaderDiscoveryStrategy
 */
public class UdpDiscoveryService implements LeaderDiscoveryStrategy {
    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryService.class);
    
    // UDP broadcast configuration
    private static final int DEFAULT_BROADCAST_PORT = 6789;
    private static final int BROADCAST_INTERVAL_MS = 5000;  // 5 seconds
    private static final String BROADCAST_ADDRESS = "255.255.255.255";
    
    private final int broadcastPort;
    private final String nodeId;
    private final String host;
    private final int rmiPort;
    
    // Broadcaster state (Leader only)
    private ExecutorService broadcasterExecutor;
    private DatagramSocket broadcasterSocket;
    private volatile boolean isBroadcasting = false; 
    // the use of 'volatile' keyword here is to ensure that changes to isBroadcasting are visible ACROSS threads
    // so that when one thread updates its value, other threads see the updated value immediately.
    // For example, when the main thread calls shutdown() and sets isBroadcasting to false,
    // the broadcaster thread will see that change without delay and stop broadcasting.
    // See the complete docs for more details
    
    /**
     * Creates a UDP discovery service (Worker mode - no parameters needed for listening).
     */
    public UdpDiscoveryService() {
        this(DEFAULT_BROADCAST_PORT, null, null, 0);
    }
    
    /**
     * Creates a UDP discovery service (Leader mode - for broadcasting).
     * 
     * @param rmiPort RMI registry port of this Leader node
     * @param nodeId Unique identifier of this Leader node
     */
    public UdpDiscoveryService(int rmiPort, String nodeId) {
        this(DEFAULT_BROADCAST_PORT, nodeId, "localhost", rmiPort);
    }
    
    /**
     * Full constructor with custom broadcast port.
     * 
     * @param broadcastPort UDP port for discovery messages (default 6789)
     * @param nodeId Node identifier (null if Worker mode)
     * @param host Hostname/IP (null if Worker mode)
     * @param rmiPort RMI port (0 if Worker mode)
     */
    public UdpDiscoveryService(int broadcastPort, String nodeId, String host, int rmiPort) {
        this.broadcastPort = broadcastPort;
        this.nodeId = nodeId;
        this.host = host;
        this.rmiPort = rmiPort;
    }
    
    /**
     * Starts UDP broadcaster (Leader mode only).
     * Sends LEADER_ANNOUNCEMENT messages every 5 seconds until shutdown.
     * 
     * @throws IOException if UDP socket creation fails (port conflict, permission denied)
     */
    public void startBroadcaster() throws IOException {
        if (nodeId == null || host == null || rmiPort == 0) { // Node info not provided
            throw new IllegalStateException("Cannot start broadcaster: node info not provided (use constructor with rmiPort and nodeId)");
        }
        
        if (isBroadcasting) { // If already running do not start again
            log.warn("Broadcaster already running");
            return;
        }
        
        // Create UDP socket, here the SO give a random available port, from which we will send the broadcasts
        broadcasterSocket = new DatagramSocket(); 
        broadcasterSocket.setBroadcast(true); // Enable broadcast
        
        // Start broadcaster thread
        broadcasterExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UDP-Broadcaster-" + nodeId);
            t.setDaemon(true);  // Don't block JVM shutdown
            return t;
        });
        
        isBroadcasting = true;
        
        broadcasterExecutor.submit(() -> {
            log.info("[OK] UDP broadcaster started on port {} (broadcasting to {}:{})", 
                     broadcasterSocket.getLocalPort(), BROADCAST_ADDRESS, broadcastPort);
            
            while (isBroadcasting) {
                try {
                    // we'll use a dedicated method to send the announcement
                    broadcastLeaderAnnouncement();
                    Thread.sleep(BROADCAST_INTERVAL_MS);
                } catch (InterruptedException e) {
                    log.debug("Broadcaster interrupted, stopping...");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error broadcasting leader announcement: {}", e.getMessage());
                }
            }
            
            log.info("UDP broadcaster stopped");
        });
    }
    
    /**
     * Sends a single LEADER_ANNOUNCEMENT broadcast message.
     */
    private void broadcastLeaderAnnouncement() throws IOException {
        // firstly let's create the DiscoveryMessage for the leader announcement
        DiscoveryMessage message = DiscoveryMessage.createLeaderAnnouncement(nodeId, host, rmiPort);
        
        // Serialize message
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
        }
        byte[] data = baos.toByteArray();
        
        // Send UDP broadcast
        InetAddress broadcastAddr = InetAddress.getByName(BROADCAST_ADDRESS); // specific broadcast address
        // Packet creation
        DatagramPacket packet = new DatagramPacket(
            data, // the data to send
            data.length, // length of data
            broadcastAddr, // destination address
            broadcastPort); // destination port
        broadcasterSocket.send(packet);
        
        log.debug("Broadcasted LEADER_ANNOUNCEMENT: {} bytes", data.length);
    }
    
    /**
     * Discovers the Leader by listening for LEADER_ANNOUNCEMENT broadcasts (Worker mode).
     * Blocks until a Leader is found or timeout expires.
     * 
     * @param timeoutMs Maximum time to wait for discovery (milliseconds)
     * @return NodeInfo of discovered Leader, or null if timeout
     * @throws Exception if UDP socket creation fails or deserialization error
     */
    @Override
    public NodeInfo discoverLeader(int timeoutMs) throws Exception {
        log.info("Listening for Leader announcements (timeout: {}ms)...", timeoutMs);
        
        DatagramSocket listenerSocket = null;
        try {
            // Create listener socket, contrarily to the broadcaster, here we need to bind to the specific port
            listenerSocket = new DatagramSocket(broadcastPort);
            listenerSocket.setSoTimeout(timeoutMs); // we cannot wait forever, so we set a timeout
            
            // We create a buffer to receive the incoming packets, is like a white canvas where the data will be written
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            // Here we start to wait for incoming packets
            listenerSocket.receive(packet);
            
            // Deserialize message
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                // the deserialized object will be of type DiscoveryMessage, so it contains all the info we need
                DiscoveryMessage message = (DiscoveryMessage) ois.readObject();
                
                if (message.getMessageType() == DiscoveryMessage.MessageType.LEADER_ANNOUNCEMENT) { // check message type
                    NodeInfo leaderInfo = message.toNodeInfo();
                    log.info("[OK] Discovered Leader: {} at {}:{} (message age: {}ms)", 
                             leaderInfo.getNodeId(), leaderInfo.getHost(), leaderInfo.getPort(), 
                             message.getAgeMs());
                    return leaderInfo; // return the discovered leader info
                } else {
                    log.warn("Received unexpected message type: {}", message.getMessageType());
                    return null;
                }
            }
            
        } catch (SocketTimeoutException e) {
            log.warn("No Leader found within {}ms timeout", timeoutMs);
            return null;
        } catch (SocketException e) {
            log.error("Failed to create UDP listener socket on port {}: {}", broadcastPort, e.getMessage());
            log.error("  Possible causes: Port already in use, firewall blocking UDP");
            throw e;
        } finally {
            if (listenerSocket != null && !listenerSocket.isClosed()) {
                listenerSocket.close();
            }
        }
    }
    
    /**
     * Stops the broadcaster (Leader mode) and releases UDP socket.
     * Idempotent - safe to call multiple times.
     */
    @Override
    public void shutdown() {
        if (isBroadcasting) {
            log.info("Shutting down UDP broadcaster...");
            isBroadcasting = false;
            
            if (broadcasterExecutor != null) {
                broadcasterExecutor.shutdownNow();
                try {
                    if (!broadcasterExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.warn("Broadcaster thread did not terminate within 2 seconds");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            if (broadcasterSocket != null && !broadcasterSocket.isClosed()) {
                broadcasterSocket.close();
            }
            
            log.info("[OK] UDP broadcaster shut down cleanly");
        }
    }
    
    public int getBroadcastPort() {
        return broadcastPort;
    }
    
    public boolean isBroadcasting() {
        return isBroadcasting;
    }
}

package com.hecaton.node;

import com.hecaton.election.bully.BullyElection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Bootstrap class to start a Hecaton node from command line.
 * 
 * Usage:
 *   Leader:  java NodeBootstrap --port 5001 --leader
 *   Worker:  java NodeBootstrap --port 5002 --join localhost:5001
 */
public class NodeBootstrap {
    private static final Logger log = LoggerFactory.getLogger(NodeBootstrap.class);
    
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            int port = 5001;  // Default port
            boolean isLeader = false;
            String joinHost = null;
            int joinPort = 0;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--port":
                        port = Integer.parseInt(args[++i]);
                        break;
                    case "--leader":
                        isLeader = true;
                        break;
                    case "--join":
                        String[] parts = args[++i].split(":");
                        joinHost = parts[0];
                        joinPort = Integer.parseInt(parts[1]);
                        break;
                    default:
                        log.warn("Unknown argument: {}", args[i]);
                }
            }
            
            // Validate arguments
            if (!isLeader && joinHost == null) {
                System.err.println("ERROR: Must specify either --leader or --join <host:port>");
                printUsage();
                System.exit(1);
            }
            
            // Create election strategy and node
            BullyElection electionStrategy = new BullyElection(null, 0, new ArrayList<>());
            NodeImpl node = new NodeImpl("localhost", port, electionStrategy);
            
            // Start as Leader or join as Worker
            if (isLeader) {
                node.startAsLeader();
                System.out.println("========================================");
                System.out.println("  LEADER NODE RUNNING");
                System.out.println("  Port: " + port);
                System.out.println("  Press Ctrl+C to stop");
                System.out.println("========================================");
            } else {
                node.joinCluster(joinHost, joinPort);
                System.out.println("========================================");
                System.out.println("  WORKER NODE RUNNING");
                System.out.println("  Port: " + port);
                System.out.println("  Leader: " + joinHost + ":" + joinPort);
                System.out.println("  Press Ctrl+C to stop");
                System.out.println("========================================");
            }
            
            // Keep process alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Failed to start node", e);
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("\nUsage:");
        System.out.println("  Start as Leader:");
        System.out.println("    mvn exec:java -Dexec.args=\"--port 5001 --leader\"");
        System.out.println("\n  Start as Worker:");
        System.out.println("    mvn exec:java -Dexec.args=\"--port 5002 --join localhost:5001\"");
        System.out.println();
    }
}

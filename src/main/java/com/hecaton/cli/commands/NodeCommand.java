package com.hecaton.cli.commands;

import com.hecaton.cli.model.NodeMode;
import com.hecaton.node.NodeImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Node lifecycle management commands
 */
@Command(
    name = "node",
    description = "Manage Hecaton node lifecycle",
    subcommands = {
        NodeCommand.Start.class,
        NodeCommand.Stop.class,
        NodeCommand.Status.class
    }
)
public class NodeCommand {
    
    /**
     * Start a Hecaton node (Leader or Worker)
     */
    @Command(name = "start", description = "Start a Hecaton node")
    static class Start implements Callable<Integer> {
        
        @Option(
            names = {"-m", "--mode"},
            description = "Node mode: ${COMPLETION-CANDIDATES}",
            defaultValue = "WORKER",
            required = true
        )
        NodeMode mode;
        
        @Option(
            names = {"-p", "--port"},
            description = "RMI registry port (default: ${DEFAULT-VALUE})",
            defaultValue = "5001"
        )
        int port;
        
        @Option(
            names = {"-h", "--host"},
            description = "Hostname/IP for RMI binding (default: ${DEFAULT-VALUE})",
            defaultValue = "localhost"
        )
        String host;
        
        @Option(
            names = {"-j", "--join"},
            description = "Join existing cluster (format: HOST:PORT, if not provided, auto-discovery will be attempted)"
        )
        String joinAddress;
        
        @Override
        public Integer call() throws Exception {
            // Validation
            if (mode == NodeMode.WORKER && joinAddress == null) {
                System.err.println("WARNING: without --join for WORKER mode auto-discovery will be attempted");
            }
            
            if (mode == NodeMode.LEADER && joinAddress != null) {
                System.err.println("WARNING: --join is ignored for LEADER mode");
            }
            
            // Banner
            System.out.println("========================================");
            System.out.println("  Hecaton Node - Starting");
            System.out.println("========================================");
            System.out.println();
            
            // Create node
            NodeImpl node = new NodeImpl(host, port);
            
            // Start based on mode
            if (mode == NodeMode.LEADER) {
                node.startAsLeader();
                System.out.println("[OK] Leader started successfully");
                System.out.println("  Host: " + host);
                System.out.println("  Port: " + port);
                System.out.println("  Node ID: " + node.getId());
                System.out.println();
                System.out.println("Waiting for Workers to join...");
            } else {
                if (joinAddress == null) { // auto-discovery
                    node.autoJoinCluster();
                    System.out.println("[OK] Worker started successfully");
                    System.out.println("  Host: " + host);
                    System.out.println("  Port: " + port);
                    System.out.println("  Node ID: " + node.getId());
                    System.out.println("  Auto-discovering Leader");
                } else { // join specified leader
                String[] parts = joinAddress.split(":");
                String leaderHost = parts[0];
                int leaderPort = Integer.parseInt(parts[1]);
                
                node.joinCluster(leaderHost, leaderPort);
                System.out.println("[OK] Worker started successfully");
                System.out.println("  Host: " + host);
                System.out.println("  Port: " + port);
                System.out.println("  Node ID: " + node.getId());
                System.out.println("  Leader: " + joinAddress);
                }
            }
            
            System.out.println();
            System.out.println("Press Ctrl+C to stop");
            System.out.println();
            
            // Keep alive
            Thread.currentThread().join();
            return 0;
        }
    }
    
    /**
     * Stop a running node
     */
    @Command(name = "stop", description = "Stop a running node")
    static class Stop implements Callable<Integer> {
        
        @Option(
            names = {"--node-id"},
            description = "Node ID to stop"
        )
        String nodeId;
        
        @Option(
            names = {"--all"},
            description = "Stop all nodes"
        )
        boolean all;
        
        @Override
        public Integer call() {
            System.out.println("Stop command not yet implemented, but I would like");
            System.out.println("So try again later...");
            System.out.println("Use Ctrl+C to stop nodes for now");
            // TODO: Implement via shutdown hook or RMI call
            return 0;
        }
    }
    
    /**
     * Show node status
     */
    @Command(name = "status", description = "Show node status")
    static class Status implements Callable<Integer> {
        
        @Override
        public Integer call() {
            System.out.println("Status command not yet implemented");
            System.out.println("Same as stop, sorry!");
            // TODO: Read from PID file or RMI call
            return 0;
        }
    }
}

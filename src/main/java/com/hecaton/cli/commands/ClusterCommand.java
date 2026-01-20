package com.hecaton.cli.commands;

import com.hecaton.rmi.LeaderService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Callable;
import com.hecaton.discovery.NodeInfo;

/**
 * Cluster management commands
 */
@Command(
    name = "cluster",
    description = "Manage cluster operations",
    subcommands = {
        ClusterCommand.Status.class,
        ClusterCommand.Nodes.class
    }
)
public class ClusterCommand {
    
    /**
     * Show cluster status overview
     */
    @Command(name = "status", description = "Show cluster status overview")
    static class Status implements Callable<Integer> {
        
        @Option(
            names = {"--leader"},
            description = "Leader address (format: HOST:PORT, default: ${DEFAULT-VALUE})",
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        @Override
        public Integer call() throws Exception {
            System.out.println("========================================");
            System.out.println("  Cluster Status");
            System.out.println("========================================");
            System.out.println();
            
            try {
                // Connect to Leader
                String[] parts = leaderAddress.split(":");
                Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
                LeaderService leader = (LeaderService) registry.lookup("leader");
                
                // Get cluster information
                int clusterSize = leader.getClusterSize();
                
                System.out.println("Leader: " + leaderAddress);
                System.out.println("Cluster Size: " + clusterSize + " node(s)");
                System.out.println("  - 1 Leader");
                System.out.println("  - " + (clusterSize - 1) + " Worker(s)");
                System.out.println();
                System.out.println("Status: [OK] Cluster is operational");
                
                return 0;
                
            } catch (Exception e) {
                System.err.println("ERROR: Cannot connect to Leader at " + leaderAddress);
                System.err.println("  " + e.getMessage());
                System.err.println();
                System.err.println("Make sure the Leader node is running:");
                System.err.println("  hecaton node start --mode LEADER --port " + leaderAddress.split(":")[1]);
                return 1;
            }
        }
    }
    
    /**
     * List all nodes in the cluster
     */
    @Command(name = "nodes", description = "List all nodes in the cluster")
    static class Nodes implements Callable<Integer> {
        
        @Option(
            names = {"--leader"},
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        @Override
        public Integer call() throws Exception {
            System.out.println("========================================");
            System.out.println("  Cluster Nodes");
            System.out.println("========================================");
            System.out.println();
            
            try {
                // Connect to Leader
                String[] parts = leaderAddress.split(":");
                Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
                LeaderService leader = (LeaderService) registry.lookup("leader");
                
                // Get node list
                java.util.List<NodeInfo> nodeInfos = leader.getClusterNodes();
                
                System.out.println("Total Nodes: " + nodeInfos.size());
                System.out.println();
                
                for (int i = 0; i < nodeInfos.size(); i++) {
                    String nodeId = nodeInfos.get(i).getNodeId();
                    String role = i == 0 ? "LEADER" : "WORKER";
                    System.out.println((i + 1) + ". " + nodeId + " (" + role + ")");
                }
                
                return 0;
                
            } catch (Exception e) {
                System.err.println("ERROR: Cannot connect to Leader at " + leaderAddress);
                System.err.println("  " + e.getMessage());
                return 1;
            }
        }
    }
}

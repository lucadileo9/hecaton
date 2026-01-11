package com.hecaton.manual.election;

import com.hecaton.election.ElectionStrategyFactory.Algorithm;
import com.hecaton.node.NodeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test manuale per verificare l'elezione automatica del Leader.
 * 
 * Scenario di test:
 * 1. Avviare 3 nodi (1 Leader + 2 Workers)
 * 2. Killare il Leader (Ctrl+C su Terminal 1)
 * 3. Verificare che il Worker con ID più alto diventa nuovo Leader
 * 
 * Comandi per testare:
 * 
 * Terminal 1 (Leader iniziale - porta 5001):
 * mvn test-compile exec:java -Dexec.mainClass=com.hecaton.manual.election.TestThreeNodeElection -Dexec.args="--port 5001 --leader"
 * 
 * Terminal 2 (Worker - porta 5002):
 * mvn test-compile exec:java -Dexec.mainClass=com.hecaton.manual.election.TestThreeNodeElection -Dexec.args="--port 5002 --join localhost:5001"
 * 
 * Terminal 3 (Worker - porta 5003):
 * mvn test-compile exec:java -Dexec.mainClass=com.hecaton.manual.election.TestThreeNodeElection -Dexec.args="--port 5003 --join localhost:5001"
 * 
 * Attendi 5 secondi per stabilizzazione, poi premi Ctrl+C su Terminal 1.
 * 
 * Verifica nei log:
 * - Terminal 2 e 3 dovrebbero mostrare " Leader heartbeat failed"
 * - Terminal 3 (ID più alto) dovrebbe mostrare "I am the new Leader"
 * - Terminal 2 dovrebbe mostrare "New Leader elected: node-localhost-5003-..."
 * - Entrambi dovrebbero riconnettersi al nuovo Leader
 * 
 * Criteri di successo:
 * - Elezione completa entro 30 secondi
 * - Nodo con porta 5003 diventa Leader (ha ID più alto)
 * - Nodo porta 5002 si riconnette al nuovo Leader
 * - No split-brain (solo 1 Leader attivo)
 */
public class TestThreeNodeElection {
    
    private static final Logger logger = LoggerFactory.getLogger(TestThreeNodeElection.class);
    
    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            String mode = null;
            int port = -1;
            String leaderHost = null;
            int leaderPort = -1;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--port":
                        if (i + 1 < args.length) {
                            port = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "--leader":
                        mode = "leader";
                        break;
                    case "--join":
                        mode = "worker";
                        if (i + 1 < args.length) {
                            String[] parts = args[++i].split(":");
                            leaderHost = parts[0];
                            leaderPort = Integer.parseInt(parts[1]);
                        }
                        break;
                }
            }
            
            // Validate arguments
            if (port == -1) {
                System.err.println("Missing --port argument");
                printUsage();
                System.exit(1);
            }
            
            if (mode == null) {
                System.err.println("Missing --leader or --join argument");
                printUsage();
                System.exit(1);
            }
            
            // Create election strategy and node
            NodeImpl node = new NodeImpl("localhost", port, Algorithm.BULLY);
            
            if ("leader".equals(mode)) {
                logger.info("Starting node as LEADER on port {}", port);
                node.startAsLeader();
                logger.info("Leader node started successfully");
                logger.info("Node ID: {}", node.getId());
                logger.info("Election ID: {}", node.getElectionId());
                logger.info("");
                logger.info("To test election:");
                logger.info("   1. Start 2 Workers on different terminals");
                logger.info("   2. Wait 5 seconds for cluster stabilization");
                logger.info("   3. Press Ctrl+C here to kill the Leader");
                logger.info("   4. Watch Workers elect new Leader");
                logger.info("");
            } else {
                logger.info("Starting node as WORKER on port {}, joining Leader at {}:{}", 
                    port, leaderHost, leaderPort);
                node.joinCluster(leaderHost, leaderPort);
                logger.info("Worker node joined cluster successfully");
                logger.info("Node ID: {}", node.getId());
                logger.info("Election ID: {}", node.getElectionId());
                logger.info("");
                logger.info("Waiting for Leader heartbeats...");
                logger.info("   If Leader dies, this node will participate in election");
                logger.info("");
            }
            
            // Keep running until Ctrl+C
            logger.info("Node is running. Press Ctrl+C to stop.");
            logger.info("");
            
            // Add shutdown hook for graceful termination
            final NodeImpl nodeRef = node; // Final reference for lambda
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("");
                    logger.info("Shutting down node {}", nodeRef.getId());
                    nodeRef.shutdown();
                    logger.info("Node shutdown complete");
                } catch (Exception e) {
                    logger.error("Error during shutdown: {}", e.getMessage());
                }
            }));
            
            // Block main thread to keep node running
            Thread.currentThread().join();
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (Exception e) {
            logger.error("Test failed with exception", e);
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println();
        System.err.println("Usage:");
        System.err.println();
        System.err.println("  Start as Leader:");
        System.err.println("    mvn test-compile exec:java -Dexec.mainClass=com.hecaton.manual.election.TestThreeNodeElection -Dexec.args=\"--port 5001 --leader\"");
        System.err.println();
        System.err.println("  Start as Worker:");
        System.err.println("    mvn test-compile exec:java -Dexec.mainClass=com.hecaton.manual.election.TestThreeNodeElection -Dexec.args=\"--port 5002 --join localhost:5001\"");
        System.err.println();
    }
}

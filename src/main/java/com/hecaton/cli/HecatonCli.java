package com.hecaton.cli;

import com.hecaton.cli.commands.ClusterCommand;
import com.hecaton.cli.commands.JobCommand;
import com.hecaton.cli.commands.NodeCommand;
import com.hecaton.cli.commands.VersionCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Hecaton CLI - Main entry point o Command dispatcher
 * 
 * Root command with global options and subcommands for managing
 * distributed computing nodes, jobs, and cluster operations.
 */
@Command(
    name = "hecaton",
    description = "Hecaton - P2P Distributed Computing System",
    version = "Hecaton v1.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = { // these are the subcommands, each implemented in their own class
        NodeCommand.class, 
        JobCommand.class,
        ClusterCommand.class,
        VersionCommand.class
    }
)
public class HecatonCli implements Callable<Integer> { // implements Callable<Integer> to return exit code
    // because every program in every OS returns an exit code when terminating
    
    // This option are global and apply to all subcommands
    // so they can be read from any subcommand if needed
    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output (DEBUG logging)"
    )
    private boolean verbose;
    
    @Option(
        names = {"--no-color"},
        description = "Disable colored output"
    )
    private boolean noColor;
    
    public static void main(String[] args) {
        // This is the main class, which initializes Picocli and dispatches commands
        int exitCode = new CommandLine(new HecatonCli()).execute(args); // execute the command with the provided args
        System.exit(exitCode); // exit with the returned exit code
    }
    
    @Override
    public Integer call() {
        // If no subcommand is provided, show help (aka.: display usage information)
        new CommandLine(this).usage(System.out);
        return 0;
    }
    
    public boolean isVerbose() {
        return verbose;
    }
    
    public boolean isNoColor() {
        return noColor;
    }
}

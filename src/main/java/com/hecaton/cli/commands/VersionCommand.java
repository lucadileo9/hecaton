package com.hecaton.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * This is the version command that displays version information
 * about the Hecaton system and its components.
 * Aka.: nothing special, just a simple command to show version info.
 */
@Command(name = "version", description = "Show version information")
public class VersionCommand implements Callable<Integer> {
    
    @Override
    public Integer call() {
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   Hecaton - Distributed Computing System      ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Version: 1.0-SNAPSHOT");
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Architecture: " + System.getProperty("os.arch"));
        System.out.println();
        System.out.println("Components:");
        System.out.println("  - RMI: Built-in (Java)");
        System.out.println("  - CLI: Picocli 4.7.5");
        System.out.println("  - Logging: SLF4J + Logback");
        System.out.println();
        return 0;
    }
}

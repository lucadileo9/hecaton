package com.hecaton.node;

import java.io.Serializable;

/**
 * This class represents the capabilities of a compute node in terms of CPU, memory, OS, and Java version.
 * It provides methods to detect the current system's capabilities and retrieve them.
 * It is used for load balancing and resource management in distributed computing environments.
 * 
 * I use final class because the capabilities of a node should not change at runtime.
 * If the node's capabilities change, a new instance should be created via the detect() method
 */
public final class NodeCapabilities implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final int cpuCores;
    private final long totalMemoryMB;
    private final long availableMemoryMB;
    private final String osName;
    private final String osVersion;
    private final String javaVersion;
    // I'm not sure that all the fields are necessary, but they might be useful for future extensions.
    
    /**
     * Private constructor - use {@link #detect()} to create instances.
     */
    private NodeCapabilities(int cpuCores, long totalMemoryMB, long availableMemoryMB,
                            String osName, String osVersion, String javaVersion) {
        this.cpuCores = cpuCores;
        this.totalMemoryMB = totalMemoryMB;
        this.availableMemoryMB = availableMemoryMB;
        this.osName = osName;
        this.osVersion = osVersion;
        this.javaVersion = javaVersion;
    }
    
    /**
     * Detects the current node's capabilities and returns a NodeCapabilities instance.
     * @return Detected NodeCapabilities
     */
    public static NodeCapabilities detect() {
        Runtime runtime = Runtime.getRuntime();
        
        int cores = runtime.availableProcessors();
        long totalMB = runtime.maxMemory() / (1024 * 1024);
        long availableMB = runtime.freeMemory() / (1024 * 1024);
        String os = System.getProperty("os.name");
        String osVer = System.getProperty("os.version");
        String java = System.getProperty("java.version");
        
        return new NodeCapabilities(cores, totalMB, availableMB, os, osVer, java);
    }
    
    // ==================== Getters ====================
    
    /**
     * @return Number of available CPU cores
     */
    public int getCpuCores() {
        return cpuCores;
    }
    
    /**
     * @return Total memory of the node in MB
     */
    public long getTotalMemoryMB() {
        return totalMemoryMB;
    }
    
    /**
     * @return Available memory at the time of detection in MB
     */
    public long getAvailableMemoryMB() {
        return availableMemoryMB;
    }
    
    /**
     * @return Name of the operating system (e.g., "Windows 11", "Linux")
     */
    public String getOsName() {
        return osName;
    }
    
    /**
     * @return Version of the operating system
     */
    public String getOsVersion() {
        return osVersion;
    }
    
    /**
     * @return Version of Java (e.g., "21.0.1")
     */
    public String getJavaVersion() {
        return javaVersion;
    }
    
    // ==================== Utility ====================
    
    // For debugging purposes
    @Override
    public String toString() {
        return String.format("NodeCapabilities[cores=%d, memory=%dMB, os=%s %s, java=%s]",
            cpuCores, totalMemoryMB, osName, osVersion, javaVersion);
    }
    
    /**
     * Calculates a relative "weight" for this node based on CPU and memory.
     * Useful for WeightedSplitting.
     * 
     * @return normalized weight (cores * log2(memoryGB))
     */
    public double calculateWeight() {
        double memoryGB = totalMemoryMB / 1024.0;
        double memoryFactor = Math.max(1, Math.log(memoryGB + 1) / Math.log(2));
        return cpuCores * memoryFactor;
    }
}

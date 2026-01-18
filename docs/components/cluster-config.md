# ClusterConfig Component Documentation

The **ClusterConfig** is the centralized configuration component for cluster nodes. It encapsulates all strategic choices for both leader election and task execution, providing a clean, immutable configuration object using the Builder pattern.

---

## Overview

### Purpose

The ClusterConfig component (`com.hecaton.node.ClusterConfig`) provides:
- **Centralized Configuration**: Single source of truth for cluster behavior
- **Strategy Selection**: Election algorithm + task splitting/assignment strategies
- **Immutability**: Thread-safe, serializable configuration object
- **Builder Pattern**: Fluent API with validation and sensible defaults
- **Factory Method**: Creates election strategy instances on demand

### Key Classes

| Class | Purpose |
|-------|---------|
| `ClusterConfig` | Immutable configuration container, serializable for RMI |
| `ClusterConfig.Builder` | Fluent builder with validation and defaults |
| `Algorithm` | Enum for election algorithm selection (BULLY, RAFT, RING) |

---

## Architecture

### Design Patterns

**Builder Pattern + Factory Pattern + Strategy Pattern**:

```
┌─────────────────────────────────────────────────────────┐
│                ClusterConfig.Builder                     │
│                                                          │
│  • electionAlgorithm(Algorithm) → Builder               │
│  • splittingStrategy(SplittingStrategy) → Builder       │
│  • assignmentStrategy(AssignmentStrategy) → Builder     │
│  • build() → ClusterConfig                              │
│                                                          │
│  Defaults:                                               │
│    - BULLY election                                      │
│    - UniformSplitting                                    │
│    - RoundRobinAssignment                                │
└─────────────────────────────────────────────────────────┘
                          ↓
            ┌─────────────────────────────┐
            │   ClusterConfig             │
            │   (Serializable)            │
            ├─────────────────────────────┤
            │ - electionAlgorithm         │
            │ - splittingStrategy         │
            │ - assignmentStrategy        │
            ├─────────────────────────────┤
            │ + createElectionStrategy()  │
            │ + getElectionAlgorithm()    │
            │ + getSplittingStrategy()    │
            │ + getAssignmentStrategy()   │
            └─────────────────────────────┘
                          ↓
            ┌─────────────────────────────┐
            │   ElectionStrategy          │
            │   (created on demand)       │
            ├─────────────────────────────┤
            │ • BullyElection             │
            │ • RaftElection (future)     │
            │ • RingElection (future)     │
            └─────────────────────────────┘
```

### Why Centralized Configuration?

**Single Responsibility**: Separates configuration concerns from node behavior:

```java
// Clean separation of concerns
ClusterConfig config = new ClusterConfig.Builder()
    .electionAlgorithm(Algorithm.BULLY)
    .splittingStrategy(new UniformSplitting())
    .assignmentStrategy(new RoundRobinAssignment())
    .build();

NodeImpl node = new NodeImpl("localhost", 5001, config);
```

**Alternative (without ClusterConfig)**: Node constructor would need multiple strategy parameters, leading to:
- Long parameter lists
- No validation at construction time
- Difficult to add new strategies
- No serialization support

---

## Algorithm Enum

### Available Algorithms

| Algorithm | Description | Status | Complexity |
|-----------|-------------|--------|------------|
| `BULLY` | Highest ID wins election | ✅ Implemented | O(n²) messages |
| `RAFT` | Consensus algorithm with log replication | 🔮 Future | O(n) messages |
| `RING` | Election message circulates in ring topology | 🔮 Future | O(n) messages |

### Algorithm Selection Guide

**BULLY** (Current Implementation):
- **Best for**: Small clusters (3-20 nodes)
- **Pros**: Simple, deterministic, well-tested
- **Cons**: High message overhead (n² messages)
- **Use when**: Cluster size is manageable, simplicity is priority

**RAFT** (Future):
- **Best for**: Larger clusters requiring consensus (20-100 nodes)
- **Pros**: Log replication, proven in production systems
- **Cons**: More complex implementation
- **Use when**: Need strong consistency guarantees

**RING** (Future):
- **Best for**: Medium clusters with stable topology (10-50 nodes)
- **Pros**: Linear message complexity O(n)
- **Cons**: Token loss scenarios require handling
- **Use when**: Want predictable message overhead

---

## Usage Examples

### Basic Configuration (Using Defaults)

```java
// Uses default strategies:
// - BULLY election
// - UniformSplitting
// - RoundRobinAssignment
ClusterConfig config = new ClusterConfig.Builder().build();

NodeImpl node = new NodeImpl("localhost", 5001, config);
```

### Custom Election Algorithm

```java
ClusterConfig config = new ClusterConfig.Builder()
    .electionAlgorithm(Algorithm.BULLY)
    .build();

NodeImpl node = new NodeImpl("localhost", 5001, config);
```

### Full Custom Configuration

```java
ClusterConfig config = new ClusterConfig.Builder()
    .electionAlgorithm(Algorithm.BULLY)
    .splittingStrategy(new AdaptiveSplitting())  // Future
    .assignmentStrategy(new LoadAwareAssignment())  // Future
    .build();

NodeImpl leader = new NodeImpl("localhost", 5001, config);
NodeImpl worker = new NodeImpl("localhost", 5002, config);
```

### Creating Election Strategy

ClusterConfig acts as a **factory** for election strategy instances:

```java
// Inside NodeImpl constructor or initialization
ElectionStrategy electionStrategy = config.createElectionStrategy(
    this,                          // selfNode reference
    this.nodeIdValue,              // electionId (timestamp-based)
    this::getClusterNodesCache     // Supplier for fresh cluster cache
);

// ElectionStrategy is ready to use
electionStrategy.startElection();  // Triggers election process
```

---

## Integration Points

### NodeImpl Constructor

```java
public NodeImpl(String host, int port, ClusterConfig config) {
    // ... RMI setup ...
    
    // Store configuration
    this.clusterConfig = config;
    
    // Create election strategy using factory method
    this.electionStrategy = config.createElectionStrategy(
        this, 
        this.nodeIdValue, 
        this::getClusterNodesCache
    );
    
    // Access task strategies
    this.splittingStrategy = config.getSplittingStrategy();
    this.assignmentStrategy = config.getAssignmentStrategy();
}
```

### Serialization (RMI Compatibility)

ClusterConfig is **serializable**, enabling transmission over RMI:

```java
// Example: Worker receives Leader's configuration
public class LeaderServiceImpl implements LeaderService {
    public ClusterConfig getClusterConfig() throws RemoteException {
        return this.config;  // Automatically serialized by RMI
    }
}

// Worker can align its configuration with Leader's
ClusterConfig leaderConfig = leaderRef.getClusterConfig();
```

---

## Builder Pattern Details

### Validation Strategy

The Builder enforces **fail-fast** validation using `Objects.requireNonNull`:

```java
public Builder electionAlgorithm(Algorithm algorithm) {
    this.electionAlgorithm = Objects.requireNonNull(
        algorithm, 
        "algorithm cannot be null"
    );
    return this;
}
```

**Benefits**:
- Catches null values at configuration time, not execution time
- Clear error messages indicate which parameter is invalid
- No partially-constructed invalid objects possible

### Default Values Strategy

All fields have **sensible defaults** for zero-configuration usage:

```java
private Algorithm electionAlgorithm = Algorithm.BULLY;
private SplittingStrategy splittingStrategy = new UniformSplitting();
private AssignmentStrategy assignmentStrategy = new RoundRobinAssignment();
```

**Philosophy**: "Convention over Configuration"
- New users can start immediately with `new Builder().build()`
- Advanced users override only what they need
- Defaults represent best practices for typical use cases

---

## Factory Method Pattern

### createElectionStrategy() Design

The factory method encapsulates **complex instantiation logic**:

```java
public ElectionStrategy createElectionStrategy(
        NodeImpl selfNode, 
        long electionId, 
        Supplier<List<NodeInfo>> clusterNodesSupplier) {
    
    switch (electionAlgorithm) {
        case BULLY:
            return new BullyElection(selfNode, electionId, clusterNodesSupplier);
        
        case RAFT:
            throw new UnsupportedOperationException("Raft not yet implemented");
        
        case RING:
            throw new UnsupportedOperationException("Ring not yet implemented");
        
        default:
            throw new IllegalArgumentException("Unknown algorithm: " + electionAlgorithm);
    }
}
```

**Why Factory Method?**:
- **Encapsulation**: NodeImpl doesn't need to know concrete strategy classes
- **Extensibility**: Adding new algorithms only requires updating ClusterConfig
- **Type Safety**: Returns ElectionStrategy interface, hiding implementation
- **Consistency**: Ensures all required parameters are passed correctly

**Alternative (without factory)**:
```java
// Bad: NodeImpl tightly coupled to concrete strategies
if (config.getAlgorithm() == Algorithm.BULLY) {
    this.election = new BullyElection(this, nodeId, supplier);
} else if (config.getAlgorithm() == Algorithm.RAFT) {
    this.election = new RaftElection(this, nodeId, supplier);
}
// Violates Open/Closed Principle
```

---

## Thread Safety

### Immutability Guarantees

ClusterConfig is **immutable after construction**:

1. **Private Constructor**: Only Builder can create instances
2. **Final Fields**: All fields are `final`, preventing reassignment
3. **No Setters**: No methods modify internal state
4. **Serialization Safe**: `serialVersionUID` for version control

**Benefits**:
- Can be safely shared across threads without synchronization
- Can be passed to multiple nodes without defensive copying
- RMI serialization doesn't break immutability

### Builder Thread Safety

Builder is **NOT thread-safe** (by design):
- Each builder instance should be used by single thread
- Multiple builders can operate in parallel (creating different configs)

**Usage Pattern**:
```java
// ✅ GOOD: Each thread creates its own builder
Thread t1 = new Thread(() -> {
    ClusterConfig c1 = new ClusterConfig.Builder()
        .electionAlgorithm(Algorithm.BULLY)
        .build();
});

Thread t2 = new Thread(() -> {
    ClusterConfig c2 = new ClusterConfig.Builder()
        .electionAlgorithm(Algorithm.RAFT)
        .build();
});

// ❌ BAD: Sharing builder across threads (undefined behavior)
ClusterConfig.Builder sharedBuilder = new ClusterConfig.Builder();
Thread t1 = new Thread(() -> sharedBuilder.electionAlgorithm(Algorithm.BULLY));
Thread t2 = new Thread(() -> sharedBuilder.electionAlgorithm(Algorithm.RAFT));
```

---

## Future Enhancements

### Planned Features

1. **Configuration Validation** (Phase 3):
   ```java
   public Builder validate() {
       // Check algorithm compatibility with cluster size
       // Check strategy compatibility with task types
       return this;
   }
   ```

2. **Configuration Profiles** (Phase 4):
   ```java
   // Predefined configurations for common scenarios
   ClusterConfig.Profile.SMALL_CLUSTER  // 3-10 nodes, BULLY
   ClusterConfig.Profile.LARGE_CLUSTER  // 50+ nodes, RAFT
   ClusterConfig.Profile.LOW_LATENCY    // Optimized for speed
   ```

3. **Additional parameters** (Phase 2):
   ```java
   // Timeout settings, retry policies, logging levels
   public Builder electionTimeout(Duration timeout) { ... }
   public Builder maxRetries(int retries) { ... }
   ```

4. **Configuration Persistence**:
   ```java
   // Save/load from file
   config.saveTo("hecaton.conf");
   ClusterConfig loaded = ClusterConfig.loadFrom("hecaton.conf");
   ```

---

## Related Documentation

- [Election Component](./election.md) - Election strategy implementations
- [Node Component](./node.md) - How NodeImpl uses ClusterConfig
- [Architecture Overview](../architecture/overview.md) - System-wide configuration flow

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-18 | Initial implementation with BULLY algorithm support |
| Future | TBD | Add RAFT and RING algorithm implementations |
| Future | TBD | Add configuration validation and profiles |

# ClusterMembershipService Component

## Overview

**ClusterMembershipService** (formerly `membershipService`) is the component responsible for managing cluster membership in the Hecaton system. It maintains a thread-safe list of all active Worker nodes registered with the Leader.

**Important distinction**: This service manages the **membership list** (which nodes are in the cluster), not **Leader discovery** (how Workers find the Leader initially). See [Discovery Overview](../discovery/overview.md) for Leader discovery mechanisms.

### Responsibilities

* **Node Registration**: Adding new Workers to the cluster when they connect.
* **Node Removal**: Removing Workers from the cluster (due to death or voluntary disconnection).
* **Concurrent Access**: Providing thread-safe access to the list of active nodes.
* **Duplicate Detection**: Preventing multiple registrations of the same node.

### Architectural Positioning

```
┌─────────────────────────────────────┐
│         Leader Node                 │
│  ┌───────────────────────────────┐  │
│  │     NodeImpl (Leader)         │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  membershipService       │  │  │
│  │  │                         │  │  │
│  │  │  activeNodes:           │  │  │
│  │  │   CopyOnWriteArrayList  │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
          ▲         ▲      ▲
          │         │      │
     RMI calls (registerNode)
          │         │      │
    ┌─────┘    ┌────┘      ┘───┐
    │          │               │
Worker 1    Worker 2       Worker 3

```

**membershipService** is an internal component of the Leader Node—it is not exposed via RMI. Workers interact with discovery only indirectly via the `registerNode()` RMI calls of the LeaderService.

## Architecture

### Design Decisions

#### 1. CopyOnWriteArrayList for Thread Safety

```java
private final List<NodeService> activeNodes = new CopyOnWriteArrayList<>();

```

**Rationale:**

* **Read-heavy workload**: The list is read frequently (e.g., HeartbeatMonitor iterating over Workers, task scheduler checking available nodes).
* **Infrequent writes**: Modifications (add/remove) are rare events compared to reads.
* **No external synchronization needed**: Snapshot-based iterators guarantee consistency without `synchronized`.
* **Fail-safe iterations**: Iterations do not throw `ConcurrentModificationException`.

**Trade-off:**

* ✅ **Pro**: Excellent performance for read-heavy scenarios, zero contention on readers.
* ⚠️ **Con**: Write operations (add/remove) are expensive—acceptable given their rarity.

#### 2. NodeService as Element Type

The list contains `NodeService` references (RMI stubs), not serializable objects:

```java
public void addNode(NodeService node) throws RemoteException { ... }

```

**Rationale:**

* Allows direct RMI calls on nodes (e.g., `node.ping()`, `node.getId()`).
* Avoids data duplication (NodeService already contains all necessary info).
* Uniform interface for Worker and Leader (both implement NodeService).

#### 3. Duplicate Detection Based on ID

```java
public void addNode(NodeService node) throws RemoteException {
    String nodeId = node.getId();
    
    for (NodeService existing : activeNodes) {
        String existingId = getNodeIdSafe(existing);
        if (nodeId.equals(existingId)) {
            log.warn("Node {} already registered - skipping duplicate", nodeId);
            return;
        }
    }
    
    activeNodes.add(node);
}

```

**Rationale:**

* Node ID is immutable and unique (`node-<host>-<port>-<timestamp>`).
* Prevents double registration if a Worker retries connection.
* Safe extraction with `getNodeIdSafe()` handles RemoteException.

## API Reference

### Public Methods

#### `addNode(NodeService node)`

```java
public void addNode(NodeService node) throws RemoteException
```

Adds a Worker node to the cluster's active list.

**Parameters**:
- `node` - RMI stub of the Worker to register

**Throws**:
- `RemoteException` - If `node.getId()` RMI call fails during duplicate check

**Behavior**:
- **Duplicate Detection**: Iterates existing nodes comparing IDs - skips if already present
- **Thread Safety**: Write operation creates new array copy (CopyOnWriteArrayList semantics)
- **Idempotent**: Safe to call multiple times with same node (logs warning, no error)
---

#### `removeNode(NodeService node)`

```java
public boolean removeNode(NodeService node)
```

Removes a node from the active list (typically after death detection).

**Parameters**:
- `node` - RMI stub of the node to remove

**Returns**:
- `true` if node was present and removed
- `false` if node not found in list

**Use Cases**:
- HeartbeatMonitor detected node death
- Graceful Worker shutdown (future - requires `unregisterNode()` in LeaderService)
---

#### `removeNodeById(String nodeId)`

```java
public boolean removeNodeById(String nodeId)
```

Removes a node by ID when RMI stub is no longer usable (e.g., after network partition).

**Parameters**:
- `nodeId` - Unique node identifier (format: `node-<host>-<port>-<timestamp>`)

**Returns**:
- `true` if node with matching ID was found and removed
- `false` if no matching node found

**Advantages vs `removeNode()`**:
- Works even if RMI stub is stale/expired
- No `RemoteException` risk (uses cached `getNodeIdSafe()`)

---

#### `getActiveNodes()`

```java
public List<NodeService> getActiveNodes()
```

Returns a snapshot of all currently registered nodes.

**Returns**:
- **Unmodifiable List** of active `NodeService` stubs (via `Collections.unmodifiableList()`)

**Use Cases**:
- Iterating nodes for broadcasting messages
- Counting cluster size
- Selecting nodes for task assignment (future)

**Important**: Returned list is **read-only** - calling `add()` or `remove()` throws `UnsupportedOperationException`.

---

#### `hasNode(NodeService node)`

```java
public boolean hasNode(NodeService node)
```

Checks if a specific node is currently registered.

**Parameters**:
- `node` - RMI stub to search for

**Returns**:
- `true` if node present in active list (via `List.contains()`)
- `false` otherwise

**Use Cases**:
- Pre-registration validation
- Debugging/diagnostic tools
---

### Internal Methods

#### `getNodeIdSafe(NodeService node)`

```java
private String getNodeIdSafe(NodeService node)
```

Defensive wrapper for extracting node ID from potentially dead RMI stubs.

**Parameters**:
- `node` - NodeService stub (may be dead/unreachable)

**Returns**:
- Node's unique ID if `node.getId()` succeeds
- `"unknown-node"` placeholder if `RemoteException` occurs

## Usage Examples

### Scenario 1: Worker Registration

**Context**: A Worker connects to the Leader and registers itself.

```java
// NodeImpl (Leader) - registerNode() implementation
@Override
public void registerNode(NodeService node) throws RemoteException {
    String nodeId = node.getId();
    log.info("Registration request from node: {}", nodeId);
    
    // membershipService handles duplicate detection
    membershipService.addNode(node);
    
    int totalNodes = membershipService.getActiveNodes().size();
    log.info("[OK] New node registered: {} (Total: {} nodes)", nodeId, totalNodes);
}

```

**Flow**:

1. Worker calls `leader.registerNode(workerStub)`.
2. Leader delegates to `membershipService.addNode()`.
3. membershipService checks for duplicates (via node ID).
4. If new, it adds it to `activeNodes`.
5. Leader confirms registration.

### Scenario 2: Node Removal (Death Detection)

**Context**: HeartbeatMonitor detects that a Worker has died.

```java
// NodeImpl (Leader) - onWorkerDied() callback
private void onWorkerDied(NodeService deadWorker) {
    String deadNodeId = membershipService.getNodeIdSafe(deadWorker);
    log.error("[ALERT] WORKER {} IS DEAD!", deadNodeId);
    
    // Remove from active nodes
    boolean removed = membershipService.removeNode(deadWorker);
    
    if (removed) {
        log.info("Dead worker removed from cluster (remaining: {})", 
                 membershipService.getActiveNodes().size());
        // TODO Phase 3: Reassign tasks from dead worker
    }
}

```

**Flow**:

1. HeartbeatMonitor detects 3 consecutive failed pings.
2. Invokes `onWorkerDied(deadWorkerStub)` callback.
3. Leader removes node from membershipService.
4. Future: Reassign tasks from the dead Worker to other nodes.

### Scenario 3: Iterating Active Nodes (Thread-Safe)

**Context**: Leader iterates over Workers for some operation (e.g., broadcasting, health check).

```java
// Safe iteration - snapshot semantics
List<NodeService> nodes = membershipService.getActiveNodes();

for (NodeService worker : nodes) {
    try {
        // RMI call on each worker
        boolean alive = worker.ping();
        log.debug("Worker {} is alive: {}", worker.getId(), alive);
    } catch (RemoteException e) {
        // Worker unreachable - will be detected by HeartbeatMonitor
        log.warn("Failed to ping worker: {}", e.getMessage());
    }
}

```

**Thread Safety Guarantee**:

* Iterator uses a snapshot of the list at the time of the `getActiveNodes()` call.
* If a Worker is added/removed during iteration, the iterator does not throw an exception.
* Iterator reflects the state at time T0, not subsequent modifications.

## Configuration

### Constants

No explicit configuration—membershipService uses `CopyOnWriteArrayList` defaults.

**Initial Capacity**: 10 elements (ArrayList default)
**Growth Strategy**: 1.5x expansion when capacity is exceeded

### Performance Characteristics

| Operation | Time Complexity | Notes |
| --- | --- | --- |
| `addNode()` | **O(n)** | Duplicate check + array copy |
| `removeNode()` | **O(n)** | Linear search + array copy |
| `getActiveNodes()` | **O(1)** | Returns reference, no copy |
| `hasNode()` | **O(n)** | Linear search via equals |
| Iteration | **O(n)** | Lock-free, no synchronization |

**Scalability**:

* Small Clusters (<100 nodes): Excellent—negligible overhead.
* Large Clusters (>1000 nodes): Write operations might slow down (acceptable for rare add/remove events).

## Error Handling

### RemoteException in getNodeIdSafe()

```java
private String getNodeIdSafe(NodeService node) {
    try {
        return node.getId();
    } catch (RemoteException e) {
        log.warn("Failed to get node ID - node might be dead: {}", e.getMessage());
        return "unknown-node";
    }
}

```

**Strategy**: Graceful degradation

* Remote call failed → Log warning + return placeholder.
* Caller can proceed (e.g., logging "unknown-node" instead of crashing).
* HeartbeatMonitor will detect node death autonomously.

### Duplicate Detection Fallback

If `getId()` fails during duplicate check:

* `getNodeIdSafe()` returns `"unknown-node"`.
* Duplicate check fails (unknown != real ID).
* Node is added anyway (safe default).
* Safer alternative: crash with exception.

## Integration with Other Components

### NodeImpl (Leader Mode)

NodeImpl uses membershipService to implement LeaderService:

```java
public class NodeImpl extends UnicastRemoteObject implements NodeService, LeaderService {
    private final membershipService membershipService;  // Leader only
    
    public void startAsLeader() throws RemoteException {
        this.isLeader = true;
        this.membershipService = new membershipService();
        
        myRegistry.rebind("leader", this);
        log.info("[OK] Node {} started as LEADER", nodeId);
    }
    
    @Override
    public void registerNode(NodeService node) throws RemoteException {
        membershipService.addNode(node);
        // TODO Phase 3: Start HeartbeatMonitor for this worker
    }
}
```

## Testing

This is a simple component—most functionality is verified via integration tests in the Leader/Worker context. So we don't need dedicated tests here.

## Related Documentation

* [NodeImpl Component](https://www.google.com/search?q=node.md) - membershipService integration in the Leader.
* [HeartbeatMonitor Component](https://www.google.com/search?q=heartbeat.md) - Node death detection.
* [Testing Guide](https://www.google.com/search?q=../testing/README.md) - Testing procedures.

## Code Location

**Source File**: `src/main/java/com/hecaton/discovery/membershipService.java`
**Test File**: N/A (functionality tested via integration tests in `src/test/java/com/hecaton/manual/`)
**Config**: `src/main/resources/logback.xml` (logging levels)


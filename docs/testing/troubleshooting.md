# Troubleshooting Guide

Common issues encountered during Hecaton development and their solutions.

---
## Automatic Cache Refresh via HeartbeatMonitor

### Problem

Even with Supplier Pattern implemented, the cluster cache still becomes stale because it's only populated once during `joinCluster()`. Workers don't know when new nodes join the cluster after them.

**Scenario:**
1. Worker A joins at T=0 → cache = [Leader, A]
2. Worker B joins at T=10 → cache = [Leader, A, B]
3. Worker C joins at T=20 → cache = [Leader, A, B, C]
4. **Worker A's cache still = [Leader, A]** (never updated!)
5. **Worker B's cache still = [Leader, A, B]** (doesn't know C exists!)

**Why manual refresh isn't enough:**
- Workers don't receive notifications when cluster membership changes
- No event-driven mechanism for cache updates
- Relying on election time to fetch fresh cache is too late (Leader already dead!)

### Solution: Periodic Refresh via Heartbeat

**Design choice:** Piggyback cache refresh on existing heartbeat mechanism.

**Rationale:**
- HeartbeatMonitor already pings Leader every 5 seconds
- Minimal overhead to fetch cluster list every 8 pings (~40 seconds)
- Automatic - no manual intervention needed
- Cache stays fresh for election without waiting for failure

**Implementation:**

#### 1. Add `CacheRefreshCallback` interface to `HeartbeatMonitor`:

```java
// HeartbeatMonitor.java
public interface CacheRefreshCallback {
    /**
     * Called every CACHE_REFRESH_INTERVAL heartbeats to update cluster nodes cache.
     */
    void refreshCache();
}
```

#### 2. Add heartbeat counter and refresh interval constant:

```java
private static final int CACHE_REFRESH_INTERVAL = 10;  // Every 10 heartbeats (~50s)

private int heartbeatCount = 0;  // Total heartbeats sent
private final CacheRefreshCallback cacheRefreshCallback;  // Optional cache refresh
```

#### 3. Update constructor to accept optional callback:

```java
public HeartbeatMonitor(
        NodeService targetNode, 
        NodeFailureCallback callback,
        CacheRefreshCallback cacheRefreshCallback,  // ← New parameter (nullable)
        String monitorName) {
    
    this.targetNode = targetNode;
    this.callback = callback;
    this.cacheRefreshCallback = cacheRefreshCallback;  // Can be null
    this.monitorName = monitorName;
    // ...
}
```

#### 4. Trigger refresh every 8 successful heartbeats:

```java
private void sendHeartbeat() {
    try {
        boolean alive = targetNode.ping();
        
        if (alive) {
            missedHeartbeats = 0;
            heartbeatCount++;  // ← Increment counter
            
            log.debug("[{}] Heartbeat OK (count: {})", monitorName, heartbeatCount);
            
            // Periodic cache refresh
            if (cacheRefreshCallback != null && heartbeatCount % CACHE_REFRESH_INTERVAL == 0) {
                log.debug("[{}] Triggering cache refresh (heartbeat #{})", 
                    monitorName, heartbeatCount);
                try {
                    cacheRefreshCallback.refreshCache();  // ← Call callback
                } catch (Exception e) {
                    log.warn("[{}] Cache refresh failed: {}", monitorName, e.getMessage());
                    // Continue anyway - old cache better than crash
                }
            }
        }
        // ...
    }
}
```

#### 5. Implement `updateClusterCache()` in `NodeImpl`:

```java
/**
 * Refreshes the cluster nodes cache by fetching latest list from Leader.
 * Called periodically by HeartbeatMonitor to keep cache up-to-date.
 * Only runs for Worker nodes (Leader has no cache).
 */
private void updateClusterCache() {
    if (isLeader || leaderNode == null) {
        return;  // Leaders don't need cache, only Workers do
    }
    
    try {
        LeaderService leader = (LeaderService) leaderNode;
        List<NodeInfo> freshNodes = leader.getClusterNodes();
        
        // Update cache with fresh data (clear + addAll for thread safety)
        this.clusterNodesCache.clear();
        this.clusterNodesCache.addAll(freshNodes);
        
        log.debug("Cluster cache refreshed: {} nodes", clusterNodesCache.size());
        
    } catch (RemoteException e) {
        log.warn("Failed to refresh cluster cache: {}", e.getMessage());
        // Keep old cache - better than nothing
    }
}
```

#### 6. Pass callback when creating `HeartbeatMonitor`:

```java
// NodeImpl.joinCluster()
leaderMonitor = new HeartbeatMonitor(
    leaderNode, 
    this::onLeaderDied,          // Failure callback
    this::updateClusterCache,    // ← Cache refresh callback
    "Leader Monitor"
);
leaderMonitor.start();
log.info("[OK] Heartbeat monitoring started (cache refresh enabled)");
```

**Key properties:**
- ✅ **All Workers eventually get fresh cache** (within 40 seconds)
- ✅ **No split-brain:** Cache updated before Leader dies (most likely)
- ✅ **Minimal overhead:** 1 RMI call per 8 heartbeats (vs 1 per heartbeat)
- ✅ **Fault-tolerant:** Refresh failure doesn't kill heartbeat
- ✅ **Automatic:** No manual intervention needed

**Alternative rejected: Event-driven updates**
- ❌ Requires Leader to broadcast membership changes to all Workers
- ❌ More complex (pub/sub pattern, Worker callbacks)
- ❌ Overkill for educational project
- ❌ Periodic refresh simpler and "good enough"
---

## Cache Staleness: Split-Brain Election Bug

### Problem

After implementing Bully Election (Phase 2), testing with 3 nodes revealed a critical bug: when the Leader dies, **multiple Workers elect themselves as Leader simultaneously**, creating a split-brain scenario.

**Test scenario:**
1. Start Leader on port 5001
2. Worker A joins on port 5002 (cache = [Leader, A])
3. Worker B joins on port 5003 (cache = [Leader, B])
4. Kill Leader process (Ctrl+C)
5. **EXPECTED:** Only Worker B (highest ID) becomes Leader
6. **ACTUAL:** Both A and B think they're the only Worker and elect themselves!

### Root Cause

**Cache never updates**. So each Worker has an outdated view of the cluster nodes.

**Why this breaks:**
1. Worker A joins at T=0 → fetches cache = [Leader, A]
2. Worker B joins at T=5 → fetches cache = [Leader, A, B]
3. **Worker A's cache NEVER UPDATED** (still thinks only it and Leader exist!)
4. Leader dies → A thinks no higher nodes exist → elects itself
5. Leader dies → B thinks only it exists (A in cache has lower ID) → elects itself
6. **Result:** Two Leaders! (Split-brain)

### Solution: Supplier Pattern for Lazy Cache Evaluation

**Design choice:** Instead of pushing stale cache to the strategy, let the strategy **pull fresh cache** when needed.

**Implementation:**

#### 1. Replace `List<NodeInfo>` with `Supplier<List<NodeInfo>>` in `BullyElection`:

```java
// BEFORE: Mutable cache field
private List<NodeInfo> clusterNodes;

public BullyElection(NodeImpl selfNode, long electionId, List<NodeInfo> clusterNodes) {
    this.clusterNodes = clusterNodes;  // ← Snapshot at CONSTRUCTION time
}

// AFTER: Lazy supplier
private final Supplier<List<NodeInfo>> clusterNodesSupplier;

public BullyElection(NodeImpl selfNode, long electionId, Supplier<List<NodeInfo>> supplier) {
    this.clusterNodesSupplier = supplier;  // ← Lambda, not data!
}
```

#### 2. Fetch fresh cache in `startElection()`:

```java
@Override
public void startElection() {
    // Get fresh snapshot from supplier (lazy evaluation)
    List<NodeInfo> clusterNodes = clusterNodesSupplier.get();  // ← FRESH DATA!
    
    List<NodeInfo> higherNodes = clusterNodes.stream()
        .filter(node -> node.getElectionId() > selfElectionId)
        .collect(Collectors.toList());
    // ... rest of election logic ...
}
```


#### 3. Update `ElectionStrategyFactory`:

```java
public static ElectionStrategy create(
        Algorithm algorithm,
        NodeImpl selfNode,
        long electionId,
        Supplier<List<NodeInfo>> clusterNodesSupplier) {  // ← Supplier parameter
    
    switch (algorithm) {
        case BULLY:
            return new BullyElection(selfNode, electionId, clusterNodesSupplier);
        // ...
    }
}
```

### Why Supplier Pattern Wins

**Advantages:**
- ✅ **Lazy evaluation:** Cache fetched ONLY when election starts (not at construction)
- ✅ **Clean interface:** No setter methods in `ElectionStrategy`
- ✅ **Immutable strategy:** `clusterNodesSupplier` is `final`, no state mutations
- ✅ **Fresh data guarantee:** `clusterNodesSupplier.get()` always returns current cache reference
- ✅ **Testability:** Easy to inject mock suppliers: `() -> Arrays.asList(...)`
- ✅ **Functional style:** Idiomatic Java 8+ pattern

**Trade-offs accepted:**
- ⚠️ **Cache still needs periodic refresh** - Supplier only provides fresh *reference*, not fresh *data*
- ⚠️ **Requires Java 8+** - Uses `java.util.function.Supplier<T>` (project already uses Java 17)

**Alternatives considered:**
- ❌ **Callback method:** `selfNode.getClusterNodesForElection()` → Couples strategy to NodeImpl method names
- ❌ **Shared reference:** Pass `CopyOnWriteArrayList` → Harder to test, less explicit intent

### Related Fix: Periodic Cache Refresh

**Important:** Supplier Pattern solves the *access* problem (how strategy gets cache), but doesn't solve the *staleness* problem (cache still never updates).
---


## RemoteException occurred in server thread;
### Problem

When starting a the registration test, the test fails.

**Error message:**
```
TEST FAILED: RemoteException occurred in server thread; nested exception is: 
        java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect

java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect
        at java.rmi/sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:392)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:200)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:712)
        at java.rmi/sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:587)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:828)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:705)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:399)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:704)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        at java.base/java.lang.Thread.run(Thread.java:842)
        at java.rmi/sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:304)
        at java.rmi/sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:280)
        at java.rmi/sun.rmi.server.UnicastRef.invoke(UnicastRef.java:165)
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:215) 
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:160)
        at jdk.proxy3/jdk.proxy3.$Proxy27.registerNode(Unknown Source)
        at com.hecaton.manual.rmi.TestNodeRegistration.main(TestNodeRegistration.java:59)
        at org.codehaus.mojo.exec.ExecJavaMojo$1.run(ExecJavaMojo.java:279)
        at java.base/java.lang.Thread.run(Thread.java:842)
Caused by: java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect
        at java.rmi/sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:626)
        at java.rmi/sun.rmi.transport.tcp.TCPChannel.createConnection(TCPChannel.java:217)
        at java.rmi/sun.rmi.transport.tcp.TCPChannel.newConnection(TCPChannel.java:204)
        at java.rmi/sun.rmi.server.UnicastRef.invoke(UnicastRef.java:133)
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:215) 
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:160)
        at jdk.proxy3/jdk.proxy3.$Proxy27.getId(Unknown Source)
        at com.hecaton.discovery.DiscoveryService.addNode(DiscoveryService.java:35)
        at com.hecaton.node.NodeImpl.registerNode(NodeImpl.java:129)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.base/java.lang.reflect.Method.invoke(Method.java:568)
        at java.rmi/sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:360)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:200)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:712)
        at java.rmi/sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:587)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:828)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:705)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:399)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:704)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        ... 1 more
Caused by: java.net.ConnectException: Connection refused: connect
        at java.base/sun.nio.ch.Net.connect0(Native Method)
        at java.base/sun.nio.ch.Net.connect(Net.java:579)
        at java.base/sun.nio.ch.Net.connect(Net.java:568)
        at java.base/sun.nio.ch.NioSocketImpl.connect(NioSocketImpl.java:593)
        at java.base/java.net.SocksSocketImpl.connect(SocksSocketImpl.java:327)
        at java.base/java.net.Socket.connect(Socket.java:633)
        at java.base/java.net.Socket.connect(Socket.java:583)
        at java.base/java.net.Socket.<init>(Socket.java:507)
        at java.base/java.net.Socket.<init>(Socket.java:287)
        at java.rmi/sun.rmi.transport.tcp.TCPDirectSocketFactory.createSocket(TCPDirectSocketFactory.java:40)
        at java.rmi/sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:620)
        ... 25 more
```

### Root Cause
I think it is a problem of duplicate RMI registries, maybe the leader already has one running on the xxx port, and when the worker tries to connect, it fails. In fact it happen only the SECOND time I run the test, because the first time everything works fine.

### Solution
I don't know exactly why, but killing all java processes before running the test again seems to solve the problem. Maybe becasue in this way I reset the leader state completely.

Since I don't think it is an important issue, I will not investigate further for now. Maybe thanks to the monitoring system we will never face this problem again.
I hope so for you, future Luca.

---




## Test Classes Not Found: exec:java Classpath Issue

### Problem

Running manual test classes with `mvn exec:java` fails:

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Error message:**
```
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.1.0:java
java.lang.ClassNotFoundException: com.hecaton.manual.node.TestLeaderNode
```

### Root Cause

The `exec-maven-plugin` by default uses **runtime classpath scope**, which includes only:
- `target/classes/` (compiled from `src/main/java/`)
- Runtime dependencies

It **does NOT include**:
- `target/test-classes/` (compiled from `src/test/java/`)

Since all manual tests are in `src/test/java/com/hecaton/manual/`, they are compiled to `target/test-classes/` and are invisible to exec:java.

### Solutions

#### ✅ Solution 1: Configure pom.xml (Applied)

Add `<classpathScope>test</classpathScope>` to exec-maven-plugin in `pom.xml`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <classpathScope>test</classpathScope>
    </configuration>
</plugin>
```

**Commands:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Pros:**
- ✅ Clean commands - no extra `-D` parameters needed
- ✅ Works for all test classes automatically
- ✅ Configured once, works forever
- ✅ Team members don't need to know about classpath scope

**Cons:**
- ⚠️ Must remember to run `test-compile` phase before `exec:java`

#### ❌ Solution 2: Manual -Dexec.classpathScope=test (Not Recommended)

Specify classpath scope on every command:

```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode' '-Dexec.classpathScope=test'
```

**Pros:**
- ✅ No pom.xml changes needed

**Cons:**
- ❌ Verbose - every command needs the extra parameter
- ❌ Easy to forget
- ❌ Error-prone (typos in classpathScope)

#### ❌ Solution 3: Move Tests to src/main/java (CRAZY)

Move manual tests from `src/test/java/` to `src/main/java/`.

**Pros:**
- ✅ Works with default classpath scope

**Cons:**
- ❌ Test code pollutes production code
- ❌ Test classes included in final JAR
- ❌ Violates Maven standard directory layout
- ❌ Confusing for new developers

### Why I Chose Solution 1

**Reason:** Balance between convenience and correctness.

- **Keeps test code separate**: Tests stay in `src/test/java/` (Maven convention)
- **Minimal cognitive load**: Developers just run `mvn test-compile exec:java`, no need to remember extra parameters
- **One-time setup**: Configured once in pom.xml, benefits everyone
- **Explicit test-compile**: The need to run `test-compile` is actually beneficial - it makes it clear we're running test code

**Trade-off accepted:** Needing `test-compile` before `exec:java` is a small price for keeping the codebase clean.

---

## PowerShell Quote Hell: Maven Commands Not Working

### Problem

Running Maven commands with `-Dexec.mainClass` fails in PowerShell:

```powershell
# ❌ FAILS with "Unknown lifecycle phase" error
mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode"
```

**Error message:**
```
[ERROR] Unknown lifecycle phase ".mainClass=com.hecaton.manual.node.TestLeaderNode"
```

### Root Cause

PowerShell interprets quotes differently than bash/cmd:
- **Curved quotes** `"` (from copy/paste): PowerShell sees them as text, not string delimiters
- **Straight double quotes** `"`: PowerShell expands variables inside them
- **Parameter parsing**: PowerShell tokenizes `-Dexec.mainClass="value"` incorrectly

### Solutions

#### ✅ Solution 1: Single Quotes (Recommended)

Use single quotes around the entire `-D` parameter:

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Why it works:** Single quotes in PowerShell preserve the string literally, no expansion or interpretation.

#### ✅ Solution 2: Escape with Nested Quotes

```powershell
mvn exec:java -D"exec.mainClass"="com.hecaton.manual.node.TestLeaderNode"
```

**Why it works:** Separates the parameter name and value into individually quoted parts.

#### ❌ Solution 3: Backtick Escaping (NOT Recommended)

```powershell
mvn exec:java -Dexec.mainClass=`"com.hecaton.manual.node.TestLeaderNode`"
```

**Why avoid:** Verbose, error-prone, hard to read.

### Related Fix: pom.xml Configuration

**Original problem:** Even with correct quotes, Maven executed `com.hecaton.cli.Main` instead of test classes.

**Root cause:** `pom.xml` had hardcoded mainClass in exec-maven-plugin, so it ignored `-Dexec.mainClass`.:

```xml
<!-- ❌ BEFORE: Hardcoded mainClass -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.hecaton.cli.Main</mainClass>
    </configuration>
</plugin>
```

**Solution:** Remove default mainClass to allow command-line override:

```xml
<!-- ✅ AFTER: Command-line mainClass only -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <!-- No default mainClass -->
</plugin>
```

---

## Circular Dependency: ElectionStrategy and NodeImpl

### Problem

When implementing Dependency Injection for `ElectionStrategy`, tests fail with NullPointerException:

```java
// ❌ FAILS: selfNode is null, selfElectionId is 0
BullyElection strategy = new BullyElection(null, 0, new ArrayList<>());
NodeImpl node = new NodeImpl("localhost", 5001, strategy);
```

**Error message:**
```
java.lang.NullPointerException: Cannot invoke "com.hecaton.node.NodeImpl.getId()" because "this.selfNode" is null
    at com.hecaton.election.bully.BullyElection.startElection(BullyElection.java:75)
```

**Additional symptoms:**
- Election always uses ID `0` instead of node's timestamp
- Cannot call methods on `selfNode` during election (it's null)
- Tests compile but fail at runtime when election starts

### Root Cause

Classic **chicken-and-egg dependency problem**:

1. To create `BullyElection`, you need a reference to `NodeImpl` (for `selfNode` parameter)
2. To create `NodeImpl` with Dependency Injection, you need an `ElectionStrategy` instance
3. But `NodeImpl` doesn't exist yet when creating `BullyElection`!

```java
// ❌ Circular dependency
BullyElection strategy = new BullyElection(
    node,           // ← node doesn't exist yet!
    nodeId,         // ← don't know the ID yet!
    clusterCache    // ← cache not initialized yet!
);

NodeImpl node = new NodeImpl("localhost", port, strategy);  // ← strategy is broken!
```

**Why passing `null` doesn't work:**
- `BullyElection` stores `selfNode` as `final` field
- When `startElection()` is called, it tries to use `selfNode.getId()` → NullPointerException
- Election ID remains `0` instead of using node's actual timestamp

### Solutions Considered

#### ❌ Option 1: NodeImpl Creates Strategy Internally (Rejected)

```java
public NodeImpl(String host, int port) throws RemoteException {
    // ... initialization ...
    this.electionStrategy = new BullyElection(this, nodeIdValue, clusterNodesCache);
}
```

**Pros:**
- Simple, no chicken-and-egg problem

**Cons:**
- Hardcodes `BullyElection` inside `NodeImpl`
- Violates Dependency Inversion Principle
- Cannot swap algorithms without modifying `NodeImpl`
- Not elegant

#### ❌ Option 2: Setter Post-Construction (Rejected)

```java
NodeImpl node = new NodeImpl("localhost", port);
BullyElection strategy = new BullyElection(node, node.getElectionId(), new ArrayList<>());
node.setElectionStrategy(strategy);
```

**Pros:**
- Resolves circular dependency
- Flexible

**Cons:**
- `NodeImpl` in invalid state until `setElectionStrategy()` is called
- Easy to forget calling setter → runtime errors
- More verbose
- Not elegant

#### ❌ Option 3: Multiple Static Factory Methods (Rejected)

```java
NodeImpl node = NodeImpl.createWithBullyElection("localhost", 5001);
NodeImpl node = NodeImpl.createWithRaftElection("localhost", 5001);
```

**Pros:**
- Clean API

**Cons:**
- Need one factory method per algorithm
- Scales poorly (10 algorithms = 10 methods)
- Still couples `NodeImpl` to concrete implementations
- Not elegant

#### ✅ Option 4: ElectionStrategyFactory Pattern (CHOSEN)

**Implementation:** Create a dedicated factory class that centralizes strategy creation logic.

**File: `ElectionStrategyFactory.java`**
```java
public class ElectionStrategyFactory {
    
    public enum Algorithm {
        BULLY,
        RAFT,   // Future
        RING    // Future
    }
    
    public static ElectionStrategy create(
            Algorithm algorithm,
            NodeImpl selfNode, 
            long electionId, 
            List<NodeInfo> clusterNodes) {
        
        switch (algorithm) {
            case BULLY:
                return new BullyElection(selfNode, electionId, clusterNodes);
            case RAFT:
                throw new UnsupportedOperationException("Raft not yet implemented");
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
```

**Updated `NodeImpl` constructor:**
```java
public NodeImpl(String host, int port, ElectionStrategyFactory.Algorithm algorithm) 
        throws RemoteException {
    
    // Initialize node fields FIRST
    this.nodeIdValue = System.currentTimeMillis();
    this.nodeId = "node-" + host + "-" + port + "-" + nodeIdValue;
    this.port = port;
    this.clusterNodesCache = new ArrayList<>();
    
    // NOW create strategy with fully-initialized node reference
    this.electionStrategy = ElectionStrategyFactory.create(
        algorithm,          // ← Enum for type safety
        this,               // ← 'this' is now valid!
        nodeIdValue,        // ← Real timestamp
        clusterNodesCache   // ← Real cache reference
    );
    
    // ... rest of initialization ...
}
```

**Usage in tests (clean and simple):**
```java
import static com.hecaton.election.ElectionStrategyFactory.Algorithm;

// Leader
NodeImpl leader = new NodeImpl("localhost", 5001, Algorithm.BULLY);

// Worker
NodeImpl worker = new NodeImpl("localhost", 5002, Algorithm.BULLY);

// Future: switching to Raft is trivial
NodeImpl node = new NodeImpl("localhost", 5003, Algorithm.RAFT);
```

### Why Factory Pattern Wins

**Advantages:**
- ✅ **Elegant**: Single point of strategy creation
- ✅ **Scalable**: Adding Raft = one new `case` statement
- ✅ **Type-safe**: Enum prevents typos (compile-time safety)
- ✅ **Flexible**: Easy to swap algorithms via constructor parameter
- ✅ **SOLID compliant**: Follows Dependency Inversion and Open/Closed principles
- ✅ **No invalid state**: Node always has a valid strategy after construction
- ✅ **Clean tests**: No boilerplate, just specify algorithm enum

**Trade-offs accepted:**
- ⚠️ Algorithm selection happens at compile-time (constructor parameter)
- ⚠️ Factory must know about all concrete implementations

**Design Decision:** Algorithm selection at node creation time is acceptable because:
1. Election algorithm is a fundamental architectural choice, not runtime configuration
2. Changing algorithms requires cluster-wide coordination anyway
3. For educational project, compile-time selection is sufficient


---

## RMI Connection Issues

### Port Already in Use

**Symptoms:**
```
java.rmi.server.ExportException: Port already in use: 5001
```

**Cause:** Previous Java process still running or another application using the port.

**Solution 1 - Kill Java processes:**
```powershell
Get-Process java | Stop-Process -Force
```

**Solution 2 - Use different port:**
Change port in test code:
```java
NodeImpl node = new NodeImpl("localhost", 5004);  // Instead of 5001
```

---

### Connection Refused

**Symptoms:**
```
java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
    java.net.ConnectException: Connection refused: connect
```

**Cause:** Leader not running or not fully initialized.

**Solution:**
1. Start Leader first (TestLeaderNode)
2. Wait 2-3 seconds for RMI registry to initialize
3. Then start Worker/other tests

**Verification:** Leader terminal should show:
```
[OK} Node node-localhost-5001-... initialized on port 5001 with RMI registry
[OK} started as LEADER
```

---

## Build/Compilation Issues

### Class Not Found During Compilation

**Symptoms:**
```
[ERROR] cannot find symbol: class NodeService
```

**Cause:** Missing source files or incorrect package structure.

**Solution:**
```powershell
# Clean and recompile
mvn clean compile

# Verify package structure matches
# src/main/java/com/hecaton/rmi/NodeService.java
# src/main/java/com/hecaton/node/NodeImpl.java
```

---

### Test Class Not Found

**Symptoms:**
```
[ERROR] The specified mainClass doesn't exist
```

**Cause:** Test class not compiled or wrong package name.

**Solution:**
```powershell
# Recompile including test sources
mvn clean test-compile

# Verify test class exists in correct package
# src/test/java/com/hecaton/manual/node/TestLeaderNode.java
```

---

## Issue Resolution Timeline

| Date | Issue | Solution | Status |
|------|-------|----------|--------|
| 2025-12-27 | PowerShell quote parsing | Use single quotes `'-Dparam=value'` | ✅ Solved |
| 2025-12-27 | Maven executes wrong class | Remove hardcoded mainClass from pom.xml | ✅ Solved |
| 2026-01-15 | Circular dependency ElectionStrategy/NodeImpl | Implement ElectionStrategyFactory pattern | ✅ Solved |

---

## Reporting New Issues

When encountering new issues:

1. **Check this document first** - Issue might already be documented
2. **Check terminal output** - Read full error message and stack trace
3. **Check logs** - Look in `logs/hecaton.log` for detailed errors
4. **Try clean build** - `mvn clean compile` often fixes compilation issues
5. **Document solution** - Add to this file once resolved

---
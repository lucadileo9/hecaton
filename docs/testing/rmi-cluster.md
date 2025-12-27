# Testing: RMI Communication & Cluster Formation

This guide covers testing for **Java RMI** communication and **cluster formation** (Leader/Worker nodes).

---

## What We're Testing

- ✅ RMI Registry creation (each node has its own)
- ✅ Remote method invocation (ping, getId, getStatus)
- ✅ Leader node startup and initialization
- ✅ Worker node joining cluster
- ✅ Node registration flow
- ✅ Multi-node cluster coordination

---

## Available Tests

All tests are in `src/test/java/com/hecaton/manual/`

### Node Tests (`manual/node/`)

| Test | Command | Description | Prerequisites |
|------|---------|-------------|---------------|
| **TestLeaderNode** | `mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'` | Start Leader on port 5001 | None |
| **TestWorkerNode** | `mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestWorkerNode'` | Start Worker on port 5002, join Leader | TestLeaderNode running |
| **TestThreeNodeCluster** | `mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestThreeNodeCluster'` | Add 3rd Worker on port 5003 | TestLeaderNode + TestWorkerNode running |

### RMI Tests (`manual/rmi/`)

| Test | Command | Description | Prerequisites |
|------|---------|-------------|---------------|
| **TestRemotePing** | `mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.rmi.TestRemotePing'` | Test RMI calls: ping(), getId(), getStatus() | TestLeaderNode running |
| **TestNodeRegistration** | `mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.rmi.TestNodeRegistration'` | Test registration flow with temporary node | TestLeaderNode running |

---

## Quick Start: Two-Node Cluster

### Step 1: Start Leader

**Terminal 1:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Expected output:**
```
========================================
  TEST 1: Leader Node Startup
========================================

12:30:15.123 [main] INFO  c.h.node.NodeImpl - Node node-localhost-5001-1735315815123 initialized on port 5001 with RMI registry
12:30:15.234 [main] DEBUG c.h.node.NodeImpl - [OK} Node node-localhost-5001-1735315815123 started as LEADER on port 5001
12:30:15.235 [main] DEBUG c.h.node.NodeImpl - [OK} Cluster size: 1 node(s)

[OK} Leader node running on port 5001
[OK} RMI registry created
[OK} Cluster size: 1

Press Ctrl+C to stop
```

**What happens internally:**
1. `NodeImpl` constructor creates RMI registry on port 5001
2. Binds itself as "node" in registry
3. `startAsLeader()` additionally binds as "leader"
4. Registers itself as first cluster member

---

### Step 2: Start Worker

**Terminal 2:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestWorkerNode'
```

**Expected output (Terminal 2 - Worker):**
```
========================================
  TEST 2: Worker Node Joining Cluster
========================================

12:31:20.456 [main] INFO  c.h.node.NodeImpl - Node node-localhost-5002-1735315880456 initialized on port 5002 with RMI registry
12:31:20.567 [main] DEBUG c.h.node.NodeImpl - [OK} Node node-localhost-5002-1735315880456 joined cluster via localhost:5001

[OK} Worker node running on port 5002
[OK} Connected to Leader at localhost:5001
[OK} Registration complete

Check Leader's terminal for 'New node registered' message
Press Ctrl+C to stop
```

**Expected output (Terminal 1 - Leader):**
```
12:31:20.570 [RMI TCP Connection(2)-127.0.0.1] DEBUG c.h.node.NodeImpl - [OK} New node registered: node-localhost-5002-1735315880456 (Total: 2 nodes)
```

**What happens internally:**
1. Worker creates its own RMI registry on port 5002
2. Binds itself as "node"
3. Looks up "leader" in Leader's registry (port 5001)
4. Calls `leader.registerNode(this)` remotely
5. Leader adds Worker to its internal node list

---

## Detailed Test Scenarios

### Test 1: Leader Node Startup

**Goal**: Verify Leader can initialize successfully

**What to verify:**
- ✅ No exceptions during startup
- ✅ RMI registry created on port 5001
- ✅ Both "node" and "leader" bindings exist
- ✅ Cluster size is 1 (Leader registers itself)

**Key code path:**
```java
NodeImpl leader = new NodeImpl("localhost", 5001);
// → Creates RMI registry
// → Exports object for RMI
// → Binds as "node"

leader.startAsLeader();
// → Adds self to registeredNodes list
// → Binds as "leader"
```

---

### Test 2: Worker Node Joining

**Goal**: Verify Worker can join existing cluster

**What to verify:**
- ✅ Worker creates its own RMI registry (port 5002)
- ✅ Worker connects to Leader's registry (port 5001)
- ✅ Worker successfully calls `registerNode()` remotely
- ✅ Leader logs "New node registered"
- ✅ Leader's cluster size increases to 2

**Key code path:**
```java
NodeImpl worker = new NodeImpl("localhost", 5002);
// → Creates own RMI registry on 5002

worker.joinCluster("localhost", 5001);
// → LocateRegistry.getRegistry("localhost", 5001)
// → registry.lookup("leader")
// → leader.registerNode(this)  // RMI call!
```

---

### Test 3: Three Node Cluster

**Goal**: Demonstrate scalability beyond 2 nodes

**Prerequisites**: TestLeaderNode + TestWorkerNode already running

**What to verify:**
- ✅ Third node can join without issues
- ✅ Leader's cluster size becomes 3
- ✅ All nodes remain responsive

**Use case**: Validates cluster can grow dynamically

---

### Test 4: Remote RMI Ping

**Goal**: Test basic RMI communication without permanent node

**What happens:**
1. Connects to Leader's RMI registry
2. Looks up "leader" service
3. Calls `ping()` - expects `true`
4. Calls `getId()` - expects node ID string
5. Calls `getStatus()` - expects "LEADER"

**What to verify:**
- ✅ All RMI calls succeed
- ✅ No `RemoteException` thrown
- ✅ Correct values returned

**Expected output:**
```
1. Connecting to Leader's RMI registry on localhost:5001...
   [OK} Connected to registry

2. Looking up 'leader' service...
   [OK} Leader service found

3. Calling ping() method remotely...
   [OK} Ping successful: true

4. Getting Leader's ID...
   [OK} Leader ID: node-localhost-5001-1735315815123

5. Getting Leader's status...
   [OK} Leader status: LEADER

========================================
  [OK} ALL RMI CALLS SUCCESSFUL
========================================
```

---

### Test 5: Node Registration Flow

**Goal**: Verify registration mechanism in isolation

**What happens:**
1. Creates temporary Worker node (port 6000)
2. Connects to Leader
3. Calls `registerNode()` directly
4. Exits (temporary node)

**What to verify:**
- ✅ Registration succeeds
- ✅ Leader logs "New node registered"
- ✅ Leader's cluster size increases

**Note**: Temporary node exits after registration. Leader still tracks it (cleanup happens via heartbeat in Phase 1.4)

---

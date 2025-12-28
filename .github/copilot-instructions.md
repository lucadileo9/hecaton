# Hecaton - AI Coding Assistant Instructions

## Project Overview

**Hecaton** is a P2P distributed computing system using Java RMI for cluster coordination. Transforms heterogeneous computers into a virtual supercomputer with automatic leader election and fault tolerance.

**Architecture Philosophy**: 3-layer design
1. **Network & Control** (Java RMI): Discovery, heartbeat, leader election, fault detection
2. **Application Logic** (Strategy Pattern): Pluggable Task implementations (currently password cracking)
3. **User Interface**: CLI (planned - currently using manual test harnesses)

**Deployment Stages**: Local (multi-terminal) → Docker Compose → Physical hybrid cluster (PC + laptop + Android/Termux)

## Critical Build & Test Commands

### Maven Execution Model

All code runs via `mvn exec:java` with explicit main classes (no default). Project uses **test classpath** for manual integration tests:

```powershell
# Compile everything (main + test sources)
mvn clean test-compile

# Run Leader node on port 5001
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'

# Run Worker node on port 5002 (requires Leader running first)
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestWorkerNode'

# Kill all Java processes (when ports stuck)
Get-Process java | Stop-Process -Force
```

**Why test-compile?** Tests are NOT JUnit - they're runnable integration tests with `main()` methods requiring both source and test classes compiled.

### Multi-Terminal Workflow

Most testing requires **2-3 simultaneous terminals**:
1. Start Leader first (port 5001) - must be running before workers
2. Workers join sequentially (ports 5002, 5003+)
3. Watch both terminals for distributed log coordination

## Package Structure & Responsibilities

```
com.hecaton.
├── rmi/           # Remote interfaces (NodeService, LeaderService)
├── node/          # Core node impl (NodeImpl, NodeBootstrap)
├── cli/           # Main entry point (future CLI)
├── discovery/     # Node discovery mechanisms (planned)
├── election/      # Leader election algorithm (planned)
├── monitor/       # Heartbeat monitoring (planned)
├── scheduler/     # Task scheduling (planned)
└── task/          # Task interface & implementations (planned)

src/test/java/com/hecaton/manual/
├── node/          # Multi-node cluster tests
└── rmi/           # RMI communication tests
```

## RMI Architecture Essentials

### Every Node Has Its Own RMI Registry

**Critical Pattern**: Each node creates `LocateRegistry.createRegistry(port)` in constructor:
- Leader binds as BOTH `"node"` and `"leader"` in its registry
- Workers bind only as `"node"` in their registry
- Workers connect to Leader's registry at known `host:port`

```java
// Leader startup sequence
NodeImpl leader = new NodeImpl("localhost", 5001);  // Creates registry
leader.startAsLeader();  // Binds as "leader" + self-registers

// Worker startup sequence  
NodeImpl worker = new NodeImpl("localhost", 5002);  // Creates own registry
worker.joinCluster("localhost", 5001);  // Connects to Leader's registry
```

### RMI Hostname Configuration

**Automatic in constructor**: Sets `java.rmi.server.hostname=localhost` if not already set to prevent "Connection refused" on multi-NIC systems.

### Remote Interfaces Contract

- MUST extend `java.rmi.Remote`
- Every method MUST throw `RemoteException`
- See [NodeService.java](src/main/java/com/hecaton/rmi/NodeService.java) and [LeaderService.java](src/main/java/com/hecaton/rmi/LeaderService.java)

## Testing Philosophy

**No JUnit** - System requires multi-process RMI communication across separate JVMs. Manual integration tests with `main()` methods simulate real distributed scenarios.

**Test Execution Order** (documented in [docs/testing/rmi-cluster.md](docs/testing/rmi-cluster.md)):
1. Prerequisites must be running (e.g., Leader before Worker)
2. Wait 2-3 seconds for RMI services to initialize
3. Check BOTH terminals for coordinated log messages

**Naming Convention**: `Test<FeatureName>` (not "Phase1Test") in `com.hecaton.manual.<category>` packages.

## Logging Conventions

**Framework**: SLF4J + Logback ([logback.xml](src/main/resources/logback.xml))

**Per-Package Levels**:
- `com.hecaton.node` → DEBUG (detailed node operations)
- `com.hecaton.monitor` → DEBUG (heartbeat debugging)
- Everything else → INFO

**Log Patterns**:
- Success: `[OK] Node node-localhost-5001-xxx started as LEADER`
- Events: `New node registered: node-localhost-5002-xxx (Total: 2 nodes)`
- Console: `HH:mm:ss.SSS [thread] LEVEL logger - message`
- File: `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL logger - message` → `logs/hecaton.log`

## Code Patterns to Follow

### Node Identity

Unique IDs use timestamp + connection details:
```java
this.nodeIdValue = System.currentTimeMillis();
this.nodeId = "node-" + host + "-" + port + "-" + nodeIdValue;
```

### Leader/Worker Duality

`NodeImpl` implements BOTH `NodeService` (all nodes) and `LeaderService` (Leader only):
- `isLeader` boolean flag switches behavior
- Leader maintains `registeredNodes` list
- Workers store `leaderRef` for callbacks

### State Pattern (Future)

Task implementations will use Strategy Pattern - swap `Task` implementations without changing cluster infrastructure.

## Development Phases (ROADMAP.md)

**Current**: Phase 1.2 - Basic RMI cluster formation (Leader + Workers)

**Next**: Phase 1.3 - Heartbeat monitoring + fault detection

**Future**: Leader election (Phase 2), Task distribution (Phase 3), Containerization (Phase 4)

## Documentation Structure

- [README.md](README.md) - Vision, architecture overview, complete workflow
- [ROADMAP.md](ROADMAP.md) - Incremental implementation phases with code examples
- [docs/architecture/overview.md](docs/architecture/overview.md) - Structural details
- [docs/testing/README.md](docs/testing/README.md) - Testing philosophy & guides
- [docs/testing/rmi-cluster.md](docs/testing/rmi-cluster.md) - Current test execution instructions

## Common Pitfalls

1. **Port conflicts**: Java processes don't always terminate cleanly → use `Get-Process java | Stop-Process -Force`
2. **Worker before Leader**: Workers need Leader's registry → start Leader first, wait for initialization
3. **Wrong main class**: exec-maven-plugin has no default → always specify `-Dexec.mainClass`
4. **Missing test-compile**: Manual tests need both src/main and src/test compiled
5. **RMI hostname**: Already handled in `NodeImpl` constructor, but be aware for debugging multi-NIC issues

## When Adding New Features

1. **Create manual test first** in appropriate `manual/<category>/` package
2. **Update test documentation** in `docs/testing/<feature>.md`
3. **Follow package boundaries**: RMI interfaces → `rmi/`, implementations → component packages
4. **Add logging** with appropriate SLF4J level for the package
5. **Document in ROADMAP.md** under relevant phase section

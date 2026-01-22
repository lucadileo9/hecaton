# Architecture Overview

Hecaton is a P2P distributed computing system that transforms heterogeneous computers into a virtual supercomputer. Built on Java RMI, it implements a **Leader-Worker architecture** with automatic leader election, fault tolerance, and pluggable task distribution strategies.

## Table of Contents

- [Project Description](#project-description)
- [Package & Class Reference](#package--class-reference)
- [System Integration](#system-integration)
- [Key Interactions](#key-interactions)
  - [Job Submission Flow](#1-job-submission-flow)
  - [Early Termination](#2-early-termination)
  - [Heartbeat Monitoring & Election Trigger](#3-heartbeat-monitoring--election-trigger)
  - [Worker Failure & Task Reassignment](#4-worker-failure--task-reassignment)
  - [Bully Election Algorithm](#5-bully-election-algorithm)
- [Testing Strategy](#testing-strategy)
- [Logging Configuration](#logging-configuration)

---

## Project Description

Hecaton enables distributed computation by:
- **Coordinating** multiple compute nodes via Leader-Worker pattern
- **Distributing** workload using pluggable splitting/assignment strategies
- **Executing** tasks in parallel across worker thread pools
- **Monitoring** node health through bidirectional heartbeat mechanisms
- **Recovering** from failures automatically (worker death → task reassignment)
- **Electing** new leaders when current leader fails (Bully algorithm)

### Key Characteristics

| Aspect | Technology/Approach |
|--------|-------------------|
| **Communication** | Java RMI (Remote Method Invocation) |
| **Architecture** | Leader-Worker with dynamic election |
| **Failure Detection** | Bidirectional heartbeat monitoring |
| **Leader Election** | Bully Algorithm (highest ID wins) |
| **Language** | Java 17 |
| **Build System** | Maven 3.x |

### Architectural Layers

The system is organized into **three logical levels**:

```
┌─────────────────────────────────────────────────────────┐
│  1. Network & Control Level (The Brain)                 │
│     RMI, Discovery, Heartbeat, Election                 │
├─────────────────────────────────────────────────────────┤
│  2. Application Logic Level (The Extensible Engine)     │
│     Task Framework (Strategy Pattern for computation)   │
├─────────────────────────────────────────────────────────┤
│  3. User Interface Level (The Command Center)           │
│     CLI (Picocli-based commands)                        │
└─────────────────────────────────────────────────────────┘
```

**Cross-cutting Concerns**: Logging (SLF4J), Configuration (ClusterConfig), Capabilities (NodeCapabilities)

---

## Package & Class Reference

### Package Structure

```
com.hecaton/
├── cli/                    # Command-line interface
├── rmi/                    # Remote interfaces (contracts)
├── node/                   # Node core implementation
├── discovery/              # Cluster discovery & membership
├── monitor/                # Health monitoring
├── election/               # Leader election algorithms
├── scheduler/              # Job orchestration
└── task/                   # Task framework & implementations
```

### Class Reference by Package

| Package | Key Classes | Responsibility |
|---------|-------------|----------------|
| **rmi** | `NodeService`, `LeaderService` | RMI contracts: common node ops vs leader-specific ops |
| **node** | `NodeImpl`, `TaskExecutor`, `ClusterConfig`, `NodeCapabilities` | Dual-mode node (Leader/Worker), task execution thread pool, strategy configuration, hardware detection |
| **discovery** | `ClusterMembershipService`, `UdpDiscoveryService`, `NodeInfo` | Thread-safe node registry, UDP broadcast discovery, serializable node metadata |
| **monitor** | `HeartbeatMonitor`, `FailureDetector` | Worker→Leader active monitoring, Leader→Workers passive monitoring |
| **election** | `ElectionStrategy`, `BullyElection` | Strategy interface, Bully algorithm (highest ID wins) |
| **scheduler** | `JobManager`, `TaskScheduler` | Job lifecycle facade, distributed task state machine |
| **task** | `Job`, `Task`, `JobResult`, `TaskResult`, `SplittingStrategy`, `AssignmentStrategy` | Work interfaces, result containers, job→tasks splitting, tasks→workers assignment |
| **task/impl** | `PasswordCrackJob`, `SumRangeJob` | Example implementations with early termination support |

### Class Diagram

```mermaid
classDiagram
    direction TB
    
    %% RMI Interfaces
    class NodeService {
        <<interface>>
        +ping() boolean
        +getNodeId() String
        +getCapabilities() NodeCapabilities
        +executeTasks(List~Task~) void
        +cancelJob(String jobId) void
        +receiveElectionMessage(long senderId) void
        +receiveCoordinatorMessage(String leaderId, String host, int port) void
    }
    
    class LeaderService {
        <<interface>>
        +registerNode(NodeService node) void
        +submitJob(Job job) JobResult
        +submitResults(String jobId, List~TaskResult~) void
        +getClusterNodes() List~NodeInfo~
        +ping(String workerId) void
    }
    
    %% Core Node
    class NodeImpl {
        -String nodeId
        -long nodeIdValue
        -boolean isLeader
        -Registry myRegistry
        -List~NodeInfo~ clusterNodesCache
        +startAsLeader() void
        +joinCluster(String host, int port) void
        +promoteToLeader() void
    }
    
    NodeImpl ..|> NodeService
    NodeImpl ..|> LeaderService
    
    %% Leader-only components
    class ClusterMembershipService {
        -Map~String, NodeService~ nodes
        +addNode(NodeService node) void
        +removeNodeById(String nodeId) void
        +getAllNodes() List~NodeService~
    }
    
    class JobManager {
        -Map~String, Job~ activeJobs
        -Map~String, CountDownLatch~ pendingJobs
        -SplittingStrategy splittingStrategy
        -AssignmentStrategy assignmentStrategy
        +submitJob(Job job) JobResult
        +onWorkerFailed(String workerId) void
    }
    
    class TaskScheduler {
        -Map~String, JobContext~ jobContexts
        -Map~String, Set~String~~ workerIndex
        +scheduleTasks(String jobId, Map assignments) void
        +submitResults(String jobId, List~TaskResult~) void
        +terminateJob(String jobId) void
    }
    
    class FailureDetector {
        -Map~String, Long~ lastHeartbeatTable
        -Consumer~String~ failureCallback
        +start() void
        +stop() void
        +ping(String workerId) void
    }
    
    %% Worker-only components
    class HeartbeatMonitor {
        -NodeService targetNode
        -int missedHeartbeats
        -Runnable onNodeDied
        -Runnable cacheRefreshCallback
        +start() void
        +stop() void
    }
    
    class TaskExecutor {
        -ExecutorService threadPool
        -LeaderService leaderRef
        -Map~String, List~Future~~ runningTasks
        +receiveTasks(List~Task~) void
        +cancelJob(String jobId) void
    }
    
    %% Election
    class ElectionStrategy {
        <<interface>>
        +startElection() void
        +notifyCoordinatorReceived() void
    }
    
    class BullyElection {
        -long selfElectionId
        -Supplier~List~NodeInfo~~ clusterNodesSupplier
        -Runnable promoteCallback
    }
    
    BullyElection ..|> ElectionStrategy
    
    %% Task Framework
        
    class SplittingStrategy {
        <<interface>>
        +split(Job job, Map capabilities) List~Task~
    }
    
    class AssignmentStrategy {
        <<interface>>
        +assign(List~Task~, Map capabilities) Map
    }

    class Job {
        <<interface>>
        +getJobId() String
        +supportsEarlyTermination() boolean
        +aggregateResults(List~TaskResult~) JobResult
    }
    
    class Task {
        <<interface>>
        +getTaskId() String
        +getJobId() String
        +execute() TaskResult
    }
    
    %% Relationships
    NodeImpl --> HeartbeatMonitor : creates (Worker)
    NodeImpl --> TaskExecutor : creates (Both)
    NodeImpl --> ElectionStrategy : uses
    
    JobManager --> TaskScheduler : creates
    JobManager --> SplittingStrategy : uses
    JobManager --> AssignmentStrategy : uses
    
    TaskScheduler --> ClusterMembershipService : uses
    FailureDetector --> ClusterMembershipService : uses
    
    TaskExecutor ..> LeaderService : RMI submitResults
    TaskScheduler ..> NodeService : RMI executeTasks

        NodeImpl --> ClusterMembershipService : creates (Leader)
    NodeImpl --> JobManager : creates (Leader)
    NodeImpl --> FailureDetector : creates (Leader)

```

### Core Design Patterns

| Pattern | Usage |
|---------|-------|
| **State Pattern** | `NodeImpl` switches Leader/Worker behavior via `isLeader` flag |
| **Strategy Pattern** | Pluggable `SplittingStrategy`, `AssignmentStrategy`, `ElectionStrategy` |
| **Facade Pattern** | `JobManager` hides `TaskScheduler` complexity |
| **Observer/Callback** | Failure callbacks (`onWorkerFailed`, `onLeaderDied`) |
| **Supplier Pattern** | Lazy cluster cache evaluation in `BullyElection` |

---

## System Integration

### Component Dependency Graph

This diagram shows how components are created and how they interact at runtime:

```mermaid
flowchart TB

    subgraph CONFIG["⚙️ SHARED CONFIG"]
        CC["ClusterConfig"]
        Split["SplittingStrategy"]
        Assign["AssignmentStrategy"]
        CC --> Split
        CC --> Assign
    end

    subgraph LEADER["🟢 LEADER NODE"]
        direction TB
        NI_L["<b>NodeImpl</b><br/>isLeader = true"]
        
        subgraph LEADER_COMPONENTS["Leader Components"]
            CMS["ClusterMembershipService<br/><i>node registry</i>"]
            JM["JobManager<br/><i>job lifecycle</i>"]
            TS["TaskScheduler<br/><i>task distribution</i>"]
            UDP["UdpDiscoveryService<br/><i>broadcaster</i>"]
            FD["FailureDetector<br/><i>worker monitoring</i>"]
        end
        
        NI_L --> CMS
        NI_L --> JM
        NI_L --> FD

        JM --> TS

    end
    
    subgraph WORKER["🔵 WORKER NODE"]
        direction TB
        NI_W["<b>NodeImpl</b><br/>isLeader = false"]
        
        subgraph WORKER_COMPONENTS["Worker Components"]
            HM["HeartbeatMonitor<br/><i>leader monitoring</i>"]
            TE["TaskExecutor<br/><i>thread pool</i>"]
            Cache["clusterNodesCache<br/><i>election data</i>"]
        end
        
        subgraph ELECTION_BOX["Election"]
            Elect["BullyElection"]
        end
        
        NI_W --> HM
        NI_W --> TE
        NI_W --> Elect
    end
    
    
    %% Cross-node relationships
    JM -.-> Split
    JM -.-> Assign
    TS -.-> CMS
    FD -.-> CMS
    
    %% Callbacks (dashed red)
    FD ==>|"onWorkerFailed()"| JM
    HM ==>|"onLeaderDied()"| Elect
    HM ==>|"refreshCache()"| Cache
    Elect -.-> Cache
    Elect ==>|"promoteToLeader()"| NI_W
    
    %% RMI Communication (bold blue)
    TS <-->|"RMI: executeTasks()<br/>RMI: cancelJob()"| TE
    TE <-->|"RMI: submitResults()"| TS
    HM <-->|"RMI: ping()"| FD
```

**Legend**:
- **Solid arrows** (→): Creation/ownership relationships
- **Dashed arrows** (⇢): Usage dependencies  
- **Double arrows** (⇒): Callback invocations
- **Bidirectional** (↔): RMI communication

### Key Relationships

**Creation Hierarchy**:
- `NodeImpl` creates all components (`ClusterMembershipService`, `JobManager`, `FailureDetector`, `TaskExecutor`)
- `JobManager` creates `TaskScheduler` internally (avoids circular dependency)
- `ClusterConfig` creates `ElectionStrategy` via factory method

**Callback Chains**:
- `FailureDetector` → `onWorkerFailed()` → `JobManager` → `TaskScheduler` reassignment
- `HeartbeatMonitor` → `onLeaderDied()` → `BullyElection` → `promoteToLeader()`
- `HeartbeatMonitor` → `updateClusterCache()` → refreshes election data

**RMI Communication**:
- Leader's `TaskScheduler` → Worker's `TaskExecutor` (`executeTasks`, `cancelJob`)
- Worker's `TaskExecutor` → Leader's `JobManager` (`submitResults`)
- Worker → Leader (`ping` for heartbeat via `LeaderService`)

---

## Key Interactions

### 1. Job Submission Flow

When a client submits a job, it flows through multiple components for splitting, assignment, distribution, execution, and result aggregation.

```mermaid
sequenceDiagram
    participant CLI
    participant Leader as NodeImpl<br/>(Leader)
    participant JM as JobManager
    participant Split as SplittingStrategy
    participant Assign as AssignmentStrategy
    participant TS as TaskScheduler
    participant W1 as Worker-1<br/>TaskExecutor
    participant W2 as Worker-2<br/>TaskExecutor
    
    CLI->>Leader: submitJob(Job) via RMI
    Leader->>JM: submitJob(Job)
    
    Note over JM: Blocking call with timeout
    
    rect rgb(0,0,0)
        Note over JM,Assign: Phase 1: Split & Assign
        JM->>Split: split(job, workerCapabilities)
        Split-->>JM: List<Task> tasks
        JM->>Assign: assign(tasks, workers)
        Assign-->>JM: Map<WorkerId, List<Task>>
    end
    
    rect rgb(25,25,25)
        Note over JM,W2: Phase 2: Distribute
        JM->>TS: scheduleTasks(jobId, assignments)
        par Parallel dispatch
            TS->>W1: RMI: executeTasks(tasks)
            TS->>W2: RMI: executeTasks(tasks)
        end
    end
    
    rect rgb(50, 50, 50)
        Note over W1,JM: Phase 3: Execute & Stream Results
        Note over W1,W2: Parallel execution in thread pools
        par Results streaming
            W1->>TS: RMI: submitResults(results)
            W2->>TS: RMI: submitResults(results)
        end
        TS->>JM: onJobFinished(jobId, allResults)
    end
    
    JM->>JM: Aggregate via job.aggregateResults()
    JM-->>Leader: JobResult
    Leader-->>CLI: JobResult
```

**Key Implementation Details**:
- `JobManager.submitJob()` blocks using `CountDownLatch` until all tasks complete or timeout
- `TaskScheduler` tracks task states: `WORKING` → `SUCCESS`/`FAILED`/`CANCELLED`
- Results are streamed as they complete (not batched at end)

---

### 2. Early Termination

Early termination allows jobs to stop immediately when a result is found (e.g., password cracking). This is a **3-layer mechanism**:

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **Job Declaration** | `Job.supportsEarlyTermination()` | Opt-in flag (default: false) |
| **Local Cancellation** | `TaskExecutor` | Cancels remaining tasks on this worker |
| **Remote Broadcast** | `TaskScheduler` | Broadcasts `cancelJob()` to ALL workers |

```mermaid
sequenceDiagram
    participant W1 as Worker-1<br/>TaskExecutor
    participant W2 as Worker-2<br/>TaskExecutor
    participant JM as JobManager
    participant TS as TaskScheduler
    
    Note over W1,W2: Both workers executing tasks in parallel
    
    rect rgb(13, 13, 13)
        Note over W1: Task finds SUCCESS result!
        W1->>W1: earlyTerminationTriggered = true
        W1->>W1: cancelRemainingTasks(jobId)
        Note over W1: Interrupts thread pool futures
        W1->>JM: RMI: submitResults([successResult])
    end
    
    rect rgb(57, 57, 63)
        Note over JM,TS: Leader broadcasts termination
        JM->>TS: submitResults(jobId, results)
        TS->>JM: supportsEarlyTermination(jobId)?
        
        JM->>TS: supportsEarlyTermination(jobId) = true
        TS->>TS: terminateJob(jobId)
        TS->>W2: RMI: cancelJob(jobId)
    end
    
    rect rgb(39, 43, 39)
        Note over W2: Worker-2 receives cancel
        W2->>W2: cancelRemainingTasks(jobId)
    end
    
    TS->>JM: onJobFinished(jobId, allResults)
    
    JM->>JM: CountDownLatch released
    Note over JM: Job completes with found result
```

**Trigger Condition**: Only `TaskResult.Status.SUCCESS` triggers termination, not `COMPLETED`. This distinction allows:
- `SUCCESS`: Found the answer (e.g., cracked password) → **STOP**
- `COMPLETED`: Finished searching range, nothing found → continue other tasks

---

### 3. Heartbeat Monitoring & Election Trigger

Hecaton uses **bidirectional health monitoring**:

| Direction | Component | Model | Purpose |
|-----------|-----------|-------|---------|
| Worker → Leader | `HeartbeatMonitor` | ACTIVE (push) | Detect leader failure → trigger election |
| Leader → Workers | `FailureDetector` | PASSIVE (receive) | Detect worker failure → reassign tasks |

**Heartbeat Constants**:
- Interval: 5 seconds
- Failure threshold: 3 missed heartbeats (15s total)
- Cache refresh: every 8 heartbeats (~40s)

```mermaid
sequenceDiagram
    participant W as Worker
    participant HM as HeartbeatMonitor
    participant L as Leader
    participant Elect as BullyElection
    
    loop Every 5 seconds (normal operation)
        W->>HM: ping(WorkerId)
        HM->>L: RMI: ping(WorkerId)
        L-->>HM: true
        HM->>HM: missedHeartbeats = 0
    end
    
    Note over L: ❌ Leader CRASHES
    
    rect rgb(32, 31, 31)
        Note over HM: Failure Detection Phase
        HM->>L: RMI: ping()
        Note over HM: RemoteException!
        HM->>HM: missedHeartbeats = 1
        
        Note over HM: +5s
        HM->>L: RMI: ping()
        Note over HM: RemoteException!
        HM->>HM: missedHeartbeats = 2
        
        Note over HM: +5s
        HM->>L: RMI: ping()
        Note over HM: RemoteException!
        HM->>HM: missedHeartbeats = 3
    end
    
    rect rgb(65, 64, 62)
        Note over HM,Elect: Election Trigger
        HM->>HM: THRESHOLD REACHED!
        HM->>HM: stop() monitoring
        HM->>W: callback: onLeaderDied()
        W->>Elect: startElection()
    end
    
    Note over Elect: See Bully Algorithm section
```

**Cache Refresh Importance**:
Workers periodically refresh their `clusterNodesCache` (every 40s) to know about nodes that joined after them. Without this:
```
Worker-A's cache: [Leader, A]     // Doesn't know about B, C
Actual cluster:   [Leader, A, B, C]
→ If A wins election: SPLIT-BRAIN (B, C orphaned)
```

---

### 4. Worker Failure & Task Reassignment

When `FailureDetector` detects a worker timeout (15s no heartbeat), it triggers task reassignment to healthy workers.

```mermaid
sequenceDiagram
    participant W2 as Worker-2
    participant L as Leader
    participant FD as FailureDetector
    participant JM as JobManager
    participant TS as TaskScheduler
    participant W1 as Worker-1<br/>(healthy)
    
    Note over W2: Worker-2 executing tasks
    
    loop Every 5 seconds (normal)
        W2->>L: RMI: ping("worker-2")
        L->>FD: ping("worker-2")
        FD->>FD: lastHeartbeat[worker-2] = now()
    end
    
    Note over W2: ❌ Worker-2 CRASHES
    
    rect 
        Note over FD: Timeout Detection
        Note over FD: T=0s: Last ping received
        Note over FD: T=5s: Check - OK (within 15s)
        Note over FD: T=10s: Check - OK (within 15s)
        Note over FD: T=15s: Check - TIMEOUT!
        FD->>FD: Worker-2 declared DEAD
    end
    
    rect rgb(52, 45, 18)
        Note over FD,TS: Failure Callback Chain
        FD->>L: callback: onWorkerFailed("worker-2")
        L->>JM: onWorkerFailed("worker-2")
        JM->>TS: onWorkerFailed("worker-2")
    end
    
    rect rgb(64, 69, 64)
        Note over TS,W1: Task Reassignment
        TS->>TS: Find orphaned tasks (status=WORKING)
        TS->>TS: tasks = [Task42, Task43, Task44]
        TS->>JM: reassignTasks(orphanedTasks)
        JM->>TS: newAssignments
        TS->>W1: RMI: executeTasks([Task42, Task43, Task44])
    end
    
    Note over W1: Executes orphaned tasks
    W1->>JM: RMI: submitResults(results)
    Note over JM: Job continues normally
```

---

### 5. Bully Election Algorithm

When a worker detects leader failure, it initiates the Bully election. The node with the **highest election ID** (timestamp-based) wins.

**Message Types** (via RMI):
| Message | Purpose | Implementation |
|---------|---------|----------------|
| ELECTION | "I'm starting election, respond if higher" | `receiveElectionMessage()` |
| OK | "My ID is higher, I'll handle it" | Implicit (no `RemoteException`) |
| COORDINATOR | "I won, I'm the new Leader" | `receiveCoordinatorMessage()` |

```mermaid
sequenceDiagram
    participant L as Leader<br/>
    participant A as Worker-A<br/>(ID: 2000)
    participant B as Worker-B<br/>(ID: 3000)
    participant C as Worker-C<br/>(ID: 4000)
    
    Note over L: ❌ Leader CRASHES
    Note over A,C: T=15s - All workers detect failure
    
    rect rgb(95, 92, 86)
        Note over A,C: Phase 1: Election Messages
        par All workers start election
            A->>A: startElection()
            A->>A: Find higher: [B, C]
            A->>B: ELECTION(2000)
            A->>C: ELECTION(2000)
        and
            B->>B: startElection()
            B->>B: Find higher: [C]
            B->>C: ELECTION(3000)
        and
            C->>C: startElection()
            C->>C: Find higher: []
            Note over C: No higher ID → I WIN!
        end
    end
    
    rect rgb(65, 64, 62)
        Note over C: Phase 2: Become Leader
        C->>C: promoteToLeader()
        C->>C: Initialize ClusterMembershipService
        C->>C: Initialize JobManager
        C->>C: Initialize FailureDetector
        C->>C: Bind as "leader" in RMI registry
        C->>C: Start UDP broadcaster
    end
    
    rect rgb(0, 0, 0)
        Note over A,C: Phase 3: Announce & Reconnect
        par Broadcast COORDINATOR
            C->>A: COORDINATOR(4000, host, port)
            C->>B: COORDINATOR(4000, host, port)
        end
        
        par Workers reconnect
            A->>A: receiveCoordinatorMessage()
            A->>C: RMI: registerNode(this)
            A->>A: Restart HeartbeatMonitor → C
        and
            B->>B: receiveCoordinatorMessage()
            B->>C: RMI: registerNode(this)
            B->>B: Restart HeartbeatMonitor → C
        end
    end
    
    Note over A,C: Cluster reformed with C as Leader
```

---

## Testing Strategy

Hecaton uses **manual integration tests** instead of JUnit for RMI cluster testing.

**Rationale**:
- RMI requires multiple JVM processes
- JUnit cannot simulate true distributed environment
- Manual tests provide realistic cluster behavior

**Test Structure**:
```
src/test/java/com/hecaton/manual/
├── node/          # Node lifecycle tests
├── rmi/           # RMI communication tests
├── monitor/       # Heartbeat & failure detection
├── election/      # Bully election scenarios
└── task/          # Job execution tests
```

**Running Tests**:
```bash
# Terminal 1: Start Leader
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'

# Terminal 2: Start Worker
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestWorkerNode'
```

See [testing readme](../testing/README.md) for complete testing guide.

---

## Logging Configuration

Structured logging via SLF4J + Logback:

```xml
<!-- src/main/resources/logback.xml -->
<logger name="com.hecaton.node" level="DEBUG"/>
<logger name="com.hecaton.monitor" level="DEBUG"/>
<logger name="com.hecaton" level="INFO"/>
```

**Log Levels**:
- **DEBUG**: Node lifecycle, heartbeat, detailed RMI operations
- **INFO**: Cluster events (join, registration, status changes)
- **WARN**: Heartbeat failures, retry attempts
- **ERROR**: Node failures, critical errors

**See** [logging configuration guide](../logging/README.md) for advanced setup.
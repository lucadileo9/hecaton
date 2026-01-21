# Task Framework Architecture

## Overview

The **Task Framework** is the core execution engine of Hecaton, responsible for distributing computational work across the cluster and aggregating results. It transforms large computational problems into parallelizable units of work that can be executed efficiently on heterogeneous worker nodes.

### Design Philosophy

The framework follows a **separation of concerns** approach with three distinct layers:

1. **Application Layer** (Job/Task interfaces) - Defines WHAT to compute
2. **Orchestration Layer** (JobManager/TaskScheduler) - Decides HOW to distribute work
3. **Execution Layer** (TaskExecutor) - Performs the actual computation

This architecture enables:
- **Pluggable algorithms**: Implement new distributed tasks without modifying cluster infrastructure
- **Flexible scheduling**: Different splitting and assignment strategies for different workloads
- **Fault tolerance**: Task reassignment on worker failure
- **Early termination**: Stop computation as soon as a result is found (for search problems)

---

## Architecture Overview

```mermaid
graph TB
    subgraph User["👤 User/CLI Layer"]
        Client[Client Code]
    end
    
    subgraph Leader["🎯 Leader Node"]
        JM[JobManager]
        SS[SplittingStrategy]
        AS[AssignmentStrategy]
        TS[TaskScheduler]
        
        JM -->|1. split job| SS
        SS -->|List&lt;Task&gt;| JM
        JM -->|2. assign tasks| AS
        AS -->|Map&lt;Worker,List&lt;Task&gt;&gt;| JM
        JM -->|3. schedule| TS
        
        subgraph TSState["TaskScheduler State"]
            JC[JobContext]
            CASS[&lt;Worker, List&lt;Task&gt;&gt; <br/>assignments <br/>Useful for task reassignment on failure]
            LAV[&lt;TaskId, TaskResult;&gt;<br/>taskStatus <br/>Contains the status and successive results of each task]
            CONT[AtomicInteger<br/>pendingTasks <br/>Count of tasks yet to complete]
            JC --> CASS
            JC --> LAV
            JC --> CONT
        end
        
        TS --> JC
    end
    
    subgraph Workers["⚙️ Worker Nodes"]
        W1[Worker <br/>TaskExecutor]
        
        subgraph ThreadPool["Thread Pool"]
            T1[Task 1]
            T2[Task 2]
            T3[Task 3]
        end
        
        W1 --> ThreadPool
    end
    
    Client -->|submitJob| JM
    TS -.->|RMI: executeTasks| W1
    W1 -.->|RMI: submitResult| TS
    JM -->|JobResult| Client
    
    style User fill: #0274ad
    style Leader fill: #b0730b
    style Workers fill: #21ad2c
    style TSState fill: #b04cbf
    style ThreadPool fill: #dbc60d
```

---

## Core Concepts

### Job

A **Job** represents a complete distributed computational task. It encapsulates:
- The problem definition (e.g., "crack this password hash")
- How to split the problem into smaller Tasks
- How to aggregate Task results into a final answer

**Key responsibilities:**
- `split(int numTasks)` - Divide work into parallelizable chunks
- `aggregateResults(List<TaskResult>)` - Combine partial results

### Task

A **Task** is a unit of work assigned to a single worker. It represents:
- A subset of the Job's work (e.g., "check password indices 0-1000")
- Self-contained computation with no dependencies on other Tasks
- Serializable for RMI transfer to workers

**Key responsibilities:**
- `execute()` - Perform the computation
- `execute(ExecutionContext)` - Execute with worker hardware info

### Result Flow

```mermaid
graph LR
    Job[Job] -->|split| T1[Task 1]
    Job -->|split| T2[Task 2]
    Job -->|split| T3[Task N]
    
    T1 -->|execute| R1[TaskResult 1]
    T2 -->|execute| R2[TaskResult 2]
    T3 -->|execute| R3[TaskResult N]
    
    R1 -->|aggregate| JR[JobResult]
    R2 -->|aggregate| JR
    R3 -->|aggregate| JR
    
    style Job fill: #3172a7
    style JR fill: #2d9530
```

---

## Job Lifecycle

### Complete Execution Flow

```mermaid
sequenceDiagram
    participant User
    participant JobManager
    participant SplittingStrategy
    participant AssignmentStrategy
    participant TaskScheduler
    participant Worker
    participant TaskExecutor

    User->>JobManager: submitJob(job)
    activate JobManager
    
    Note over JobManager: Phase 1: Splitting
    JobManager->>SplittingStrategy: split(job, workers)
    activate SplittingStrategy
    SplittingStrategy->>Job: split(numTasks)
    Job-->>SplittingStrategy: List<Task>
    SplittingStrategy-->>JobManager: List<Task>
    deactivate SplittingStrategy
    
    Note over JobManager: Phase 2: Assignment
    JobManager->>AssignmentStrategy: assign(tasks, capabilities)
    activate AssignmentStrategy
    AssignmentStrategy-->>JobManager: Map<WorkerId, List<Task>>
    deactivate AssignmentStrategy
    
    Note over JobManager: Phase 3: Scheduling
    JobManager->>TaskScheduler: scheduleTasks(jobId, assignments)
    activate TaskScheduler
    TaskScheduler->>TaskScheduler: Create JobContext
    
    Note over TaskScheduler,Worker: Phase 4: Execution
    TaskScheduler->>Worker: RMI: executeTasks(tasks)
    activate Worker
    Worker->>TaskExecutor: submit to thread pool
    activate TaskExecutor
    
    loop For each task
        TaskExecutor->>Task: execute()
        Task-->>TaskExecutor: TaskResult
        TaskExecutor->>TaskScheduler: RMI: submitResults(results)
        TaskScheduler->>TaskScheduler: Update taskStatus
        TaskScheduler->>TaskScheduler: Decrement pendingTasks
    end
    
    deactivate TaskExecutor
    deactivate Worker
    
    Note over TaskScheduler: All tasks complete
    TaskScheduler->>JobManager: onJobComplete(jobId, results)
    deactivate TaskScheduler
    
    Note over JobManager: Phase 5: Aggregation
    JobManager->>Job: aggregateResults(results)
    Job-->>JobManager: JobResult
    
    JobManager-->>User: return JobResult
    deactivate JobManager
```

### State Transitions

**Task State Machine:**

```mermaid
stateDiagram-v2
    [*] --> CREATED: Task created by Job.split()
    
    CREATED --> WORKING: Dispatched to worker
    
    WORKING --> SUCCESS: Result found
    WORKING --> PARTIAL: Partial contribution
    WORKING --> NOT_FOUND: Search exhausted
    WORKING --> FAILURE: Exception thrown
    WORKING --> CANCELLED: Early termination
    
    SUCCESS --> [*]
    PARTIAL --> [*]
    NOT_FOUND --> [*]
    FAILURE --> [*]
    CANCELLED --> [*]
    
    note left of SUCCESS
        Terminal state
        Triggers early termination
    end note
    
    note left of PARTIAL
        Non-terminal
        Used for aggregation jobs
    end note
```

## Class Hierarchy

### Core Interfaces and Abstract Classes

```mermaid
classDiagram
    class Job {
        <<interface>>
        +String getJobId()
        +void setJobId()
        +List~Task~ split(int numTasks)*
        +JobResult aggregateResults(List~TaskResult~)*
        +String getJobType()
        +boolean supportsEarlyTermination()
        +long estimateExecutionTime(int workers)
        +void onStart()
        +void onComplete(JobResult)
    }
    
    class AbstractJob {
        <<abstract>>
        -String jobId
        +String getJobId()
        +void setJobId()
        +String getJobType()
        +boolean supportsEarlyTermination()
        +void onStart()
        +void onComplete(JobResult)
        +long estimateExecutionTime(int)
        #split(int)* abstract
        #aggregateResults(List~TaskResult~)* abstract
    }
    
    class Task {
        <<interface>>
        +String getTaskId()
        +String getJobId()
        +TaskResult execute()*
        +TaskResult execute(ExecutionContext)
        +void onCancel()
        +int getEstimatedComplexity()
        +String getTargetWorkerId()
    }
    
    class AbstractTask {
        <<abstract>>
        -String jobId
        -String taskId
        +String getJobId()
        +String getTaskId()
        +TaskResult execute(ExecutionContext)
        +void onCancel()
        +int getEstimatedComplexity()
        +String getTargetWorkerId()
        #execute()* abstract
    }
    
    class PasswordCrackJob {
        -String targetHash
        -String charset
        -int minLength
        -int maxLength
        +split(int numTasks)
        +aggregateResults(List~TaskResult~)
    }
    
    class PasswordCrackTask {
        -String targetHash
        -String charset
        -int passwordLength
        -long startIndex
        -long endIndex
        +execute()
    }
    
    class SumRangeJob {
        -long start
        -long end
        +split(int numTasks)
        +aggregateResults(List~TaskResult~)
    }
    
    class SumRangeTask {
        -long start
        -long end
        +execute()
    }
    
    Job <|.. AbstractJob
    AbstractJob <|-- PasswordCrackJob
    AbstractJob <|-- SumRangeJob
    
    Task <|.. AbstractTask
    AbstractTask <|-- PasswordCrackTask
    AbstractTask <|-- SumRangeTask
    
    PasswordCrackJob ..> PasswordCrackTask : creates
    SumRangeJob ..> SumRangeTask : creates
```

---

## Strategy Pattern Implementation

The framework uses the **Strategy Pattern** to decouple job splitting and task assignment from the core orchestration logic.

### Splitting Strategies

```mermaid
classDiagram
    class SplittingStrategy {
        <<interface>>
        +List~Task~ split(Job, Map~String,NodeCapabilities~)*
        +String getName()
        +boolean isCompatibleWith(String)
    }
    
    class UniformSplitting {
        +split(Job, Map~String,NodeCapabilities~)
        +getName()
    }
    
    class WeightedSplitting {
        +split(Job, Map~String,NodeCapabilities~)
        +getName()
    }
    
    class DynamicSplitting {
        -int minTasksPerWorker
        -int maxTasksPerWorker
        +split(Job, Map~String,NodeCapabilities~)
        +getName()
    }
    
    SplittingStrategy <|.. UniformSplitting
    SplittingStrategy <|.. WeightedSplitting
    SplittingStrategy <|.. DynamicSplitting
    
    note for UniformSplitting "Creates N tasks where
    N = worker count"
    note for WeightedSplitting "Creates tasks proportional
    to worker CPU power"
    note for DynamicSplitting "Creates 2-4 tasks per worker
    for load balancing"
```

**Strategy Selection Guide:**

| Strategy | Use Case | Pros | Cons |
|----------|----------|------|------|
| **UniformSplitting** | Homogeneous cluster, predictable tasks | Simple, fair distribution | Underutilizes fast workers |
| **WeightedSplitting** | Heterogeneous hardware | Better resource utilization | Requires accurate capability info |
| **DynamicSplitting** | Unknown task complexity | Handles stragglers, fault tolerance | More overhead, complex |

### Assignment Strategies

```mermaid
classDiagram
    class AssignmentStrategy {
        <<interface>>
        +Map~String,List~Task~~ assign(List~Task~, Map~String,NodeCapabilities~)*
        +String getName()
    }
    
    class RoundRobinAssignment {
        +assign(List~Task~, Map~String,NodeCapabilities~)
        +getName()
    }
    
    class TargetedAssignment {
        +assign(List~Task~, Map~String,NodeCapabilities~)
        +getName()
    }
    
    AssignmentStrategy <|.. RoundRobinAssignment
    AssignmentStrategy <|.. TargetedAssignment
    
    note for RoundRobinAssignment "Distributes tasks evenly
    in cyclic order"
    note for TargetedAssignment "Respects task.getTargetWorkerId()
    Falls back to round-robin"
```

---

## Orchestration Components

### JobManager

**Purpose**: Entry point for job submission and high-level orchestration.

**Responsibilities:**
- Accept job submissions from users
- Coordinate splitting and assignment strategies
- Manage job timeout and cancellation
- Aggregate final results
- Track multiple concurrent jobs

**Threading Model:**
- `submitJob()` blocks until job completes or times out
- Uses `CountDownLatch` for synchronization
- Thread-safe for concurrent job submissions

### TaskScheduler

**Purpose**: Manages task lifecycle and worker communication.

**Architecture - "3 Data Structures" Design:**

```mermaid
graph TD
    subgraph JobContext["JobContext (per job)"]
        CASS[📦 Cassaforte<br/>assignments<br/>Map&lt;WorkerId, List&lt;Task&gt;&gt;]
        LAV[📝 Lavagna<br/>taskStatus<br/>Map&lt;TaskId, TaskResult&gt;]
        CONT[🔢 Contatore<br/>pendingTasks<br/>AtomicInteger]
        FLAG[🚩 terminated<br/>boolean]
    end
    
    CASS -.->|Read-only<br/>Used only on<br/>worker failure| Recovery[Task Recovery]
    LAV -.->|Write-heavy<br/>Updated on every<br/>task completion| Update[Status Updates]
    CONT -.->|Atomic decrement<br/>O1 completion<br/>check| Check[Job Complete?]
    FLAG -.->|Prevents<br/>double-trigger| Early[Early Termination]
    
```

**Key Features:**
- **O(1) job completion check** via atomic counter
- **Immutable task backup** for worker failure recovery
- **Early termination support** for search problems
- **Worker failure handling** via FailureDetector integration

### TaskExecutor

**Purpose**: Executes tasks on worker nodes using thread pool.

**Thread Pool Sizing:**
```
Pool Size = CPU Cores × CORE_MULTIPLIER
Default CORE_MULTIPLIER = 1
```

**Execution Flow:**
1. Receive task BATCH via RMI `executeTasks()`
2. Submit each task to local thread pool
3. Execute task with `ExecutionContext` (worker ID, hardware info)
4. Send result back to Leader via RMI `submitResults()`
5. Continue with next task (non-blocking)

---

## Advanced Features

### Early Termination

For search problems (e.g., password cracking), computation can stop as soon as one worker finds a result.

**Mechanism:**
1. Task returns `TaskResult.SUCCESS` (not `PARTIAL`)
2. TaskScheduler sets `JobContext.terminated = true`
3. JobManager checks `job.supportsEarlyTermination()`
4. Remaining tasks are cancelled (workers notified)
5. Partial results aggregated immediately

```mermaid
sequenceDiagram
    participant W1 as Worker 1
    participant W2 as Worker 2
    participant TS as TaskScheduler
    participant JM as JobManager
    
    W1->>TS: submitResults(Results)
    activate TS
    TS->>TS: Check: status == SUCCESS?
    TS->>TS: Set terminated = true
    TS->>TS: Cancel remaining tasks
    TS->>W2: cancelTasks()
    TS->>JM: onJobComplete(Results)
    deactivate TS
    
    activate JM
    JM->>JM: aggregateResults(partial)
    JM-->>User: JobResult
    deactivate JM
```

### Fault Tolerance

**Worker Failure Detection:**
1. `FailureDetector` (heartbeat monitor) detects worker crash
2. Notifies `TaskScheduler` via `onWorkerFailure(workerId)`
3. TaskScheduler looks up affected jobs in `workerIndex`
4. Retrieves orphaned tasks from `JobContext.assignments` (Cassaforte)
5. Reassigns tasks to healthy workers

**Task Timeout** (planned):
- TaskScheduler maintains task dispatch timestamps
- Periodic check for stale WORKING tasks
- Automatic reassignment after threshold

---

## Execution Context

Workers receive hardware information to optimize task execution:

```java
ExecutionContext {
    String workerId;              // "node-localhost-5002-123"
    String workerAddress;         // "192.168.1.10:5002"
    int availableCpuCores;        // 8
    long availableMemoryMB;       // 16384
    int suggestedParallelism;     // 8 (usually == CPU cores)
}
```

**Use Cases:**
- **Memory-bound tasks**: Adjust buffer sizes based on `availableMemoryMB`
- **CPU-intensive tasks**: Use `suggestedParallelism` for internal threading
- **Data locality**: Future feature - prefer workers with cached data

---

## Result Types

### TaskResult

```mermaid
graph LR
    TR[TaskResult] --> SUCCESS[SUCCESS<br/>Terminal result found]
    TR --> PARTIAL[PARTIAL<br/>Aggregatable contribution]
    TR --> NOT_FOUND[NOT_FOUND<br/>Search exhausted]
    TR --> FAILURE[FAILURE<br/>Exception occurred]
    TR --> CANCELLED[CANCELLED<br/>Early termination]
    TR --> WORKING[WORKING<br/>Execution in progress]
    
    style SUCCESS fill:#4caf50
    style PARTIAL fill:#2196f3
    style NOT_FOUND fill:#ff9800
    style FAILURE fill:#f44336
    style CANCELLED fill:#9e9e9e
    style WORKING fill:#ffeb3b
```

**Semantics:**
- **SUCCESS**: Terminal result, triggers early termination if supported
- **PARTIAL**: Non-terminal, contributes to aggregation (e.g., sum, count)
- **NOT_FOUND**: Completed successfully but found nothing
- **FAILURE**: Exception during execution
- **CANCELLED**: Task was cancelled before/during execution
- **WORKING**: Initial state when task is dispatched

### JobResult

```java
JobResult {
    String jobId;
    Status status;           // SUCCESS | NOT_FOUND | FAILURE | CANCELLED
    Object data;             // Aggregated result (flexible type)
    String errorMessage;     // Error details if FAILURE
    long executionTimeMs;    // Total execution time
    int totalTasks;          // Task statistics
    int completedTasks;
    int failedTasks;
}
```

---
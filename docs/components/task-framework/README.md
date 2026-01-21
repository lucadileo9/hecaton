# Task Framework - Component Documentation

This directory contains detailed documentation for all components of the Hecaton Task Framework, the distributed job execution system that powers the Hecaton cluster.

## 📚 Documentation Overview

The Task Framework documentation is organized into **three layers**:

### Layer 1: Architecture (High-Level)

- **[Architecture Overview](../../architecture/task-framework.md)** - Start here for framework philosophy, design patterns, and complete lifecycle

### Layer 2: Components (This Directory)

Detailed technical specifications for each framework component:

| Component | Purpose | Read When... |
|-----------|---------|--------------|
| **[Core Interfaces](core-interfaces.md)** | Job, Task, JobResult, TaskResult contracts | Building custom jobs or understanding data flow |
| **[Abstract Classes](abstract-classes.md)** | AbstractJob, AbstractTask helpers | Implementing new job types quickly |
| **[Splitting Strategies](splitting-strategies.md)** | How jobs divide into tasks | Choosing parallelization approach |
| **[Assignment Strategies](assignment-strategies.md)** | How tasks map to workers | Optimizing worker distribution |
| **[JobManager](job-manager.md)** | Orchestration & lifecycle | Understanding job submission flow |
| **[TaskScheduler](task-scheduler.md)** | Leader-side dispatching | Understanding distributed execution |
| **[TaskExecutor](task-executor.md)** | Worker-side execution | Understanding task execution engine |

### Layer 3: Examples & Tutorials (Coming Soon)

- **Creating Custom Jobs** - Step-by-step tutorial
- **Password Crack Example** - Deep dive into early termination
- **Sum Range Example** - Deep dive into partial result aggregation

---

## 📖 Component Details

### Core Interfaces

**File**: [core-interfaces.md](core-interfaces.md)  
        
Defines the fundamental contracts of the framework:

- **Job Interface**: How to define a distributed job (split, aggregate, early termination)
- **Task Interface**: How to define executable work units (execute, serialize)
- **JobResult**: Aggregated final results with metadata (duration, success rate)
- **TaskResult**: Individual task outcomes with status (SUCCESS, PARTIAL, NOT_FOUND, FAILURE, CANCELLED)
---

### Abstract Classes

**File**: [abstract-classes.md](abstract-classes.md)  

Template implementations to accelerate job development:

- **AbstractJob**: Pre-built splitting and assignment logic
  - Constructor-based strategy injection
  - Default implementations for common patterns
  - Built-in validation and error handling

- **AbstractTask**: Execution scaffolding
  - Serialization helpers
  - Common utility methods
  - Template method pattern support

**When to Use**: Prefer extending these over implementing raw interfaces (80% less boilerplate)

---

### Splitting Strategies

**File**: [splitting-strategies.md](splitting-strategies.md)  

How jobs divide into parallel tasks:

| Strategy | Use Case | Example |
|----------|----------|---------|
| **UniformSplitting** | Equal work per task | Password crack: 100M passwords → 100 tasks × 1M each |
| **WeightedSplitting** | Capability-aware distribution | Fast workers get 2× tasks vs slow workers |
| **DynamicSplitting** | Runtime adaptation | Start with few large tasks, subdivide if workers idle |

---

### Assignment Strategies

**File**: [assignment-strategies.md](assignment-strategies.md)  

How tasks map to worker nodes:

| Strategy | Distribution Pattern | Use Case |
|----------|---------------------|----------|
| **RoundRobinAssignment** | Circular allocation | Uniform tasks, equal workers |
| **TargetedAssignment** | Capability-based | Heterogeneous cluster (PC + laptop + Android) |

---

### JobManager

**File**: [job-manager.md](job-manager.md)  

Orchestrates the complete job lifecycle:

**Responsibilities**:
- Job submission entry point (`submitJob()`)
- Blocking API with `CountDownLatch` synchronization
- State management via 3 `ConcurrentHashMap`s
- Integration with strategies and TaskScheduler
- Result aggregation and early termination coordination

---

### TaskScheduler

**File**: [task-scheduler.md](task-scheduler.md)  

Leader-side distributed execution engine:

**Core Architecture** - "Cassaforte/Lavagna/Contatore":
- **Cassaforte** (assignments): Read-only backup for worker failure recovery (O(1) lookup)
- **Lavagna** (taskStatus): Write-heavy current state map (WORKING/SUCCESS/FAILED/etc)
- **Contatore** (pendingTasks): Atomic counter for O(1) completion check

**Responsibilities**:
- Dispatch tasks to workers via RMI (`executeTasks()`)
- Track task state changes (WORKING → SUCCESS/FAILED/CANCELLED)
- Aggregate results from workers (`submitResults()`)
- Handle worker failures (`onWorkerFailed()` - reassign orphaned tasks)
- Coordinate early termination (broadcast `cancelJob()` to workers)

---

### TaskExecutor

**File**: [task-executor.md](task-executor.md)  

Worker-side execution engine:

**Core Components**:
- **Fixed Thread Pool**: Size = CPU cores × 1 (optimal for CPU-bound tasks)
- **CompletionService**: Processes results as they complete (not submission order)
- **runningTasks**: Map Future → Task for cancellation support

**Responsibilities**:
- Receive tasks from Leader (`receiveTasks()`)
- Execute tasks in parallel using thread pool
- Process results as they complete (streaming, not batch)
- Detect early termination locally (cancel own remaining tasks)
- Handle remote cancellation from Leader (`cancelJob()`)
- Submit results back to Leader via RMI
---
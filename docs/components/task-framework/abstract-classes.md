# Abstract Classes

This document describes **AbstractJob** and **AbstractTask**, the convenient base classes that simplify the implementation of custom distributed jobs and tasks.

---

## Table of Contents

- [AbstractJob](#abstractjob)
  - [What It Provides](#what-it-provides)
  - [What You Must Implement](#what-you-must-implement)
  - [Constructor Patterns](#constructor-patterns)
  - [Complete Example](#complete-example-abstractjob)
- [AbstractTask](#abstracttask)
  - [What It Provides](#what-it-provides-1)
  - [What You Must Implement](#what-you-must-implement-1)
  - [Constructor Patterns](#constructor-patterns-1)
  - [Complete Example](#complete-example-abstracttask)
- [When to Use vs Direct Interface](#when-to-use-vs-direct-interface)
- [Advanced Patterns](#advanced-patterns)

---

## AbstractJob

### Overview

`AbstractJob` is a **base class** that implements the `Job` interface with sensible defaults. It eliminates boilerplate and lets you focus on the core logic: splitting work and aggregating results.

```java
package com.hecaton.task;

public abstract class AbstractJob implements Job {
    private static final long serialVersionUID = 1L;
    private String jobId;
    
    public AbstractJob() {
        setJobId();
    }
    
    // MUST override these
    public abstract List<Task> split(int numTasks);
    public abstract JobResult aggregateResults(List<TaskResult> results);
    
    // Already implemented with defaults
    @Override
    public String getJobId() { return jobId; }
    
    @Override
    public void setJobId() {
        this.jobId = "job-" + System.currentTimeMillis();
    }
    
    @Override
    public String getJobType() {
        return getClass().getSimpleName();
    }
    
    @Override
    public boolean supportsEarlyTermination() {
        return true;  // Most jobs support it
    }
    
    @Override
    public void onStart() { }
    
    @Override
    public void onComplete(JobResult result) { }
    
    @Override
    public long estimateExecutionTime(int workerCount) {
        return -1;  // Unknown
    }
}
```

---

### What It Provides

#### 1. Automatic Job ID Generation

The constructor calls `setJobId()` which generates a unique ID:

```java
public AbstractJob() {
    setJobId();  // Creates "job-<timestamp>"
}
```

**ID Format**: `"job-" + System.currentTimeMillis()`
- Example: `"job-1737500000000"`
- Guaranteed unique (within millisecond resolution)
- Monotonically increasing (sortable by creation time)

**Custom ID Generation** (override if needed):
```java
public class MyJob extends AbstractJob {
    @Override
    public void setJobId() {
        this.jobId = "custom-" + UUID.randomUUID();
    }
}
```

---

#### 2. Job Type Name

Returns simple class name for logging:

```java
@Override
public String getJobType() {
    return getClass().getSimpleName();
}
```

**Example Output**:
- `PasswordCrackJob` → `"PasswordCrackJob"`
- `SumRangeJob` → `"SumRangeJob"`

**Custom Type Name** (override for clarity):
```java
@Override
public String getJobType() {
    return "Password Crack (MD5, charset=" + charset.length() + ")";
}
```

---

#### 3. Early Termination Support

Default: `true` (assumes search-like jobs)

```java
@Override
public boolean supportsEarlyTermination() {
    return true;
}
```

**Override for aggregation jobs**:
```java
public class SumRangeJob extends AbstractJob {
    @Override
    public boolean supportsEarlyTermination() {
        return false;  // Must wait for all partial sums
    }
}
```

---

#### 4. Lifecycle Hooks

Empty implementations (override if needed):

```java
@Override
public void onStart() { }

@Override
public void onComplete(JobResult result) { }
```

**Example Usage**:
```java
@Override
public void onStart() {
    log.info("Starting password crack: hash={}, keyspace={}",
             targetHash.substring(0, 8) + "...",
             calculateTotalCombinations());
}

@Override
public void onComplete(JobResult result) {
    if (result.getStatus() == JobResult.Status.SUCCESS) {
        log.info("✓ Password found: {}", result.getData());
    } else {
        log.warn("✗ Password not found in keyspace");
    }
}
```

---

#### 5. Execution Time Estimation

Default: `-1` (unknown)

```java
@Override
public long estimateExecutionTime(int workerCount) {
    return -1;
}
```

**Override for better scheduling**:
```java
@Override
public long estimateExecutionTime(int workerCount) {
    long totalCombinations = calculateTotalCombinations();
    long hashesPerSecondPerWorker = 1_000_000;  // 1M hashes/sec
    long totalHashesPerSecond = hashesPerSecondPerWorker * workerCount;
    return (totalCombinations * 1000) / totalHashesPerSecond;
}
```

---

### What You Must Implement

#### 1. `split(int numTasks)` ⚠️ REQUIRED

Divide your job into independent tasks.

**Contract**:
- Return a list of `Task` objects
- Each task should be roughly equal work (unless using complexity hints)
- Tasks must be serializable and independent

---

#### 2. `aggregateResults(List<TaskResult> results)` ⚠️ REQUIRED

Combine task results into a final job result.

**Contract**:
- Return a `JobResult` summarizing all task results
- Handle partial successes, failures, cancellations appropriately

---

### Constructor Patterns

#### Basic Constructor (No Parameters)

```java
public class SimpleJob extends AbstractJob {
    public SimpleJob() {
        super();  // Calls AbstractJob(), generates jobId
    }
}
```

---

#### Parameterized Constructor

```java
public class PasswordCrackJob extends AbstractJob {
    private final String targetHash;
    private final String charset;
    private final int passwordLength;
    
    public PasswordCrackJob(String targetHash, String charset, int passwordLength) {
        super();  // Generate jobId first
        
        // Validate input
        if (targetHash == null || !targetHash.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Invalid MD5 hash");
        }
        
        this.targetHash = targetHash.toLowerCase();
        this.charset = charset;
        this.passwordLength = passwordLength;
    }
}
```

---

## AbstractTask

### Overview

`AbstractTask` is a **base class** that implements the `Task` interface with sensible defaults. It handles task ID management and provides default implementations for optional methods.

```java
package com.hecaton.task;

public abstract class AbstractTask implements Task {
    private static final long serialVersionUID = 1L;
    
    private final String jobId;
    private final String taskId;
    
    // Constructor with auto-generated UUID
    protected AbstractTask(String jobId) {
        this.jobId = jobId;
        this.taskId = UUID.randomUUID().toString();
    }
    
    // Constructor with explicit taskId
    protected AbstractTask(String jobId, String taskId) {
        this.jobId = jobId;
        this.taskId = taskId;
    }
    
    // MUST override this
    public abstract TaskResult execute();
    
    // Already implemented
    @Override
    public String getJobId() { return jobId; }
    
    @Override
    public String getTaskId() { return taskId; }
    
    @Override
    public TaskResult execute(ExecutionContext context) {
        return execute();  // Default: ignore context
    }
    
    @Override
    public void onCancel() { }
    
    @Override
    public int getEstimatedComplexity() {
        return 1;  // All tasks equal
    }
    
    @Override
    public String getTargetWorkerId() {
        return null;  // No preference
    }
}
```

---

### What It Provides

#### 1. Task ID Management

Two constructor options:

**Auto-generated UUID** (random ID):
```java
protected AbstractTask(String jobId) {
    this.jobId = jobId;
    this.taskId = UUID.randomUUID().toString();
}
```

**Explicit task ID** (sequential, readable):
```java
protected AbstractTask(String jobId, String taskId) {
    this.jobId = jobId;
    this.taskId = taskId;
}
```

**Comparison**:

| Approach | Pros | Cons | Use Case |
|----------|------|------|----------|
| UUID | Guaranteed unique | Hard to read | Distributed task generation |
| Explicit | Human-readable | Must ensure uniqueness | Sequential splitting |

**Example - Explicit IDs**:
```java
// In Job.split()
for (int i = 0; i < numTasks; i++) {
    tasks.add(new MyTask(getJobId(), "task-" + i, start, end));
}
// Result: task-0, task-1, task-2, ...
```

**Example - UUID**:
```java
// In Job.split()
for (int i = 0; i < numTasks; i++) {
    tasks.add(new MyTask(getJobId(), start, end));
}
// Result: 550e8400-e29b-41d4-a716-446655440000, ...
```

---

#### 2. Execution Context (Default Ignores It)

```java
@Override
public TaskResult execute(ExecutionContext context) {
    return execute();  // Calls simple execute()
}
```

**Override to use context**:
```java
@Override
public TaskResult execute(ExecutionContext context) {
    int threads = context.getSuggestedParallelism();
    long memoryMB = context.getAvailableMemoryMB();
    
    // Use hardware info for optimization
    return doWorkWithContext(threads, memoryMB);
}
```

---

#### 3. Cancellation Hook

Default: no-op

```java
@Override
public void onCancel() { }
```

**Override for cleanup**:
```java
private volatile FileInputStream stream;

@Override
public void onCancel() {
    if (stream != null) {
        try {
            stream.close();
        } catch (IOException e) {
            log.warn("Error closing stream", e);
        }
    }
}
```

---

#### 4. Complexity Hint

Default: all tasks equal complexity

```java
@Override
public int getEstimatedComplexity() {
    return 1;
}
```

**Override for load balancing**:
```java
@Override
public int getEstimatedComplexity() {
    // Larger ranges = more work
    return (int) (endIndex - startIndex) / 1000;
}
```

---

#### 5. Target Worker

Default: no preference

```java
@Override
public String getTargetWorkerId() {
    return null;
}
```

**Override for data locality**:
```java
private String cachedDataLocation;

@Override
public String getTargetWorkerId() {
    return cachedDataLocation;  // Prefer worker with cached data
}
```

---

### What You Must Implement

#### `execute()` ⚠️ REQUIRED

Perform the actual computation.

**Contract**:
- Return `TaskResult` (never null, never throw exceptions)
- Be thread-safe (no shared mutable state)
- Check interruption for long-running tasks
- Use appropriate result type (SUCCESS vs PARTIAL)

---

### Constructor Patterns

#### Basic Constructor (UUID)

```java
public class MyTask extends AbstractTask {
    private final String data;
    
    public MyTask(String jobId, String data) {
        super(jobId);  // Auto-generates UUID taskId
        this.data = data;
    }
}
```

---

#### Explicit Task ID Constructor

```java
public class MyTask extends AbstractTask {
    private final long start, end;
    
    public MyTask(String jobId, String taskId, long start, long end) {
        super(jobId, taskId);  // Explicit taskId
        this.start = start;
        this.end = end;
    }
}
```

---

## When to Use vs Direct Interface

### Use Abstract Classes When:

✅ You have a **standard job/task** with typical behavior
✅ You want to **minimize boilerplate**
✅ You're **learning** the framework

---

### Implement Interface Directly When:

✅ You need **custom job ID generation** logic
✅ You have **unusual lifecycle requirements**
✅ You want **complete control** over all methods

**Guideline**: Use abstract classes **95% of the time**. Direct interface only for special cases.

---
## Summary

### AbstractJob Checklist

- ✅ **Extend** `AbstractJob`
- ✅ **Implement** `split(int numTasks)`
- ✅ **Implement** `aggregateResults(List<TaskResult>)`
- ⚙️ **Override** `supportsEarlyTermination()` if aggregation job
- ⚙️ **Override** `estimateExecutionTime(int)` for better scheduling
- ⚙️ **Override** `onStart()` / `onComplete()` for logging

### AbstractTask Checklist

- ✅ **Extend** `AbstractTask`
- ✅ **Choose** UUID or explicit task ID constructor
- ✅ **Implement** `execute()`
- ✅ **Mark** non-serializable fields as `transient`
- ✅ **Check** `Thread.interrupted()` in long loops
- ⚙️ **Override** `execute(ExecutionContext)` if using hardware info
- ⚙️ **Override** `onCancel()` for resource cleanup
- ⚙️ **Override** `getEstimatedComplexity()` for load balancing

---
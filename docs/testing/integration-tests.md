# Testing: Integration Tests with ClusterConfig

This guide covers the first part of **end-to-end integration testing** with the new `ClusterConfig` architecture, including task distribution and execution.

---

## What We're Testing

- ✅ ClusterConfig creation with default strategies
- ✅ Leader startup without hardcoded parameters (uses config)
- ✅ JobManager initialization with strategies from config
- ✅ Workers joining cluster with consistent config
- ✅ Job submission and splitting (UniformSplitting)
- ✅ Task assignment (RoundRobinAssignment)
- ✅ Task execution on Workers
- ✅ Result aggregation on Leader
- ✅ Leader executing tasks locally (no RMI to itself)

---

## Available Tests

All tests are in `src/test/java/com/hecaton/manual/integration/`

### Integration Tests (`manual/integration/`)

| Test | Command | Description | Prerequisites |
|------|---------|-------------|---------------|
| **TestLeaderWithConfig** | `mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestLeaderWithConfig'` | Start Leader with ClusterConfig, monitor cluster size | None |
| **TestWorkerWithConfig** | `mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithConfig' '-Dexec.args=5002'` | Start Worker with config, join cluster | TestLeaderWithConfig running |
| **TestIntegratedCluster** | `mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestIntegratedCluster'` | Full end-to-end test with SumRangeJob(1-100) | None (starts Leader, then needs 2+ Workers) |

---

## Quick Start: Full Integration Test

### Complete Workflow (3 Terminals)

This test verifies the **entire distributed job execution pipeline** from submission to result aggregation,    without actual task execution on Workers, or better said, with Workers executing tasks locally using the taskExecutor component.

#### Terminal 1: Leader with Job Submission

```powershell
mvn test-compile
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestIntegratedCluster'
```

**Expected output:**
```
╔═══════════════════════════════════════════════════════════╗
║  FULL INTEGRATION TEST - Job Execution                   ║
╚═══════════════════════════════════════════════════════════╝

Config: ClusterConfig{electionAlgorithm=BULLY, splittingStrategy=UniformSplitting, assignmentStrategy=RoundRobinAssignment}

[OK] Node node-localhost-5001-xxx started as LEADER on port 5001
[OK] Cluster size: 1 node(s)

Waiting 10 seconds for Workers to join...
(Start 2-3 Workers in other terminals)

Cluster ready: 2 worker(s)

╔═══════════════════════════════════════════════════════════╗
║  Submitting Job: Sum(1-100)                              ║
╚═══════════════════════════════════════════════════════════╝

Expected result: 5050 (sum of 1 to 100)

[INFO] Job job-xxx split into 3 tasks using UniformSplitting
[INFO] Tasks assigned to 3 workers using RoundRobinAssignment
[INFO] Dispatching 1 tasks to worker=node-localhost-5001-xxx
[INFO] Dispatching 1 tasks to worker=node-localhost-5002-xxx
[INFO] Dispatching 1 tasks to worker=node-localhost-5003-xxx

╔═══════════════════════════════════════════════════════════╗
║  JOB COMPLETED                                            ║
╚═══════════════════════════════════════════════════════════╝
Job ID:   sum-1-100
Status:   [OK] SUCCESS
Expected: 5050
```

**What happens internally:**
1. Leader creates default `ClusterConfig` (BULLY + UniformSplitting + RoundRobin)
2. JobManager initialized with strategies from config
3. Waits 20 seconds for Workers to join
4. Submits `SumRangeJob(100)` - sum numbers from 1 to 100
5. Job split into N tasks (1 per worker) using UniformSplitting
6. Tasks assigned round-robin to all workers (including Leader)
7. Each node executes its task locally (Leader uses direct call, Workers use RMI)
8. Results aggregated: expected = 5050

---

#### Terminal 2: Worker 1

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithConfig' '-Dexec.args=5002'
```

**Expected output:**
```
╔═══════════════════════════════════════════════════════════╗
║  TEST - Worker with ClusterConfig (Port 5002)            ║
╚═══════════════════════════════════════════════════════════╝

--- Creating ClusterConfig ---
[OK] Config: ClusterConfig{electionAlgorithm=BULLY, splittingStrategy=UniformSplitting, assignmentStrategy=RoundRobinAssignment}

--- Starting Worker Node ---
[INFO] Node node-localhost-5002-xxx initialized on port 5002

--- Joining Cluster ---
[OK] Node node-localhost-5002-xxx joined cluster via localhost:5001

[OK] Worker running on port 5002
[OK] Sending heartbeats to Leader
  Press Ctrl+C to stop

[INFO] Received 1 tasks from Leader - executing...
  [EXEC] task-1 summing [34-67] = 1683
[INFO] Task task-1 completed successfully
[INFO] Submitted 1 results to Leader
```

**What happens internally:**
1. Worker creates same config as Leader (consistency)
2. Joins cluster via RMI to Leader at localhost:5001
3. Starts heartbeat monitoring
4. Receives tasks via RMI `executeTasks()`
5. Executes tasks locally (SumRangeTask.execute())
6. Submits results back to Leader via RMI

---

#### Terminal 3: Worker 2

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.integration.TestWorkerWithConfig' '-Dexec.args=5003'
```

**Expected output:** (same as Worker 1, different port and task range)

---

## Related Documentation

- [RMI Cluster Testing](rmi-cluster.md) - Basic cluster formation
- [Heartbeat Testing](heartbeat.md) - Failure detection
- [Troubleshooting Guide](troubleshooting.md) - Common issues
- [Architecture Overview](../architecture/overview.md) - System design

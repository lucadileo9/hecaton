# Hecaton - P2P Distributed Computing System
[![Java](https://img.shields.io/badge/Java-17-007396.svg?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
![RMI](https://img.shields.io/badge/RMI-007396?style=flat&logo=java&logoColor=white)

[![GitHub license](https://img.shields.io/github/license/lucadileo9/culinary-explorer)](https://github.com/lucadileo9/culinary-explorer/blob/main/LICENSE)

[![Made for Course](https://img.shields.io/badge/Course-Distributed%20Algorithm-lightgreny.svg)](https://github.com/lucadileo9/hecaton)
[![University](https://img.shields.io/badge/University-UNIMORE-blue.svg)](https://www.unimore.it/)

## Project Vision

**Hecaton** is a peer-to-peer distributed computing system that transforms a heterogeneous group of computers (PCs, laptops, servers, even smartphones) into a **virtual supercomputer**. The system is designed to be completely modular: today it can crack passwords, tomorrow it can calculate weather simulations or video rendering, without changing the network infrastructure.

### Key Features

* **Distributed and Decentralized**: No permanent single point of failure
* **Self-Organizing**: Automatic leader election in case of failures
* **Modular**: Change the type of calculation without modifying the infrastructure
* **Fault Tolerant**: Automatically handles nodes that go offline
* **Heterogeneous**: Works on Windows, Linux, macOS, Android (Termux)

---

## ⚡ Quick Start

### Prerequisites

- **Java 17** or higher ([Download](https://www.oracle.com/java/technologies/downloads/))
- **Maven 3.6+** ([Download](https://maven.apache.org/download.cgi))
- Minimum **4GB RAM** recommended for multi-node testing
- Available ports: **5001-5003** for localhost testing

### Build

```bash
mvn clean package
```

This creates an executable JAR: `target/hecaton.jar`

### Run Leader Node

```bash
java -jar target/hecaton.jar node start --mode LEADER --port 5001
```


### Run Worker Node (in separate terminal)

**Option 1: Auto-discovery** (recommended)
```bash
java -jar target/hecaton.jar node start --mode WORKER --port 5002
```
Worker automatically discovers Leader via UDP broadcast (5-10 seconds).

**Option 2: Explicit join**
```bash
java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001
```

### Submit Your First Job

Password cracking example (finds password for MD5 hash):

```bash
java -jar target/hecaton.jar job submit \
  --task PASSWORD_CRACK \
  --hash 5f4dcc3b5aa765d61d8327deb882cf99 \
  --charset abcdefghijklmnopqrstuvwxyz \
  --max-length 5 \
```

**Expected result**: `password` (MD5 of "password")

### Development Mode (Maven)

For rapid development without building JAR:

```bash
# Terminal 1: Start Leader
mvn exec:java '-Dexec.mainClass=com.hecaton.cli.Main' '-Dexec.args=node start --mode LEADER --port 5001'

# Terminal 2: Start Worker
mvn exec:java '-Dexec.mainClass=com.hecaton.cli.Main' '-Dexec.args=node start --mode WORKER --port 5002'
```

---

## ✅ Project Status

### Implemented & Working

- ✅ **Core RMI Cluster**: Leader/Worker coordination with automatic registry management
- ✅ **UDP Auto-Discovery**: Workers find Leader without manual configuration (9876 broadcast)
- ✅ **Heartbeat Monitoring**: Bidirectional health checks (Worker→Leader, Leader→Worker)
- ✅ **Leader Election**: Bully algorithm with automatic failover (<10s detection)
- ✅ **Task Framework**: Complete job distribution system with:
  - Pluggable splitting strategies (Uniform, Weighted, Dynamic)
  - Assignment strategies (RoundRobin, Targeted)
  - Early termination support (abort on first success)
  - Partial result aggregation
  - Worker failure detection and task reassignment
- ✅ **Concrete Tasks**: Password cracking (MD5/SHA-256), Sum calculation examples
- ✅ **Worker Failure Recovery**: Detection implemented, mid-job task reassignment done
- ✅ **CLI Interface**: Picocli-based commands for node management and job submission
- ✅ **Configuration System**: Builder pattern with strategy injection
- ✅ **Comprehensive Logging**: SLF4J + Logback with per-package levels

### ❌ Planned But Not Implemented

- ❌ **Docker Containerization**: Stage 2 deployment (no Dockerfile yet)
- ❌ **Physical Multi-Host Cluster**: Stage 3 deployment (not tested on real network)
- ❌ **Additional Task Types**: Monte Carlo simulations, prime factorization, video rendering

### 📍 Deployment Stages

| Stage | Status | Description |
|-------|--------|-------------|
| **Stage 1: Local Multi-JVM** | ✅ **Working** | Multiple terminals on localhost (tested extensively) |
| **Stage 2: Docker Compose** | ❌ Planned | Virtual network simulation (not implemented) |
| **Stage 3: Physical Cluster** | ❌ Planned | Cross-platform hybrid cluster (not tested) |

**Current Deployment**: Stage 1 only (localhost with different ports)

---

## Software Architecture

The system is structured into **three distinct logical levels**, each with specific responsibilities:

### 1. Network & Control Level (The Brain)

Manages the life of the cluster without knowing the details of the calculations.

**Technology**: Java RMI (Remote Method Invocation)

**Responsibilities**:

* **Discovery**: Allows new nodes to join the cluster
* **Heartbeat**: Continuous monitoring of node health
* **Leader Election**: Algorithm to automatically elect a coordinator
* **Fault Detection**: Detects failed nodes and reassigns work

### 2. Application Logic Level (The Extensible Engine)

Contains the intelligence of the work to be performed, designed with the **Strategy Pattern**.

**Components**:

* **`Task` Interface**: Generic contract for any type of calculation
* **Concrete Implementations**:
* `PasswordCrackTask`: Brute-force of MD5/SHA passwords
* *(Future)* `MonteCarloTask`, `VideoRenderTask`, etc.



**Advantages**: Changing the type of calculation only means replacing the Task class, not rewriting the network system.

### 3. User Interface Level (The Command Center)

Interface for human interaction with the system.

**CLI (Command Line Interface)** - Picocli-based commands:

```bash
# Starting a Leader node
java -jar target/hecaton.jar node start --mode LEADER --port 5001

# Starting a Worker node (auto-discovery)
java -jar target/hecaton.jar node start --mode WORKER --port 5002

# Submitting a job
java -jar target/hecaton.jar job submit \
  --task PASSWORD_CRACK \
  --hash 5f4dcc3b5aa765d61d8327deb882cf99 \
  --charset abcdefghijklmnopqrstuvwxyz \
  --max-length 5

# Checking cluster status
java -jar target/hecaton.jar cluster info --host localhost --port 5001
```

**See**: [docs/testing/CLI-TESTING.md](docs/testing/CLI-TESTING.md) for complete command reference

---

## Infrastructure Architecture

The system is designed to evolve through **three stages** of progressive deployment:

### Stage 1: Local Development ✅ **CURRENT**

**Hardware**: 1 PC

**Environment**: Multiple terminals/shells

**Network**: `localhost` (127.0.0.1)

**Differentiation**: Different ports (5001, 5002, 5003...)

**Purpose**: Rapid development, debugging, algorithm testing

**Status**: ✅ **Fully tested and documented** - See [docs/testing/rmi-cluster.md](docs/testing/rmi-cluster.md)

---

### Stage 2: Containerized Simulation ❌ **PLANNED**

**Hardware**: 1 PC

**Environment**: Docker Compose

**Network**: Virtual bridge network (172.18.0.x)

**Differentiation**: Different IPs, same port

**Purpose**: Simulating a real LAN network, clean deployment, process isolation

**Status**: ❌ Not implemented (no Dockerfile/docker-compose.yml yet)

---

### Stage 3: Physical Hybrid Cluster ❌ **PLANNED**

**Hardware**: PC + Laptop + Android Smartphone (Termux)

**Environment**: Native OS (Windows/Linux/Android)

**Network**: Real WiFi/LAN

**Differentiation**: Real IPs assigned by router/DHCP

## How It Works - Complete Workflow

### 1. Bootstrap (Cluster Genesis) ✅ IMPLEMENTED

1. **Node A starts** → Creates RMI registry, becomes provisional Leader
2. **Node B starts** → Auto-discovers Leader via UDP broadcast (or joins explicitly)
3. **Leader registers Worker B** → Updates cluster membership: [A, B]
4. **Heartbeat monitoring starts** → Bidirectional health checks (5-second intervals)

### 2. Job Submission ✅ IMPLEMENTED

1. **User submits job**: `PASSWORD_CRACK` with hash + parameters
2. **Leader queries cluster**: Checks available workers via `ClusterMembershipService`
3. **Job splitting**: Applies strategy (Uniform/Weighted/Dynamic) to divide keyspace
4. **Task assignment**: RoundRobin or Targeted strategy distributes tasks
5. **RMI dispatch**: Leader sends serialized `Task` objects to workers

### 3. Execution ✅ IMPLEMENTED

1. **Worker receives Task** via RMI (`NodeService.executeTask()`)
2. **TaskExecutor processes** → Thread pool (CPU cores) with `CompletionService`
3. **Intensive calculation** → `task.execute()` with interruption support
4. **Result streaming** → Completed tasks sent back to Leader immediately
5. **Early termination** → If success found, Leader broadcasts cancellation

### 4. Fault Tolerance ✅ PARTIALLY IMPLEMENTED

**Scenario 1: Worker fails during execution**
1. ✅ **Leader detects** → `FailureDetector` notices missing heartbeats (15s timeout)
2. ✅ **Callback triggered** → `TaskScheduler.onWorkerFailed(workerId)`
3. ✅ **Task reassignment** → Reassigns orphaned tasks to healthy workers

**Scenario 2: Leader fails during execution**
1. ✅ **Workers detect** → `HeartbeatMonitor` notices Leader death
2. ✅ **Election triggered** → Bully algorithm elects new Leader (<10s)
3. ❌ **Job recovery** → Not implemented (job state lost, no persistence)

### 5. Completion ✅ IMPLEMENTED

1. **All tasks complete** → Workers send final results to Leader
2. **Leader aggregates results** → Combines partial results into final output
3. **User notified** → CLI displays final result (e.g., cracked password)

---

## 🛠️ Tech Stack

* **Language**: Java 17+
* **Build Tool**: Maven 3.6+
* **Networking**: Java RMI (Remote Method Invocation)
* **Discovery**: UDP Broadcast (port 9876)
* **Serialization**: Java Serialization
* **CLI Framework**: Picocli 4.7+
* **Concurrency**: 
  - `ScheduledExecutorService` (heartbeat, monitoring)
  - `CompletionService` (task result streaming)
  - `CountDownLatch` (job synchronization)
  
### 📚 Documentation

The project includes **26 comprehensive documentation files** (500KB+ of detailed specs, diagrams, examples).
See below for quick navigation.

### 🎯 Quick Navigation

| Topic | Description | Link |
|-------|-------------|------|
| **Architecture Overview** | System design, component diagrams, deployment topology | [docs/architecture/overview.md](docs/architecture/overview.md) |
| **Task Framework** | Complete job execution system (8 detailed files) | [docs/components/task-framework/](docs/components/task-framework/) |
| **Node Component** | Leader/Worker lifecycle, state management | [docs/components/node.md](docs/components/node.md) |
| **Cluster Membership** | Worker registration, discovery protocol | [docs/components/cluster-membership.md](docs/components/cluster-membership.md) |
| **Heartbeat Monitoring** | Worker→Leader health checks | [docs/components/heartbeat.md](docs/components/heartbeat.md) |
| **Failure Detection** | Leader→Worker timeout detection | [docs/components/failure-detector.md](docs/components/failure-detector.md) |
| **Leader Election** | Bully algorithm implementation | [docs/components/election.md](docs/components/election.md) |
| **Testing Guide** | Multi-terminal test procedures, troubleshooting | [docs/testing/README.md](docs/testing/README.md) |
| **CLI Testing** | Complete command reference with examples | [docs/testing/CLI-TESTING.md](docs/testing/CLI-TESTING.md) |
| **Roadmap** | Incremental implementation phases | [ROADMAP.md](ROADMAP.md) |

---

## 🧪 Testing

Hecaton uses **manual integration tests** instead of JUnit because distributed RMI requires multi-process coordination across separate JVMs.

### Quick Test: Cluster Formation

**Terminal 1 - Start Leader**:
```bash
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Terminal 2 - Start Worker** (wait 2-3 seconds after Leader):
```bash
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestWorkerNode'
```

**Expected**: Both terminals show coordinated log messages (registration, heartbeat).

### Full Test Suite

See comprehensive testing documentation:
- **[docs/testing/README.md](docs/testing/README.md)** - Testing philosophy and rationale
- **[docs/testing/rmi-cluster.md](docs/testing/rmi-cluster.md)** - Multi-terminal cluster tests
- **[docs/testing/CLI-TESTING.md](docs/testing/CLI-TESTING.md)** - CLI command examples
- **[docs/testing/integration-tests.md](docs/testing/integration-tests.md)** - End-to-end scenarios

---

## 🛠️ Troubleshooting

For the troubleshooting of common issues (RMI registry problems, port conflicts, firewall issues), see **[docs/testing/troubleshooting.md](docs/testing/troubleshooting.md)**.

---

## 🤝 Contributing

This is an **educational/academic project** for the Distributed Algorithms course at UNIMORE. 

Contributions, suggestions, and improvements are welcome! Areas of interest:
- Docker deployment automation
- Cross-platform testing (Linux, macOS, Android/Termux)
- Job persistence and checkpointing
- Additional task implementations (Monte Carlo, prime factorization)
- Web-based monitoring dashboard

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE)

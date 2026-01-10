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

**CLI (Command Line Interface)**:

```bash
# Starting a node
java -jar hecaton-node.jar --port 5001 --leader

# Submitting a job
hecaton submit --task password-crack --hash a8f9c7e2b...

# Monitoring
hecaton status

```

*Note: This is entirely theoretical; the actual CLI syntax is yet to be finalized.*

---

## Infrastructure Architecture

The system evolves through **three stages** of progressive deployment:

### Stage 1: Local Development

**Hardware**: 1 PC

**Environment**: 3 terminals/shells

**Network**: `localhost` (127.0.0.1)

**Differentiation**: Different ports (5001, 5002, 5003)

**Purpose**: Rapid development, debugging, testing of algorithms

---

### Stage 2: Containerized Simulation

**Hardware**: 1 PC

**Environment**: Docker Compose

**Network**: Virtual bridge network (172.18.0.x)

**Differentiation**: Different IPs, same port

**Purpose**: Simulating a real LAN network, clean deployment, process isolation

---

### Stage 3: Physical Hybrid Cluster

**Hardware**: PC + Laptop + Android Smartphone (Termux)

**Environment**: Native OS (Windows/Linux/Android)

**Network**: Real WiFi (Mobile Hotspot)

**Differentiation**: Real IPs assigned by the router

**Purpose**: Tangible demonstration of a real heterogeneous distributed system

---

## Complete Workflow

### 1. Bootstrap (Cluster Genesis)

1. Node A starts -> Becomes provisional Leader
2. Node B starts with A's IP -> B registers with A
3. A updates member list: [A, B]

### 2. Job Submission

1. User: "Find password for hash XYZ"
2. Leader examines available nodes
3. Leader divides the problem (keyspace splitting)
4. Leader sends serialized Task objects via RMI

### 3. Execution

1. Worker receives Task
2. Worker executes `task.execute()` (intensive calculation)
3. Worker sends periodic updates to the Leader

### 4. Fault Tolerance

**Scenario**: Leader fails during execution

1. Worker detects lack of heartbeat
2. Worker initializes Leader Election
3. New Leader elected
4. New Leader reassigns orphan tasks

### 5. Completion

1. Worker finds solution
2. Worker calls `leaderService.solutionFound("password123")`
3. Leader notifies everyone: STOP (job completed)
4. Leader displays result to the user

---

## Tech Stack

* **Language**: Java 17+
* **Build Tool**: Maven / Gradle
* **Networking**: Java RMI
* **Serialization**: Java Serialization / Protocol Buffers
* **Containerization**: Docker, Docker Compose
* **Testing**: JUnit 5, Mockito
* **Logging**: SLF4J + Logback

---

## Additional Documentation

For documentation regarding the various parts of the project, please consult the `docs/` folder.

---

## Contributing

This is an educational/academic project. Suggestions and improvements are welcome!

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.




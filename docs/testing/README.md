# Hecaton Testing Documentation

This directory contains testing guides for the Hecaton distributed computing system.

## Testing Philosophy

Hecaton uses **manual integration tests** instead of traditional JUnit tests because:

### Why Not JUnit?

JUnit is excellent for **unit tests** (testing a single class/method in isolation), but Hecaton requires testing:
- ✅ **Multi-process communication** (RMI between separate JVMs)
- ✅ **Network behavior** (timeouts, connection failures)
- ✅ **Cluster dynamics** (Leader election, node failures)
- ✅ **Distributed state** (task distribution across nodes)

These scenarios require **multiple terminals running separate processes**, which JUnit cannot easily simulate.

### Our Approach: Manual Integration Tests

**Location**: `src/test/java/com/hecaton/manual/`

**Structure**:
```
manual/
├── node/          # Node lifecycle tests (startup, cluster join)
├── rmi/           # RMI communication tests (remote calls, serialization)
├── election/      # Leader election tests (future)
├── task/          # Task distribution tests (future)
└── monitor/       # Heartbeat monitoring tests (future)
```

**Execution**: Each test is a standalone Java class with a `main()` method:
```bash
mvn exec:java -Dexec.mainClass="com.hecaton.manual.<category>.<TestName>"
```

---

## Test Categories

### Current (Phase 1)

- **[RMI & Cluster Formation](rmi-cluster.md)** - Basic RMI communication, node registration, cluster setup
- **[Heartbeat Monitoring](heartbeat.md)** - Heartbeat-based fault detection between Leader and Workers
- **[Integration Tests](integration-tests.md)** - End-to-end testing with ClusterConfig, job submission, and task execution

### Future (Coming Soon)

- Just
- Wait
- Thanks
---

## Test Naming Convention

All test classes follow this pattern:

```java
package com.hecaton.manual.<category>;

/**
 * Test: <What is being tested>
 * 
 * How to run:
 *   mvn exec:java -Dexec.mainClass="com.hecaton.manual.<category>.<TestName>"
 * 
 * Prerequisites:
 *   - What needs to be running first
 * 
 * What this test:
 *   - Step 1: Description
 * 
 * Expected Output:
 *   - Key log lines to verify success
 */
public class Test<FeatureName> {
    public static void main(String[] args) throws Exception {
        // Test implementation
    }
}
```

**Naming rules**:
- Class name: `Test<FeatureName>` (e.g., `TestLeaderNode`, `TestRemotePing`)
- Package: `com.hecaton.manual.<category>` where category is the feature area
- Descriptive names based on **what** is tested, not "phase numbers"

---

## Running Tests

### Quick Start

```bash
# 1. Compile project
mvn clean compile

# 2. Run any test
mvn exec:java -Dexec.mainClass="com.hecaton.manual.<category>.<TestName>"
```

### Multi-Terminal Tests

Many tests require multiple processes (e.g., Leader + Workers):

```bash
# Terminal 1: Start Leader
mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode"

# Terminal 2: Start Worker
mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestWorkerNode"
```

---

## Troubleshooting

### Common Issues

**Port already in use**
```powershell
Get-Process java | Stop-Process -Force
```

**Connection refused**
- Ensure prerequisite tests are running (e.g., Leader before Worker)
- Wait 2-3 seconds for services to fully initialize

**RMI errors**
- Check firewall settings (RMI uses dynamic ports)
- Verify correct IP/hostname in test code

---

## Adding New Tests

When implementing new features:

1. **Create test class** in appropriate category package
2. **Update documentation** in `docs/testing/<feature>.md`
3. **Follow naming convention** (descriptive, not phase-based)
4. **Include clear prerequisites** in class javadoc
5. **Add expected output** in documentation

---

## Documentation Index

- [RMI & Cluster Formation](rmi-cluster.md) - Current implementation
- [Heartbeat Monitoring](heartbeat.md) - Current implementation
- Coming Soon... I hope 

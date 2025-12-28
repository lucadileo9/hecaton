# Testing: Heartbeat Monitoring Test Guide


Phase 1.4 implements **heartbeat-based fault detection** using periodic ping operations. Workers monitor the Leader's health and detect failures after 3 consecutive missed heartbeats (~15 seconds).

---

## What We're Testing
This guide covers the manual tests for heartbeat monitoring:
- ✅ Normal heartbeat operation (Leader alive)
- ✅ Leader failure detection (Leader goes down)


## Available Tests
All tests are in `src/test/java/com/hecaton/manual/`.
In this case there is only one test class for heartbeat monitoring.
TestLeaderHeartbeat.java starts a Worker that monitors the Leader's heartbeats. So you need to start a Leader node separately.
Sincerely: we do not need neither this test, we can just use TestWorkerNode to see the heartbeat logs. But having a dedicated test class makes it clearer.(I hope :-) 

## Quick Start: Heartbeat Monitoring Tests

### Step 1: Start Leader

**Terminal 1:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

### Step 2: Start Worker with Heartbeat Monitoring
**Terminal 2:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.monitor.TestLeaderHeartbeat'
```

**Expected Output (Terminal 2)**:
```
[OK] Worker joined cluster and monitoring Leader
[OK] Watch for 'Heartbeat OK' messages every 5 seconds

18:53:04.680 [HeartbeatMonitor-Leader Monitor] DEBUG c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat OK from node-localhost-5001-1766944361250
18:53:09.680 [HeartbeatMonitor-Leader Monitor] DEBUG c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat OK from node-localhost-5001-1766944361250
001-1766944361250
18:54:04.683 [HeartbeatMonitor-Leader Monitor] DEBUG c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat OK from node-localhost-5001-1766944361250
18:54:09.686 [HeartbeatMonitor-Leader Monitor] DEBUG c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat OK from node-localhost-5001-1766944361250
...
```

**Success Criteria**:
- ✅ Heartbeat logs appear every 5 seconds
- ✅ No WARN or ERROR messages
- ✅ Missed heartbeats counter stays at 0

### Step 3: Simulate Leader Failure
**Purpose**: Verify that Worker detects Leader death after 3 missed heartbeats (~15 seconds).
**Terminal 1**: Stop the Leader process (Ctrl+C)
**Terminal 2**: Watch for failure detection logs
```
**Expected Output Timeline (Terminal 2)**:
18:54:14.690 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat failed for unknown-node: ConnectException
18:54:14.694 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Missed heartbeat 1 of 3 for unknown-node
18:54:19.683 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat failed for unknown-node: ConnectException
18:54:19.687 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Missed heartbeat 2 of 3 for unknown-node
18:54:24.687 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat failed for unknown-node: ConnectException
18:54:24.692 [HeartbeatMonitor-Leader Monitor] WARN  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Missed heartbeat 3 of 3 for unknown-node
18:54:24.695 [HeartbeatMonitor-Leader Monitor] ERROR c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Node unknown-node declared DEAD after 3 missed heartbeats
18:54:26.697 [HeartbeatMonitor-Leader Monitor] INFO  c.hecaton.monitor.HeartbeatMonitor - [Leader Monitor] Heartbeat monitor stopped
18:54:26.705 [HeartbeatMonitor-Leader Monitor] ERROR com.hecaton.node.NodeImpl - [ALERT] LEADER IS DEAD! Node unknown-node no longer responding
18:54:26.707 [HeartbeatMonitor-Leader Monitor] ERROR com.hecaton.node.NodeImpl - [TODO] Leader election will be implemented in Phase 2
```


**Success Criteria**:
- ✅ Detection occurs after **exactly 3 missed heartbeats**
- ✅ Total time: **~15 seconds** (±1s for RMI timeout variance)
- ✅ `onLeaderDied()` callback executed
- ✅ HeartbeatMonitor automatically stops after declaring node dead
- ✅ No exceptions or crashes in Worker process

---
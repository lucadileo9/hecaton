# Troubleshooting Guide

Common issues encountered during Hecaton development and their solutions.

---

## RemoteException occurred in server thread;
### Problem

When starting a the registration test, the test fails.

**Error message:**
```
TEST FAILED: RemoteException occurred in server thread; nested exception is: 
        java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect

java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect
        at java.rmi/sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:392)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:200)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:712)
        at java.rmi/sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:587)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:828)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:705)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:399)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:704)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        at java.base/java.lang.Thread.run(Thread.java:842)
        at java.rmi/sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:304)
        at java.rmi/sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:280)
        at java.rmi/sun.rmi.server.UnicastRef.invoke(UnicastRef.java:165)
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:215) 
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:160)
        at jdk.proxy3/jdk.proxy3.$Proxy27.registerNode(Unknown Source)
        at com.hecaton.manual.rmi.TestNodeRegistration.main(TestNodeRegistration.java:59)
        at org.codehaus.mojo.exec.ExecJavaMojo$1.run(ExecJavaMojo.java:279)
        at java.base/java.lang.Thread.run(Thread.java:842)
Caused by: java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
        java.net.ConnectException: Connection refused: connect
        at java.rmi/sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:626)
        at java.rmi/sun.rmi.transport.tcp.TCPChannel.createConnection(TCPChannel.java:217)
        at java.rmi/sun.rmi.transport.tcp.TCPChannel.newConnection(TCPChannel.java:204)
        at java.rmi/sun.rmi.server.UnicastRef.invoke(UnicastRef.java:133)
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:215) 
        at java.rmi/java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:160)
        at jdk.proxy3/jdk.proxy3.$Proxy27.getId(Unknown Source)
        at com.hecaton.discovery.DiscoveryService.addNode(DiscoveryService.java:35)
        at com.hecaton.node.NodeImpl.registerNode(NodeImpl.java:129)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.base/java.lang.reflect.Method.invoke(Method.java:568)
        at java.rmi/sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:360)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:200)
        at java.rmi/sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:712)
        at java.rmi/sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:587)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:828)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:705)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:399)
        at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:704)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
        ... 1 more
Caused by: java.net.ConnectException: Connection refused: connect
        at java.base/sun.nio.ch.Net.connect0(Native Method)
        at java.base/sun.nio.ch.Net.connect(Net.java:579)
        at java.base/sun.nio.ch.Net.connect(Net.java:568)
        at java.base/sun.nio.ch.NioSocketImpl.connect(NioSocketImpl.java:593)
        at java.base/java.net.SocksSocketImpl.connect(SocksSocketImpl.java:327)
        at java.base/java.net.Socket.connect(Socket.java:633)
        at java.base/java.net.Socket.connect(Socket.java:583)
        at java.base/java.net.Socket.<init>(Socket.java:507)
        at java.base/java.net.Socket.<init>(Socket.java:287)
        at java.rmi/sun.rmi.transport.tcp.TCPDirectSocketFactory.createSocket(TCPDirectSocketFactory.java:40)
        at java.rmi/sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:620)
        ... 25 more
```

### Root Cause
I think it is a problem of duplicate RMI registries, maybe the leader already has one running on the xxx port, and when the worker tries to connect, it fails. In fact it happen only the SECOND time I run the test, because the first time everything works fine.

### Solution
I don't know exactly why, but killing all java processes before running the test again seems to solve the problem. Maybe becasue in this way I reset the leader state completely.

Since I don't think it is an important issue, I will not investigate further for now. Maybe thanks to the monitoring system we will never face this problem again.
I hope so for you, future Luca.

---




## Test Classes Not Found: exec:java Classpath Issue

### Problem

Running manual test classes with `mvn exec:java` fails:

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Error message:**
```
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.1.0:java
java.lang.ClassNotFoundException: com.hecaton.manual.node.TestLeaderNode
```

### Root Cause

The `exec-maven-plugin` by default uses **runtime classpath scope**, which includes only:
- `target/classes/` (compiled from `src/main/java/`)
- Runtime dependencies

It **does NOT include**:
- `target/test-classes/` (compiled from `src/test/java/`)

Since all manual tests are in `src/test/java/com/hecaton/manual/`, they are compiled to `target/test-classes/` and are invisible to exec:java.

### Solutions

#### ✅ Solution 1: Configure pom.xml (Applied)

Add `<classpathScope>test</classpathScope>` to exec-maven-plugin in `pom.xml`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <classpathScope>test</classpathScope>
    </configuration>
</plugin>
```

**Commands:**
```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Pros:**
- ✅ Clean commands - no extra `-D` parameters needed
- ✅ Works for all test classes automatically
- ✅ Configured once, works forever
- ✅ Team members don't need to know about classpath scope

**Cons:**
- ⚠️ Must remember to run `test-compile` phase before `exec:java`

#### ❌ Solution 2: Manual -Dexec.classpathScope=test (Not Recommended)

Specify classpath scope on every command:

```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode' '-Dexec.classpathScope=test'
```

**Pros:**
- ✅ No pom.xml changes needed

**Cons:**
- ❌ Verbose - every command needs the extra parameter
- ❌ Easy to forget
- ❌ Error-prone (typos in classpathScope)

#### ❌ Solution 3: Move Tests to src/main/java (CRAZY)

Move manual tests from `src/test/java/` to `src/main/java/`.

**Pros:**
- ✅ Works with default classpath scope

**Cons:**
- ❌ Test code pollutes production code
- ❌ Test classes included in final JAR
- ❌ Violates Maven standard directory layout
- ❌ Confusing for new developers

### Why I Chose Solution 1

**Reason:** Balance between convenience and correctness.

- **Keeps test code separate**: Tests stay in `src/test/java/` (Maven convention)
- **Minimal cognitive load**: Developers just run `mvn test-compile exec:java`, no need to remember extra parameters
- **One-time setup**: Configured once in pom.xml, benefits everyone
- **Explicit test-compile**: The need to run `test-compile` is actually beneficial - it makes it clear we're running test code

**Trade-off accepted:** Needing `test-compile` before `exec:java` is a small price for keeping the codebase clean.

---

## PowerShell Quote Hell: Maven Commands Not Working

### Problem

Running Maven commands with `-Dexec.mainClass` fails in PowerShell:

```powershell
# ❌ FAILS with "Unknown lifecycle phase" error
mvn exec:java -Dexec.mainClass="com.hecaton.manual.node.TestLeaderNode"
```

**Error message:**
```
[ERROR] Unknown lifecycle phase ".mainClass=com.hecaton.manual.node.TestLeaderNode"
```

### Root Cause

PowerShell interprets quotes differently than bash/cmd:
- **Curved quotes** `"` (from copy/paste): PowerShell sees them as text, not string delimiters
- **Straight double quotes** `"`: PowerShell expands variables inside them
- **Parameter parsing**: PowerShell tokenizes `-Dexec.mainClass="value"` incorrectly

### Solutions

#### ✅ Solution 1: Single Quotes (Recommended)

Use single quotes around the entire `-D` parameter:

```powershell
mvn exec:java '-Dexec.mainClass=com.hecaton.manual.node.TestLeaderNode'
```

**Why it works:** Single quotes in PowerShell preserve the string literally, no expansion or interpretation.

#### ✅ Solution 2: Escape with Nested Quotes

```powershell
mvn exec:java -D"exec.mainClass"="com.hecaton.manual.node.TestLeaderNode"
```

**Why it works:** Separates the parameter name and value into individually quoted parts.

#### ❌ Solution 3: Backtick Escaping (NOT Recommended)

```powershell
mvn exec:java -Dexec.mainClass=`"com.hecaton.manual.node.TestLeaderNode`"
```

**Why avoid:** Verbose, error-prone, hard to read.

### Related Fix: pom.xml Configuration

**Original problem:** Even with correct quotes, Maven executed `com.hecaton.cli.Main` instead of test classes.

**Root cause:** `pom.xml` had hardcoded mainClass in exec-maven-plugin, so it ignored `-Dexec.mainClass`.:

```xml
<!-- ❌ BEFORE: Hardcoded mainClass -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.hecaton.cli.Main</mainClass>
    </configuration>
</plugin>
```

**Solution:** Remove default mainClass to allow command-line override:

```xml
<!-- ✅ AFTER: Command-line mainClass only -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <!-- No default mainClass -->
</plugin>
```

---

## RMI Connection Issues

### Port Already in Use

**Symptoms:**
```
java.rmi.server.ExportException: Port already in use: 5001
```

**Cause:** Previous Java process still running or another application using the port.

**Solution 1 - Kill Java processes:**
```powershell
Get-Process java | Stop-Process -Force
```

**Solution 2 - Use different port:**
Change port in test code:
```java
NodeImpl node = new NodeImpl("localhost", 5004);  // Instead of 5001
```

---

### Connection Refused

**Symptoms:**
```
java.rmi.ConnectException: Connection refused to host: localhost; nested exception is:
    java.net.ConnectException: Connection refused: connect
```

**Cause:** Leader not running or not fully initialized.

**Solution:**
1. Start Leader first (TestLeaderNode)
2. Wait 2-3 seconds for RMI registry to initialize
3. Then start Worker/other tests

**Verification:** Leader terminal should show:
```
[OK} Node node-localhost-5001-... initialized on port 5001 with RMI registry
[OK} started as LEADER
```

---

## Build/Compilation Issues

### Class Not Found During Compilation

**Symptoms:**
```
[ERROR] cannot find symbol: class NodeService
```

**Cause:** Missing source files or incorrect package structure.

**Solution:**
```powershell
# Clean and recompile
mvn clean compile

# Verify package structure matches
# src/main/java/com/hecaton/rmi/NodeService.java
# src/main/java/com/hecaton/node/NodeImpl.java
```

---

### Test Class Not Found

**Symptoms:**
```
[ERROR] The specified mainClass doesn't exist
```

**Cause:** Test class not compiled or wrong package name.

**Solution:**
```powershell
# Recompile including test sources
mvn clean test-compile

# Verify test class exists in correct package
# src/test/java/com/hecaton/manual/node/TestLeaderNode.java
```

---

## Issue Resolution Timeline

| Date | Issue | Solution | Status |
|------|-------|----------|--------|
| 2025-12-27 | PowerShell quote parsing | Use single quotes `'-Dparam=value'` | ✅ Solved |
| 2025-12-27 | Maven executes wrong class | Remove hardcoded mainClass from pom.xml | ✅ Solved |

---

## Reporting New Issues

When encountering new issues:

1. **Check this document first** - Issue might already be documented
2. **Check terminal output** - Read full error message and stack trace
3. **Check logs** - Look in `logs/hecaton.log` for detailed errors
4. **Try clean build** - `mvn clean compile` often fixes compilation issues
5. **Document solution** - Add to this file once resolved

---
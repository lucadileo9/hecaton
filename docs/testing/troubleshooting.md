# Troubleshooting Guide

Common issues encountered during Hecaton development and their solutions.

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

#### ✅ Solution 1: Configure pom.xml (Recommended - Applied)

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
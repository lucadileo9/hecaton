# 🧪 Hecaton CLI - Testing Guide

This guide contains all the commands to systematically test the Hecaton CLI.

---

## 📦 Build and Setup

### 1. Compile and Package

```powershell
# Clean, compile and create executable JAR
mvn clean package

# Verify it was created
ls target/hecaton.jar
```

**Expected output**: `target/hecaton.jar` (fat JAR with all dependencies)

---

## 🧪 Basic Tests (Help and Version)

### 2. General Help

```powershell
# Show all available commands
java -jar target/hecaton.jar --help

# Short alternative
java -jar target/hecaton.jar -h
```

### 3. Help for Specific Commands

```powershell
# Help for node command
java -jar target/hecaton.jar node --help

# Help for node start
java -jar target/hecaton.jar node start --help

# Help for job submit
java -jar target/hecaton.jar job submit --help

# Help for cluster
java -jar target/hecaton.jar cluster --help
```

### 4. Version

```powershell
java -jar target/hecaton.jar version
```

---

## 🚀 Cluster Tests (Multi-Terminal)

### Scenario 1: Leader Only

**Terminal 1** (Leader):
```powershell
java -jar target/hecaton.jar node start --mode LEADER --port 5001
```

**Terminal 2** (Test cluster status):
```powershell
java -jar target/hecaton.jar cluster status
```

**Terminal 2** (Test cluster nodes):
```powershell
java -jar target/hecaton.jar cluster nodes
```

---

### Scenario 2: Leader + 1 Worker

**Terminal 1** (Leader - already running from Scenario 1):
```powershell
# Keep the Leader running
```

**Terminal 2** (Worker):
```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001
```

**Terminal 3** (Verify cluster):
```powershell
java -jar target/hecaton.jar cluster status
```

```powershell
java -jar target/hecaton.jar cluster nodes
```

---

### Scenario 3: Leader + 2 Workers

**Terminal 1** (Leader - already running)
**Terminal 2** (Worker 1 - already running)

**Terminal 3** (Worker 2):
```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5003 --join localhost:5001
```

**Terminal 4** (Verify cluster):
```powershell
java -jar target/hecaton.jar cluster status
java -jar target/hecaton.jar cluster nodes
```

---

## 📋 Job Submission Tests

### Job 1: Password Cracking

**Prerequisites**: Leader + at least 1 Worker running

**Separate terminal** (Job submission):
```powershell
java -jar target/hecaton.jar job submit --task PASSWORD_CRACK --hash 5f4dcc3b5aa765d61d8327deb882cf99 --charset abcdefghijklmnopqrstuvwxyz --max-length 6
```

**Note**: Hash `5f4dcc3b5aa765d61d8327deb882cf99` = password `"password"` in MD5

---

### Job 2: Sum Range

**Separate terminal**:
```powershell
java -jar target/hecaton.jar job submit --task SUM_RANGE --max_number 100
```

---

## 🧹 Cleanup

### Stop all nodes

```powershell
# Ctrl+C in each terminal with running nodes
```

**Alternative (forced kill)**:
```powershell
Get-Process java | Stop-Process -Force
```

---

## 🎯 Full Test Suite (Script)

### PowerShell Script for Automated Test

**Run**:
```powershell
.\test-cli.ps1
```
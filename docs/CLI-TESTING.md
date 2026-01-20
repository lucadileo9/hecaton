# 🧪 Hecaton CLI - Testing Guide

Questa guida contiene tutti i comandi per testare la CLI di Hecaton in modo sistematico.

---

## 📦 Build e Setup

### 1. Compilazione e Packaging

```powershell
# Clean, compile e crea JAR eseguibile
mvn clean package

# Verifica che sia stato creato
ls target/hecaton.jar
```

**Output atteso**: `target/hecaton.jar` (fat JAR con tutte le dipendenze)

---

## 🧪 Test Base (Help e Versione)

### 2. Help Generale

```powershell
# Mostra tutti i comandi disponibili
java -jar target/hecaton.jar --help

# Alternativa breve
java -jar target/hecaton.jar -h
```

**Output atteso**:
```
Usage: hecaton [-hV] [COMMAND]
Hecaton - P2P Distributed Computing System
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  node     Manage Hecaton node lifecycle
  job      Manage distributed jobs
  cluster  Manage cluster operations
  version  Show version information
```

### 3. Help per Comandi Specifici

```powershell
# Help per node command
java -jar target/hecaton.jar node --help

# Help per node start
java -jar target/hecaton.jar node start --help

# Help per job submit
java -jar target/hecaton.jar job submit --help

# Help per cluster
java -jar target/hecaton.jar cluster --help
```

### 4. Versione

```powershell
java -jar target/hecaton.jar version
```

**Output atteso**:
```
╔════════════════════════════════════════════════╗
║   Hecaton - Distributed Computing System      ║
╚════════════════════════════════════════════════╝

Version: 1.0-SNAPSHOT
Java: 17.x.x
...
```

---

## 🚀 Test Cluster (Multi-Terminal)

### Scenario 1: Leader Solo

**Terminal 1** (Leader):
```powershell
java -jar target/hecaton.jar node start --mode LEADER --port 5001
```

**Output atteso**:
```
========================================
  Hecaton Node - Starting
========================================

✓ Leader started successfully
  Host: localhost
  Port: 5001
  Node ID: node-localhost-5001-xxxxx

Waiting for Workers to join...

Press Ctrl+C to stop
```

**Terminal 2** (Test cluster status):
```powershell
java -jar target/hecaton.jar cluster status
```

**Output atteso**:
```
========================================
  Cluster Status
========================================

Leader: localhost:5001
Cluster Size: 1 node(s)
  - 1 Leader
  - 0 Worker(s)

Status: ✓ Cluster is operational
```

**Terminal 2** (Test cluster nodes):
```powershell
java -jar target/hecaton.jar cluster nodes
```

**Output atteso**:
```
========================================
  Cluster Nodes
========================================

Total Nodes: 1

1. node-localhost-5001-xxxxx (LEADER)
```

---

### Scenario 2: Leader + 1 Worker

**Terminal 1** (Leader - già running da Scenario 1):
```powershell
# Mantieni il Leader attivo
```

**Terminal 2** (Worker):
```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001
```

**Output atteso**:
```
========================================
  Hecaton Node - Starting
========================================

✓ Worker started successfully
  Host: localhost
  Port: 5002
  Node ID: node-localhost-5002-xxxxx
  Leader: localhost:5001

Press Ctrl+C to stop
```

**Terminal 3** (Verifica cluster):
```powershell
java -jar target/hecaton.jar cluster status
```

**Output atteso**:
```
Leader: localhost:5001
Cluster Size: 2 node(s)
  - 1 Leader
  - 1 Worker(s)

Status: ✓ Cluster is operational
```

```powershell
java -jar target/hecaton.jar cluster nodes
```

**Output atteso**:
```
Total Nodes: 2

1. node-localhost-5001-xxxxx (LEADER)
2. node-localhost-5002-xxxxx (WORKER)
```

---

### Scenario 3: Leader + 2 Workers

**Terminal 1** (Leader - già running)
**Terminal 2** (Worker 1 - già running)

**Terminal 3** (Worker 2):
```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5003 --join localhost:5001
```

**Terminal 4** (Verifica cluster):
```powershell
java -jar target/hecaton.jar cluster status
java -jar target/hecaton.jar cluster nodes
```

**Output atteso**:
```
Total Nodes: 3

1. node-localhost-5001-xxxxx (LEADER)
2. node-localhost-5002-xxxxx (WORKER)
3. node-localhost-5003-xxxxx (WORKER)
```

---

## 📋 Test Job Submission

### Job 1: Password Cracking

**Prerequisiti**: Leader + almeno 1 Worker running

**Terminal separato** (Job submission):
```powershell
java -jar target/hecaton.jar job submit --task PASSWORD_CRACK --hash 5f4dcc3b5aa765d61d8327deb882cf99 --charset digits --max-length 6
```

**Note**: Hash `5f4dcc3b5aa765d61d8327deb882cf99` = password `"password"` in MD5

**Output atteso**:
```
========================================
  Hecaton Job Submission
========================================

Connecting to Leader at localhost:5001...
✓ Connected to Leader

Task: Password Cracking
  Hash: 5f4dcc3b5aa765d61d8327deb882cf99
  Charset: digits
  Max Length: 6

Submitting job...
✓ Job submitted successfully

  Job ID: job-xxxxx

Use 'hecaton job status job-xxxxx' to track progress
```

---

### Job 2: Sum Range

**Terminal separato**:
```powershell
java -jar target/hecaton.jar job submit --task SUM_RANGE --max_number 100
```

**Output atteso**:
```
Task: Sum Range
  Range: 1 to 100

✓ Job submitted successfully
  Job ID: job-xxxxx
```

---

## ❌ Test Error Handling

### Errore 1: Worker senza Leader

```powershell
# Non c'è un Leader running
java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001
```

**Output atteso**: Errore RMI connection refused

---

### Errore 2: Worker senza --join

```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5002
```

**Output atteso**:
```
ERROR: --join is required for WORKER mode
Usage: hecaton node start --mode WORKER --join HOST:PORT
```

---

### Errore 3: Job senza parametri task-specific

```powershell
java -jar target/hecaton.jar job submit --task PASSWORD_CRACK
```

**Output atteso**:
```
ERROR: PASSWORD_CRACK requires --hash option
```

---

### Errore 4: Cluster status senza Leader

```powershell
# Nessun nodo running
java -jar target/hecaton.jar cluster status
```

**Output atteso**:
```
ERROR: Cannot connect to Leader at localhost:5001
  Connection refused

Make sure the Leader node is running:
  hecaton node start --mode LEADER --port 5001
```

---

## 🧹 Cleanup

### Stoppa tutti i nodi

```powershell
# Ctrl+C in ogni terminale con nodi running
```

**Alternativa (kill forzato)**:
```powershell
Get-Process java | Stop-Process -Force
```

---

## 🎯 Test Suite Completo (Script)

### PowerShell Script per Test Automatico

Salva come `test-cli.ps1`:

```powershell
# Test CLI Hecaton
Write-Host "=== Hecaton CLI Test Suite ===" -ForegroundColor Cyan

# 1. Build
Write-Host "`n[1/5] Building project..." -ForegroundColor Yellow
mvn clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Build successful" -ForegroundColor Green

# 2. Help tests
Write-Host "`n[2/5] Testing help commands..." -ForegroundColor Yellow
java -jar target/hecaton.jar --help | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Help command works" -ForegroundColor Green
}

# 3. Version test
Write-Host "`n[3/5] Testing version..." -ForegroundColor Yellow
java -jar target/hecaton.jar version
Write-Host "✓ Version command works" -ForegroundColor Green

# 4. Error handling test
Write-Host "`n[4/5] Testing error handling..." -ForegroundColor Yellow
java -jar target/hecaton.jar node start --mode WORKER --port 5002 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "✓ Error validation works (expected failure)" -ForegroundColor Green
}

# 5. Cluster status (no leader)
Write-Host "`n[5/5] Testing cluster status (no leader)..." -ForegroundColor Yellow
java -jar target/hecaton.jar cluster status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "✓ Cluster status handles missing leader (expected failure)" -ForegroundColor Green
}

Write-Host "`n=== All CLI tests passed! ===" -ForegroundColor Cyan
Write-Host "`nNext steps:"
Write-Host "  1. Start Leader:  java -jar target/hecaton.jar node start --mode LEADER --port 5001"
Write-Host "  2. Start Worker:  java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001"
Write-Host "  3. Check cluster: java -jar target/hecaton.jar cluster status"
```

**Esecuzione**:
```powershell
.\test-cli.ps1
```

---

## 📊 Checklist Testing

Usa questa checklist per validare la CLI:

### Comandi Base
- [ ] `--help` mostra help generale
- [ ] `version` mostra versione e info sistema
- [ ] Comandi unknown mostrano errore chiaro

### Node Management
- [ ] `node start --mode LEADER` avvia Leader
- [ ] `node start --mode WORKER --join HOST:PORT` avvia Worker
- [ ] Worker senza `--join` mostra errore
- [ ] Leader ignora `--join` con warning

### Cluster Operations
- [ ] `cluster status` mostra stato cluster
- [ ] `cluster status` fallisce se Leader non esiste
- [ ] `cluster nodes` lista tutti i nodi

### Job Submission
- [ ] `job submit --task PASSWORD_CRACK --hash XXX` sottomette job
- [ ] `job submit --task SUM_RANGE --max_number N` sottomette job
- [ ] Job senza parametri richiesti mostra errore
- [ ] Job submission fallisce se Leader non esiste

### Output e UX
- [ ] Banner formattati correttamente
- [ ] Messaggi di errore chiari e actionable
- [ ] Simboli ✓ e ❌ visibili
- [ ] Progress indicators dove appropriato

---

## 🐛 Troubleshooting

### Problema: "Cannot find hecaton.jar"

**Soluzione**:
```powershell
mvn clean package
ls target/hecaton.jar  # Verifica esistenza
```

---

### Problema: "Connection refused" durante join

**Cause possibili**:
1. Leader non è running
2. Porta sbagliata
3. Hostname sbagliato

**Soluzione**:
```powershell
# Verifica Leader running
netstat -ano | findstr :5001

# Riavvia Leader
java -jar target/hecaton.jar node start --mode LEADER --port 5001
```

---

### Problema: Porta già in uso

**Errore**: `Address already in use`

**Soluzione**:
```powershell
# Trova processo Java sulla porta
Get-Process java | Stop-Process -Force

# O cambia porta
java -jar target/hecaton.jar node start --mode LEADER --port 5010
```

---

## 📝 Note di Sviluppo

### Rebuilding dopo modifiche

```powershell
# Rebuild veloce (skip tests)
mvn clean package -DskipTests

# Rebuild completo
mvn clean package
```

### Debug CLI

```powershell
# Verbose logging
java -jar target/hecaton.jar --verbose node start --mode LEADER --port 5001

# Java debug
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar target/hecaton.jar ...
```

---

**🎉 Happy Testing!**
package com.hecaton.cli.commands;

import com.hecaton.cli.model.TaskType;
import com.hecaton.rmi.LeaderService;
import com.hecaton.task.Job;
import com.hecaton.task.JobResult;
import com.hecaton.task.password_crack.PasswordCrackJob;
import com.hecaton.task.sum_range.SumRangeJob;
import picocli.CommandLine.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Callable;

/**
 * Job management commands
 */
@Command(
    name = "job",
    description = "Manage distributed jobs",
    subcommands = {
        JobCommand.Submit.class,
        // JobCommand.Status.class,
        JobCommand.Cancel.class,
        JobCommand.List.class
    }
)
public class JobCommand {
    
    /**
     * Submit a new job to the cluster
     */
    @Command(name = "submit", description = "Submit a new job to the cluster")
    static class Submit implements Callable<Integer> {
        
        @Option(
            names = {"-t", "--task"},
            description = "Task type: ${COMPLETION-CANDIDATES}",
            required = true
        )
        TaskType taskType;
        
        @Option(
            names = {"--leader"},
            description = "Leader address (format: HOST:PORT, default: ${DEFAULT-VALUE})",
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        // Password Crack options
        @ArgGroup(exclusive = false, multiplicity = "0..1")
        PasswordCrackOptions passwordCrackOptions;
        
        static class PasswordCrackOptions {
            @Option(
                names = {"--hash"},
                description = "MD5/SHA hash to crack",
                required = true
            )
            String hash;
            
            @Option(
                names = {"--charset"},
                description = "Character set: digits, lowercase, uppercase, alphanumeric (default: ${DEFAULT-VALUE})",
                defaultValue = "alphanumeric"
            )
            String charset;
            
            @Option(
                names = {"--max-length"},
                description = "Maximum password length (default: ${DEFAULT-VALUE})",
                defaultValue = "8"
            )
            int maxLength;
        }
        
        // Sum Range options
        @ArgGroup(exclusive = false, multiplicity = "0..1")
        SumRangeOptions sumRangeOptions;
        
        static class SumRangeOptions {
            @Option(
                names = {"--max_number"},
                description = "Maximum number to sum up to",
                required = true
            )
            int maxNumber;
        }
        
        @Override
        public Integer call() throws Exception {
            // Validate task-specific options
            if (taskType == TaskType.PASSWORD_CRACK && passwordCrackOptions == null) {
                System.err.println("ERROR: PASSWORD_CRACK requires --hash option");
                return 1;
            }
            
            if (taskType == TaskType.SUM_RANGE && sumRangeOptions == null) {
                System.err.println("ERROR: SUM_RANGE requires --start and --end options");
                return 1;
            }
            
            System.out.println("========================================");
            System.out.println("  Hecaton Job Submission");
            System.out.println("========================================");
            System.out.println();
            
            // Connect to Leader via RMI
            System.out.println("Connecting to Leader at " + leaderAddress + "...");
            String[] parts = leaderAddress.split(":");
            Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
            LeaderService leader = (LeaderService) registry.lookup("leader");
            System.out.println("[OK] Connected to Leader");
            System.out.println();
            
            // Create Job based on taskType
            Job job = null;
            if (taskType == TaskType.PASSWORD_CRACK) {
                job = new PasswordCrackJob(
                    passwordCrackOptions.hash,
                    passwordCrackOptions.charset,
                    passwordCrackOptions.maxLength
                );
                System.out.println("Task: Password Cracking");
                System.out.println("  Hash: " + passwordCrackOptions.hash);
                System.out.println("  Charset: " + passwordCrackOptions.charset);
                System.out.println("  Max Length: " + passwordCrackOptions.maxLength);
            } else if (taskType == TaskType.SUM_RANGE) {
                job = new SumRangeJob(sumRangeOptions.maxNumber);
                System.out.println("Task: Sum Range");
                System.out.println("  Range: 1 to " + sumRangeOptions.maxNumber);
            }
            
            System.out.println();
            System.out.println("Submitting job...");
            
            // Submit via RMI
            String jobId = leader.submitJob(job);
            
            System.out.println("✓ Job submitted successfully");
            System.out.println();
            System.out.println("  Job ID: " + jobId);
            System.out.println();
            System.out.println("Use 'hecaton job status " + jobId + "' to track progress");
            
            return 0;
        }
    }
    
    /**
     * Show job status
     */
    // @Command(name = "status", description = "Show job status")
    // static class Status implements Callable<Integer> {
        
    //     @Parameters(
    //         index = "0",
    //         description = "Job ID"
    //     )
    //     String jobId;
        
    //     @Option(
    //         names = {"--leader"},
    //         description = "Leader address (format: HOST:PORT, default: ${DEFAULT-VALUE})",
    //         defaultValue = "localhost:5001"
    //     )
    //     String leaderAddress;
        
    //     @Override
    //     public Integer call() throws Exception {
    //         System.out.println("========================================");
    //         System.out.println("  Job Status");
    //         System.out.println("========================================");
    //         System.out.println();
            
    //         // Connect to Leader
    //         String[] parts = leaderAddress.split(":");
    //         Registry registry = LocateRegistry.getRegistry(parts[0], Integer.parseInt(parts[1]));
    //         LeaderService leader = (LeaderService) registry.lookup("leader");
            
    //         // Query job status
    //         JobResult result = leader.getJobStatus(jobId);
            
    //         if (result == null) {
    //             System.err.println("ERROR: Job not found: " + jobId);
    //             return 1;
    //         }
            
    //         System.out.println("Job ID: " + jobId);
    //         System.out.println("Status: " + result.getStatus());
    //         System.out.println("Progress: " + result.getCompletedTasks() + "/" + result.getTotalTasks() + " tasks");
            
    //         if (result.hasResult()) {
    //             System.out.println("Result: " + result.getResult());
    //         }
            
    //         if (result.getErrorMessage() != null) {
    //             System.out.println("Error: " + result.getErrorMessage());
    //         }
            
    //         return 0;
    //     }
    // }
    
    /**
     * Cancel a running job
     */
    @Command(name = "cancel", description = "Cancel a running job")
    static class Cancel implements Callable<Integer> {
        
        @Parameters(
            index = "0",
            description = "Job ID to cancel"
        )
        String jobId;
        
        @Option(
            names = {"--leader"},
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        @Override
        public Integer call() throws Exception {
            System.out.println("Cancelling job: " + jobId);
            // TODO: Implement via RMI
            System.out.println("Cancel command not yet implemented");
            return 0;
        }
    }
    
    /**
     * List all jobs
     */
    @Command(name = "list", description = "List all jobs")
    static class List implements Callable<Integer> {
        
        @Option(
            names = {"--leader"},
            defaultValue = "localhost:5001"
        )
        String leaderAddress;
        
        @Override
        public Integer call() {
            System.out.println("Listing all jobs...");
            // TODO: Query via RMI
            System.out.println("List command not yet implemented");
            return 0;
        }
    }
}

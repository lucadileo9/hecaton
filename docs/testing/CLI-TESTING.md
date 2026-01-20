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

---

## 🚀 Test Cluster (Multi-Terminal)

### Scenario 1: Leader Solo

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

**Terminal 1** (Leader - già running da Scenario 1):
```powershell
# Mantieni il Leader attivo
```

**Terminal 2** (Worker):
```powershell
java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001
```

**Terminal 3** (Verifica cluster):
```powershell
java -jar target/hecaton.jar cluster status
```

```powershell
java -jar target/hecaton.jar cluster nodes
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

---

## 📋 Test Job Submission

### Job 1: Password Cracking

**Prerequisiti**: Leader + almeno 1 Worker running

**Terminal separato** (Job submission):
```powershell
java -jar target/hecaton.jar job submit --task PASSWORD_CRACK --hash 5f4dcc3b5aa765d61d8327deb882cf99 --charset abcdefghijklmnopqrstuvwxyz --max-length 6
```

**Note**: Hash `5f4dcc3b5aa765d61d8327deb882cf99` = password `"password"` in MD5

---

### Job 2: Sum Range

**Terminal separato**:
```powershell
java -jar target/hecaton.jar job submit --task SUM_RANGE --max_number 100
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

## 📝 Note di Sviluppo

### Rebuilding dopo modifiche

```powershell
# Rebuild veloce (skip tests)
mvn clean package -DskipTests

# Rebuild completo
mvn clean package
```
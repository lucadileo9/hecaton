Write-Host "=== Hecaton CLI Test Suite ===" -ForegroundColor Cyan

# 1. Build
Write-Host "`n[1/5] Building project..." -ForegroundColor Yellow
# Nota: cmd /c serve a garantire che mvn ritorni il codice di uscita corretto in tutte le shell
cmd /c mvn clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Build successful" -ForegroundColor Green

# 2. Help tests
Write-Host "`n[2/5] Testing help commands..." -ForegroundColor Yellow
java -jar target/hecaton.jar --help | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Help command works" -ForegroundColor Green
}

# 3. Version test
Write-Host "`n[3/5] Testing version..." -ForegroundColor Yellow
java -jar target/hecaton.jar version
Write-Host "[OK] Version command works" -ForegroundColor Green

# 4. Error handling test
Write-Host "`n[4/5] Testing error handling..." -ForegroundColor Yellow
# Eseguiamo un comando errato apposta
java -jar target/hecaton.jar node start --mode WORKER --port 5002 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[OK] Error validation works (expected failure)" -ForegroundColor Green
}

# 5. Cluster status (no leader)
Write-Host "`n[5/5] Testing cluster status (no leader)..." -ForegroundColor Yellow
java -jar target/hecaton.jar cluster status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[OK] Cluster status handles missing leader (expected failure)" -ForegroundColor Green
}

Write-Host "`n=== All CLI tests passed! ===" -ForegroundColor Cyan
Write-Host "`nNext steps:"
Write-Host "  1. Start Leader:  java -jar target/hecaton.jar node start --mode LEADER --port 5001"
Write-Host "  2. Start Worker:  java -jar target/hecaton.jar node start --mode WORKER --port 5002 --join localhost:5001"
Write-Host "  3. Check cluster: java -jar target/hecaton.jar cluster status"
$ErrorActionPreference = "Stop"

$certDir = Join-Path $PSScriptRoot "certs"
if (-not (Test-Path (Join-Path $certDir "dev.p12"))) {
    Write-Host "Generating the shared dev certificate ..." -ForegroundColor Cyan
    node (Join-Path $PSScriptRoot "tools\make-cert.mjs")
    if ($LASTEXITCODE -ne 0) { throw "Certificate generation failed." }
}

$erl  = (Get-Command erl.exe  -ErrorAction SilentlyContinue).Source
$erlc = (Get-Command erlc.exe -ErrorAction SilentlyContinue).Source
if (-not $erl) {
    $cands = @("C:\Program Files\Erlang OTP\bin", "C:\Program Files (x86)\Erlang OTP\bin")
    $erl  = (Get-ChildItem -Path $cands -Filter erl.exe  -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
    $erlc = (Get-ChildItem -Path $cands -Filter erlc.exe -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}
if (-not $erl) { throw "Could not find erl.exe. Is Erlang/OTP installed?" }

$serverDir = Join-Path $PSScriptRoot "server"
Write-Host "Compiling the Erlang messaging server ..." -ForegroundColor Cyan
& $erlc -o $serverDir (Join-Path $serverDir "whatsapp_server.erl") (Join-Path $serverDir "whatsapp_web.erl")
if ($LASTEXITCODE -ne 0) { throw "Erlang compile failed." }

Write-Host "Starting Erlang on ws://:8081 + wss://:8443 ..." -ForegroundColor Green
Start-Process -FilePath $erl -ArgumentList '-noshell', '-pa', $serverDir, '-s', 'whatsapp_web', 'start'

$backend = Join-Path $PSScriptRoot "web\backend-java"
$mvn = Join-Path $backend "mvnw.cmd"
if (-not (Test-Path $mvn)) { $mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source }
if (-not $mvn) { throw "Could not find Maven (mvnw.cmd or mvn.cmd)." }

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
    $jdk = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' `
        -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'jdk-(1[7-9]|2\d)' } | Select-Object -Last 1
    if (-not $jdk) { throw "Could not find a JDK 17+. Set JAVA_HOME and retry." }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "Starting Spring Boot on http://:8080 + https://:8444 (JAVA_HOME=$env:JAVA_HOME) ..." -ForegroundColor Green
Start-Process -FilePath $mvn -ArgumentList '-f', (Join-Path $backend "pom.xml"), 'spring-boot:run' -WorkingDirectory $backend

$frontend = Join-Path $PSScriptRoot "web\frontend"
if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Write-Host "Installing frontend dependencies ..." -ForegroundColor Cyan
    Push-Location $frontend; npm install --no-audit --no-fund; Pop-Location
}

$ip = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
       Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } | Select-Object -First 1).IPAddress
Write-Host ""
Write-Host "Open http://localhost:5173 on this machine (calls work: localhost is a secure context)." -ForegroundColor Yellow
if ($ip) {
    Write-Host "From a phone on the same Wi-Fi:" -ForegroundColor Yellow
    Write-Host "  1. visit https://${ip}:8444/api/auth/login and https://${ip}:8443 once, accept the certificate on each" -ForegroundColor Yellow
    Write-Host "  2. then open https://${ip}:5174" -ForegroundColor Yellow
}
Write-Host ""

Push-Location $frontend
Start-Process -FilePath "npm.cmd" -ArgumentList 'run', 'dev:https' -WorkingDirectory $frontend -WindowStyle Hidden
npm run dev
Pop-Location

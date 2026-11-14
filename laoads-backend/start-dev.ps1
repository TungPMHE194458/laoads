# Chạy LaoAds BE profile dev (port 8095, context-path /api).
#
# Secret KHÔNG nằm trong repo. Thứ tự lấy giá trị:
#   1. Env var đã có sẵn trong shell (LAOADS_DB_PASSWORD, hoặc BOC_DB_PASSWORD dùng chung với BOC)
#   2. File .env.local.ps1 cạnh script này (gitignored) — copy từ .env.local.ps1.example rồi điền
#
# Mặc định kết nối DB Oracle dùng chung với BOC (10.120.138.131/stl_ptdl, user BOC) — xem application-dev.yml.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$localEnv = Join-Path $PSScriptRoot ".env.local.ps1"
if (Test-Path $localEnv) {
    . $localEnv
    Write-Host "Đã nạp $localEnv"
}

if (-not $env:LAOADS_DB_PASSWORD -and -not $env:BOC_DB_PASSWORD) {
    Write-Warning "Chưa có LAOADS_DB_PASSWORD / BOC_DB_PASSWORD — BE vẫn start (A+A+) nhưng không kết nối được DB."
}

$env:SPRING_PROFILES_ACTIVE = "dev"

$logDir = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Force $logDir | Out-Null
$logFile = Join-Path $logDir "be-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
Write-Host "Log: $logFile"

# -am: build kèm laoads-domain/infra/api từ source (tránh lấy JAR cũ trong ~/.m2 — multi-module pitfall của BOC).
# Các module không phải web có spring-boot.run.skip=true nên chỉ laoads-web chạy app.
.\mvnw.cmd -pl laoads-web -am spring-boot:run 2>&1 | Tee-Object -FilePath $logFile

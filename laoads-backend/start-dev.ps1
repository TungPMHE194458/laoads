# Chạy LaoAds BE profile dev (port 8095, context-path /api) với DB Oracle XE LOCAL (../docker-compose.dev.yml).
#
# Dev KHÔNG dùng DB dùng chung với BOC: application-dev.yml mặc định localhost:1521/XE (user LAOADS / laoads),
# không fallback BOC_DB_*, và DevDatabaseGuard chặn start nếu DB không phải localhost.
# Biến tuỳ chỉnh (JWT, VSA, port…) đặt trong .env.local.ps1 cạnh script này (gitignored) — copy từ .env.local.ps1.example.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$localEnv = Join-Path $PSScriptRoot ".env.local.ps1"
if (Test-Path $localEnv) {
    . $localEnv
    Write-Host "Đã nạp $localEnv"
}

# Bật container Oracle XE local (idempotent). BE vẫn start được nếu DB chưa lên — tự migrate khi DB sẵn sàng (ping mỗi 30s).
if (-not $env:LAOADS_DB_URL) {
    $composeFile = Join-Path $PSScriptRoot "..\docker-compose.dev.yml"
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $ErrorActionPreference = "Continue"
        docker compose -f $composeFile up -d
        $dockerOk = $LASTEXITCODE -eq 0
        $ErrorActionPreference = "Stop"
        if (-not $dockerOk) {
            Write-Warning "Không bật được DB local (Docker Desktop đã chạy chưa?). BE vẫn start và thử lại DB mỗi 30s."
        }
    } else {
        Write-Warning "Không thấy lệnh docker — cài Docker Desktop, hoặc tự chạy Oracle local rồi đặt LAOADS_DB_URL."
    }
} else {
    Write-Host "LAOADS_DB_URL đã đặt → không bật docker-compose.dev.yml: $env:LAOADS_DB_URL"
}

$env:SPRING_PROFILES_ACTIVE = "dev"

$logDir = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Force $logDir | Out-Null
$logFile = Join-Path $logDir "be-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
Write-Host "Log: $logFile"

# -am: build kèm laoads-domain/infra/api từ source (tránh lấy JAR cũ trong ~/.m2 — multi-module pitfall của BOC).
# Các module không phải web có spring-boot.run.skip=true nên chỉ laoads-web chạy app.
.\mvnw.cmd -pl laoads-web -am spring-boot:run 2>&1 | Tee-Object -FilePath $logFile

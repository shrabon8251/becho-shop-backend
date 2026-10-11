# env-preflight.ps1 — backend চালানোর আগে .env check করো
# Usage (PowerShell, backend folder থেকে):  .\env-preflight.ps1
#
# আজ (2026-10-08) একই bug-class ৩ বার ধরা পড়েছে:
#   REDIS_PORT=     খালি → boot crash (int-এ "" bind হয় না)
#   SSLCZ_SANDBOX=  খালি → boot crash (boolean-এ "" bind হয় না)
#   CORS_ORIGINS=   খালি → silent CORS block → frontend-এ "Failed to fetch"
# নিয়ম: এই ৩টার যেকোনোটা হয় real value দাও, নয়তো লাইনটা মুছে দাও।
# .env-এর বাকি সব variable খালি রাখা নিরাপদ (String-typed) — audit verified.

$ErrorActionPreference = "Stop"
$envFile = Join-Path (Get-Location) ".env"
if (-not (Test-Path $envFile)) {
  Write-Host "⚠️  .env পাওয়া যায়নি: $envFile" -ForegroundColor Yellow
  exit 1
}

$danger = @("REDIS_PORT", "SSLCZ_SANDBOX", "CORS_ORIGINS")
$lines = Get-Content $envFile
$bad = @()

foreach ($v in $danger) {
  $m = $lines | Where-Object { $_ -match "^\s*$v\s*=" } | Select-Object -First 1
  if ($null -ne $m -and $m -match "^\s*$v\s*=\s*(#.*)?$") { $bad += $v }
}

if ($bad.Count -gt 0) {
  Write-Host ""
  Write-Host "❌ এই variable-গুলো খালি রাখা যাবে না: $($bad -join ', ')" -ForegroundColor Red
  Write-Host "   Fix: .env-তে real value বসাও (যেমন CORS_ORIGINS=http://localhost:5173)"
  Write-Host "   অথবা লাইনটা মুছে দাও — default তখন apply হবে।"
  Write-Host "   তারপর backend restart করো।"
  Write-Host ""
  exit 1
}

Write-Host ""
Write-Host "✅ env pre-flight OK — backend start করতে পারো।" -ForegroundColor Green
Write-Host ""

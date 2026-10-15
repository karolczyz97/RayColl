# Skrypt do pakowania repozytorium RayColl w paczki dla modeli AI (np. ChatGPT, Claude).
# Uruchamia repomix z katalogu głównego repo, więc ścieżki w konfiguracjach są względem niego.
# Paczki trafiają do repomix/out/ (ignorowane przez git). -All pakuje całość jedną paczką.
param([switch]$All)
$ErrorActionPreference = 'Stop'
$repomix = 'repomix@1.4.2'
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Pack([string]$step, [string]$title, [string]$config) {
    Write-Host "`n[$step] $title" -ForegroundColor Yellow
    npx --yes $repomix -c "repomix/$config"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Błąd repomix ($config), kod $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Push-Location $root
try {
    New-Item -ItemType Directory -Force 'repomix/out' | Out-Null
    Write-Host "Generowanie paczek Repomix dla RayColl..." -ForegroundColor Cyan
    if ($All) {
        Invoke-Pack '1/1' 'Całe repozytorium w jednej paczce...' 'all.json'
    } else {
        Invoke-Pack '1/3' 'Część 1: Architektura i Logika (Core, Data, Domain, DI, Config)...' 'part1-logic.json'
        Invoke-Pack '2/3' 'Część 2: UI i Odtwarzanie (Compose, ViewModele, Playback, Res)...' 'part2-ui.json'
        Invoke-Pack '3/3' 'Część 3: Testy (Jednostkowe i Robolectric)...' 'part3-tests.json'
    }
    Write-Host "`nGotowe! Wygenerowane paczki:" -ForegroundColor Green
    Get-Item 'repomix/out/*.xml' | Select-Object Name, @{Name = "Rozmiar (KB)"; Expression = { [math]::Round($_.Length / 1KB, 1) } } | Format-Table -AutoSize
} finally {
    Pop-Location
}

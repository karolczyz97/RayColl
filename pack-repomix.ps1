# Skrypt do pakowania repozytorium RayColl w paczki dla modeli AI (np. ChatGPT, Claude)
$ErrorActionPreference = 'Stop'
$repomix = 'repomix@1.4.2'

function Invoke-Pack([string]$step, [string]$title, [string]$config) {
    Write-Host "`n[$step] $title" -ForegroundColor Yellow
    npx --yes $repomix -c $config
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Błąd repomix ($config), kod $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host "Generowanie paczek Repomix dla RayColl..." -ForegroundColor Cyan
Invoke-Pack '1/3' 'Część 1: Architektura i Logika (Core, Data, Domain, DI, Config)...' 'repomix.full-part1-logic.config.json'
Invoke-Pack '2/3' 'Część 2: UI i Odtwarzanie (Compose, ViewModele, Playback, Res)...' 'repomix.full-part2-ui.config.json'
Invoke-Pack '3/3' 'Część 3: Testy (Jednostkowe i Robolectric)...' 'repomix.full-part3-tests.config.json'

Write-Host "`nGotowe! Wygenerowane paczki:" -ForegroundColor Green
Get-Item repomix-full-part*.xml | Select-Object Name, @{Name="Rozmiar (KB)"; Expression={[math]::Round($_.Length/1KB, 1)}} | Format-Table -AutoSize

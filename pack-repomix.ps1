# Skrypt do pakowania repozytorium RayColl w bezpieczne paczki dla modeli AI (np. ChatGPT, Claude)
Write-Host "Generowanie paczek Repomix dla RayColl..." -ForegroundColor Cyan

Write-Host "`n[1/3] Pakowanie Części 1: Architektura i Logika (Core, Data, Domain, DI, Config)..." -ForegroundColor Yellow
npx repomix -c repomix.full-part1-logic.config.json

Write-Host "`n[2/3] Pakowanie Części 2: UI i Odtwarzanie (Compose, ViewModele, Playback, Res)..." -ForegroundColor Yellow
npx repomix -c repomix.full-part2-ui.config.json

Write-Host "`n[3/3] Pakowanie Części 3: Testy (Jednostkowe i Robolectric)..." -ForegroundColor Yellow
npx repomix -c repomix.full-part3-tests.config.json

Write-Host "`nGotowe! Wygenerowane paczki:" -ForegroundColor Green
Get-Item repomix-full-part*.xml | Select-Object Name, @{Name="Rozmiar (KB)"; Expression={[math]::Round($_.Length/1KB, 1)}} | Format-Table -AutoSize

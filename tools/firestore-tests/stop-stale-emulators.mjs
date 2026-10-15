// On Windows `firebase emulators:exec` can leave the Firestore emulator JVM running after SIGINT,
// which blocks the next run with "port taken". Stop only emulator JVMs still listening on our ports.
import {execFileSync} from 'node:child_process';

const PORTS = [8089, 9099];
if (process.platform === 'win32') {
  const script = `
    Get-NetTCPConnection -LocalPort ${PORTS.join(',')} -State Listen -ErrorAction SilentlyContinue |
      ForEach-Object { Get-CimInstance Win32_Process -Filter "ProcessId=$($_.OwningProcess)" } |
      Where-Object { $_.CommandLine -match 'cloud-firestore-emulator|firebase-tools' } |
      ForEach-Object { Stop-Process -Id $_.ProcessId -Force; "stopped stale emulator $($_.ProcessId)" }
    exit 0`;
  const out = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], {encoding: 'utf8'});
  if (out.trim()) console.log(out.trim());
}

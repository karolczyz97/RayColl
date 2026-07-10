// Łatka kompatybilności react-native-track-player 4.1.2 z RN 0.85 (Kotlin):
// 1) Arguments.fromBundle dostał adnotacje nullability — Track.originalItem
//    (Bundle?) wymaga null-safe wywołania (MusicModule.kt, 2 miejsca).
// 2) HeadlessJsTaskService.onBind ma teraz sygnaturę (Intent): IBinder? —
//    stara (Intent?) przestała nadpisywać metodę bazową (MusicService.kt).
// 3) Crash przy każdym starcie appki: metody @ReactMethod zdefiniowane jako
//    `fun x(...) = scope.launch { ... }` mają wywnioskowany typ zwracany Job
//    (nie Unit). Bridgeless interop na RN 0.85 wymaga returnType == void dla
//    metod asynchronicznych i rzuca TurboModuleInteropUtils$ParsingException
//    przy starcie modułu — cała appka pada. Fix przeniesiony z upstreamowej
//    wersji 5.0.0-alpha0 (helper `launchInScope`, ten sam komentarz w ich
//    źródle: "Bridgeless interop layer tries to pass the `Job` from
//    `scope.launch` to the JS side which causes an exception").
// Uruchamiane z postinstall; idempotentne. Do usunięcia, gdy RNTP wyda
// wersję kompatybilną z RN 0.85.
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const base = join(
  root,
  'node_modules/react-native-track-player/android/src/main/java/com/doublesymmetry/trackplayer',
);

const patches = [
  {
    file: join(base, 'module/MusicModule.kt'),
    replacements: [
      {
        from: 'callback.resolve(Arguments.fromBundle(musicService.tracks[index].originalItem))',
        to: 'callback.resolve(musicService.tracks[index].originalItem?.let { Arguments.fromBundle(it) })',
      },
      {
        from: `            else Arguments.fromBundle(
                musicService.tracks[musicService.getCurrentTrackIndex()].originalItem
            )`,
        to: `            else musicService.tracks[musicService.getCurrentTrackIndex()].originalItem
                ?.let { Arguments.fromBundle(it) }`,
      },
      {
        from: 'scope.launch {',
        to: 'launchInScope {',
      },
      {
        from: 'return@launch',
        to: 'return@launchInScope',
      },
      {
        from: `        callback.resolve(Arguments.fromBundle(musicService.getPlayerStateBundle(musicService.state)))
    }
}`,
        to: `        callback.resolve(Arguments.fromBundle(musicService.getPlayerStateBundle(musicService.state)))
    }

    // Bridgeless interop layer tries to pass the \`Job\` from \`scope.launch\` to the JS side
    // which causes an exception. We can work around this using a wrapper.
    private fun launchInScope(block: suspend () -> Unit) {
        scope.launch {
            block()
        }
    }
}`,
      },
    ],
  },
  {
    file: join(base, 'service/MusicService.kt'),
    replacements: [
      {
        from: 'override fun onBind(intent: Intent?): IBinder {',
        to: 'override fun onBind(intent: Intent): IBinder {',
      },
    ],
  },
];

let applied = 0;
let alreadyPatched = 0;

for (const { file, replacements } of patches) {
  if (!existsSync(file)) {
    console.error(`patch-track-player: missing file ${file}`);
    process.exit(1);
  }
  let content = readFileSync(file, 'utf8');
  for (const { from, to } of replacements) {
    if (content.includes(to)) {
      alreadyPatched += 1;
      continue;
    }
    if (!content.includes(from)) {
      console.error(
        `patch-track-player: pattern not found in ${file} — RNTP updated? Verify the patch is still needed.`,
      );
      process.exit(1);
    }
    content = content.replaceAll(from, to);
    applied += 1;
  }
  writeFileSync(file, content);
}

console.log(`patch-track-player: ${applied} applied, ${alreadyPatched} already patched`);

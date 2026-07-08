// Łatka kompatybilności react-native-track-player 4.1.2 z RN 0.85 (Kotlin):
// 1) Arguments.fromBundle dostał adnotacje nullability — Track.originalItem
//    (Bundle?) wymaga null-safe wywołania (MusicModule.kt, 2 miejsca).
// 2) HeadlessJsTaskService.onBind ma teraz sygnaturę (Intent): IBinder? —
//    stara (Intent?) przestała nadpisywać metodę bazową (MusicService.kt).
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

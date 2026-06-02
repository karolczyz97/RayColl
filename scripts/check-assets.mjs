#!/usr/bin/env node
 /**
  * Validates that all asset paths referenced in app.config.js exist on disk.
  *
  * Usage:
  *   node scripts/check-assets.mjs
  *
  * Exit code 0 — all assets present.
  * Exit code 1 — one or more assets missing.
  */

 import { existsSync } from 'node:fs';
 import { resolve, dirname } from 'node:path';
 import { fileURLToPath } from 'node:url';
 import { createRequire } from 'node:module';

 const __dirname = dirname(fileURLToPath(import.meta.url));
 const root = resolve(__dirname, '..');
 const require = createRequire(import.meta.url);

 const config = require('../app.config.js').expo;

 const assetPaths = [];

 // Main icon
 if (config.icon) assetPaths.push(config.icon);

 // iOS icon (directory + known contents)
 if (config.ios?.icon) {
   const iosIconDir = config.ios.icon;
   assetPaths.push(iosIconDir);
   assetPaths.push(`${iosIconDir}/icon.json`);
   assetPaths.push(`${iosIconDir}/Assets/grid.png`);
   assetPaths.push(`${iosIconDir}/Assets/expo-symbol 2.svg`);
 }

 // Web favicon
 if (config.web?.favicon) assetPaths.push(config.web.favicon);

 // Android adaptive icons
 if (config.android?.adaptiveIcon) {
   const ai = config.android.adaptiveIcon;
   if (ai.foregroundImage) assetPaths.push(ai.foregroundImage);
   if (ai.backgroundImage) assetPaths.push(ai.backgroundImage);
   if (ai.monochromeImage) assetPaths.push(ai.monochromeImage);
 }

 // Splash screen plugin
 const splashPlugin = config.plugins?.find(
   (p) => Array.isArray(p) && p[0] === 'expo-splash-screen',
 );
 if (splashPlugin) {
   const splashConfig = splashPlugin[1];
   if (splashConfig?.image) assetPaths.push(splashConfig.image);
 }

 let missing = 0;
 let ok = 0;

 for (const p of assetPaths) {
   const full = resolve(root, p);
   if (existsSync(full)) {
     ok++;
     console.log(`\x1b[32m✓\x1b[0m ${p}`);
   } else {
     missing++;
     console.log(`\x1b[31m✗ MISSING\x1b[0m ${p}`);
   }
 }

 console.log(`\n${ok} present, ${missing} missing (${assetPaths.length} checked)`);

 if (missing > 0) {
   process.exit(1);
 }

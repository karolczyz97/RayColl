const fs = require('fs');
const path = require('path');

// Keep in sync with the @react-native-voice/voice version pinned in package.json.
// If the installed version differs, this patch may no longer match the upstream
// build.gradle and must be reviewed instead of silently clobbering it.
const EXPECTED_VOICE_VERSION = '3.2.4';

const voiceDir = path.join(__dirname, '..', 'node_modules', '@react-native-voice', 'voice');
const targetPath = path.join(voiceDir, 'android', 'build.gradle');
const pkgPath = path.join(voiceDir, 'package.json');

function fail(message) {
  console.error(`patch-voice-gradle: ${message}`);
  process.exit(1);
}

const newGradleContent = `apply plugin: 'com.android.library'

def safeExtGet(prop, fallback) {
    rootProject.ext.has(prop) ? rootProject.ext.get(prop) : fallback
}

android {
    compileSdk safeExtGet('compileSdkVersion', 34)

    defaultConfig {
        minSdk safeExtGet('minSdkVersion', 21)
        targetSdk safeExtGet('targetSdkVersion', 34)
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation 'com.facebook.react:react-native:+'
    implementation 'androidx.annotation:annotation:1.6.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
`;

// Package not installed (e.g. a partial/web-only install) — nothing to patch.
if (!fs.existsSync(pkgPath)) {
  console.log('patch-voice-gradle: @react-native-voice/voice not installed; skipping.');
  process.exit(0);
}

const installedVersion = JSON.parse(fs.readFileSync(pkgPath, 'utf8')).version;
if (installedVersion !== EXPECTED_VOICE_VERSION) {
  fail(
    `expected @react-native-voice/voice@${EXPECTED_VOICE_VERSION} but found ${installedVersion}. ` +
      'Review scripts/patch-voice-gradle.js (and EXPECTED_VOICE_VERSION) before continuing.',
  );
}

if (!fs.existsSync(targetPath)) {
  fail(`build.gradle not found at ${targetPath}; the package layout may have changed.`);
}

if (fs.readFileSync(targetPath, 'utf8') === newGradleContent) {
  console.log('patch-voice-gradle: build.gradle already patched.');
  process.exit(0);
}

fs.writeFileSync(targetPath, newGradleContent, 'utf8');
console.log('patch-voice-gradle: successfully patched @react-native-voice/voice build.gradle.');

const fs = require('fs');
const path = require('path');

const targetPath = path.join(__dirname, '..', 'node_modules', '@react-native-voice', 'voice', 'android', 'build.gradle');

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

if (fs.existsSync(targetPath)) {
  fs.writeFileSync(targetPath, newGradleContent, 'utf8');
  console.log('Successfully patched @react-native-voice/voice build.gradle!');
} else {
  console.error('Could not find @react-native-voice/voice build.gradle to patch!');
}

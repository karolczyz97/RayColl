import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// klucz Firebase spoza repozytorium: local.properties (lokalnie) albo zmienna środowiskowa (CI / GitHub Secrets);
// bez klucza kompilują się testy i lint, ale APK (debug i release) się nie zbuduje — aplikacja bez klucza
// wywraca się przy starcie na inicjalizacji Firebase
val firebaseApiKey: String = providers.gradleProperty("firebase.apiKey").orNull ?: (
    localProperties.getProperty("firebase.apiKey")?.takeIf { it.isNotBlank() }
        ?: System.getenv("FIREBASE_API_KEY")
    ).orEmpty()

// opcjonalny podpis release z local.properties (release.storeFile, release.storePassword, release.keyAlias,
// release.keyPassword); bez tych wpisów release jest niepodpisany
val releaseSigning: Map<String, String>? = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .associateWith { localProperties.getProperty("release.$it").orEmpty() }
    .takeIf { values -> values.values.all { it.isNotBlank() } }

val requireFirebaseApiKey = tasks.register("requireFirebaseApiKey") {
    val key = firebaseApiKey
    doLast {
        if (key.isBlank()) {
            throw GradleException(
                "Brak klucza Firebase: dodaj firebase.apiKey=... do local.properties albo ustaw FIREBASE_API_KEY"
            )
        }
    }
}
// instalowalne paczki wymagają klucza; testy jednostkowe i lint nie
val tasksNeedingFirebaseKey = setOf("packageDebug", "packageRelease", "bundleDebug", "bundleRelease")
tasks.configureEach {
    if (name in tasksNeedingFirebaseKey) dependsOn(requireFirebaseApiKey)
}

android {
    namespace = "com.raycoll.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.raycoll.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // locale_config dla systemowego wyboru języka aplikacji (Android 13+)
        generateLocaleConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets.getByName("test").resources.srcDir("schemas")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.commons.csv)
    implementation(libs.commons.text)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.media3.session)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // bez bezpośrednich importów, ale podnosi androidx.test.ext:junit wciągany przez ui-test-junit4 (inaczej 1.1.5)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}

// Styl kodu: ktlint (reguły w .editorconfig). `./gradlew :app:ktlintCheck` / `:app:ktlintFormat`.
val ktlint: Configuration = configurations.create("ktlint")

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL)) }
    }
}

// ścieżki względem katalogu głównego repo: kod aplikacji i wszystkie skrypty Gradle
val ktlintSources = listOf("app/src/**/*.kt", "*.kts", "app/*.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Sprawdza styl Kotlin (ktlint)."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    workingDir = rootDir
    args(ktlintSources)
}

tasks.named("check") { dependsOn("ktlintCheck") }

// Lint/UAST widzi generowane źródła obu wariantów. Hilt musi skończyć ich zapis,
// zanim lint testów je otworzy (AGP 9.4 + Hilt 2.60, inaczej FileNotFoundException).
tasks.configureEach {
    if (name.startsWith("lintAnalyze") && name.endsWith("UnitTest")) {
        dependsOn(tasks.matching { it.name.startsWith("hiltJavaCompile") && !it.name.endsWith("UnitTest") })
    }
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Poprawia styl Kotlin (ktlint -F)."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    workingDir = rootDir
    args(listOf("-F") + ktlintSources)
}

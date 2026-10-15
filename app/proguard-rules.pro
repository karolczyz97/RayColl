# Reguły aplikacji dla R8. Media3, Firebase/Play Services, Room, Hilt, Credential Manager
# i kotlinx.serialization dostarczają własne reguły konsumenckie (META-INF/proguard,
# META-INF/com.android.tools) — globalne -keep dla tych bibliotek tylko powiększały APK.

# Commons CSV 1.14.1: opcjonalna adnotacja analizy SpotBugs, nieużywana podczas działania aplikacji.
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings

# kotlinx.serialization: serializery klas aplikacji (kroki trybów, kopie zapasowe, schemat chmury)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.raycoll.app.**$$serializer { *; }
-keepclassmembers class com.raycoll.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.raycoll.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

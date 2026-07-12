# Keep Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.raycoll.app.**$$serializer { *; }
-keepclassmembers class com.raycoll.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.raycoll.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Media3
-keep class androidx.media3.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Hilt
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

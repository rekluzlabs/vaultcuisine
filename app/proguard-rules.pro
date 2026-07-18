# VaultCuisine ProGuard rules
# Add project specific ProGuard rules here.

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rekluzlabs.vaultcuisine.**$$serializer { *; }
-keepclassmembers class com.rekluzlabs.vaultcuisine.** {
    *** Companion;
}
-keepclasseswithmembers class com.rekluzlabs.vaultcuisine.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.*

# Keep ML Kit models
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }


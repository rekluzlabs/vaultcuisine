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

# Tink / security-crypto (EncryptedSharedPreferences for BYOK key storage)
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
# EncryptedSharedPreferences uses reflection to instantiate key/value schemes
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Room DAOs — keep interfaces so generated _Impl classes resolve correctly
-keep interface com.rekluzlabs.vaultcuisine.**.*Dao { *; }
-keep class com.rekluzlabs.vaultcuisine.**.*Dao_Impl { *; }


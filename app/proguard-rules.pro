# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.sellernest.poreceiving.**$$serializer { *; }
-keepclassmembers class com.sellernest.poreceiving.** {
    *** Companion;
}
-keepclasseswithmembers class com.sellernest.poreceiving.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# EncryptedSharedPreferences (androidx.security.crypto) is backed by Tink, which
# does its own reflective lookups for cipher/key-manager implementations.
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

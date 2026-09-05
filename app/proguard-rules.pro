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

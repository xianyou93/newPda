# ProGuard rules for mfPda
-keep class com.mefront.mfPda.data.** { *; }
-keep class com.mefront.mfPda.net.** { *; }
-keepclassmembers class * {
    @org.json.* <fields>;
}
-keepattributes *Annotation*

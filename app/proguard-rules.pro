# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# HiveMQ MQTT Client
-keep class com.hivemq.client.** { *; }
-dontwarn com.hivemq.client.**

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }

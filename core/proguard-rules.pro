# Android 组件需要保留原始类名，供系统/Manifest/WorkManager 反射创建
-keep class com.push.core.service.PushService { *; }
-keep class com.push.core.service.BootReceiver { *; }
-keep class com.push.core.worker.MqttReconnectWorker { *; }

# Room / Proto / Parcelable 运行时元数据
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.push.core.proto.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# HiveMQ 反射与可选依赖告警抑制
-dontwarn com.hivemq.client.**
-keep class com.hivemq.client.** { *; }

# 对外公开的稳定 API：供业务方直接依赖
-keep class com.push.core.PushManager { public *; }
-keep class com.push.core.viewmodel.PushViewModel { public *; }
-keep class com.push.core.repository.MessageRepository { public *; }
-keep class com.push.core.model.PushMessage { public *; }
-keep class com.push.core.model.MessageFilter { public *; }
-keep class com.push.core.model.MessageType { public *; }
-keep class com.push.core.model.MessageStatus { public *; }
-keep class com.push.core.model.ConnectionStatus { public *; }
-keep class com.push.core.model.BrokerConfig { public *; }
-keep class com.push.core.model.UserSession { public *; }
-keep class com.push.core.model.LoginResult { public *; }
-keep class com.push.core.model.LogoutResult { public *; }
-keep class com.push.core.proto.** { *; }

# Parcelable / Kotlin 元信息
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

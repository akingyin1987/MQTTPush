package com.push.core.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 推送消息实体
 * 存储所有接收到的 MQTT 消息
 */
@Entity(tableName = "push_messages")
data class PushMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 消息来源主题 */
    val topic: String,

    /** 消息内容 */
    val payload: String,

    /** QoS 等级: 0=最多一次, 1=至少一次, 2=恰好一次 */
    val qos: Int = 0,

    /** 是否已读 */
    val isRead: Boolean = false,

    /** 是否已标记/重要 */
    val isStarred: Boolean = false,

    /** 消息类型: notification, alert, system, custom */
    val type: MessageType = MessageType.NOTIFICATION,

    /** 消息来源应用标识 */
    val sourceApp: String = "mqtt",

    /** 消息展示标题（可选，从 payload 解析） */
    val title: String = "",

    /** 消息展示内容（可选，从 payload 解析） */
    val content: String = "",

    /** 是否已展示过（防止重复通知） */
    val isNotified: Boolean = false,

    /** 接收时间戳（毫秒） */
    val receivedAt: Long = System.currentTimeMillis(),

    /** 过期时间戳（0=永不过期） */
    val expiresAt: Long = 0
)

/**
 * 消息类型枚举
 */
enum class MessageType(val value: String, val displayName: String) {
    NOTIFICATION("notification", "通知"),
    ALERT("alert", "告警"),
    SYSTEM("system", "系统"),
    CUSTOM("custom", "自定义");

    companion object {
        fun fromValue(value: String): MessageType {
            return entries.find { it.value == value } ?: CUSTOM
        }
    }
}

/**
 * 消息状态（用于 UI 观察）
 */
enum class MessageStatus {
    UNREAD,    // 未读
    READ,      // 已读
    STARRED,   // 已标记
    ALL        // 全部
}

/**
 * MQTT 连接状态
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
    data object Reconnecting : ConnectionStatus()
}

/**
 * MQTT Broker 配置
 */
@Parcelize
data class BrokerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 1883,
    val clientId: String = "push-client-${System.currentTimeMillis()}",
    val username: String? = null,
    val password: String? = null,
    /** 是否为 WebSocket 连接 */
    val isWebSocket: Boolean = false,
    val webSocketPath: String = "/mqtt",
    val cleanSession: Boolean = true,
    val keepAliveInterval: Int = 60,
    /** 自动重连 */
    val autoReconnect: Boolean = true,
    /** 连接超时（秒） */
    val connectionTimeout: Int = 30
) : Parcelable

/**
 * 主题订阅配置
 */
data class Subscription(
    val topic: String,
    val qos: Int = 0,
    val alias: String = "",  // 显示别名
    val enabled: Boolean = true
)

/**
 * 消息过滤器
 */
data class MessageFilter(
    val status: MessageStatus = MessageStatus.ALL,
    val type: MessageType? = null,
    val topic: String? = null,
    val keyword: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

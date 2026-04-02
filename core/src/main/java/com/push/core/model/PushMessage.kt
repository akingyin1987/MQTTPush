package com.push.core.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 单条推送消息实体。
 *
 * 这个模型同时承担三类职责：
 * 1. Room 持久化实体：落库后可支持消息中心、未读数、星标等功能。
 * 2. UI 展示模型：标题、正文、类型、已读状态都直接供界面使用。
 * 3. 通知去重依据：通过 [isNotified] 避免同一条消息重复弹通知。
 */
@Entity(tableName = "push_messages")
data class PushMessage(
    /** 本地数据库主键，仅在本地存储后生成。 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** MQTT 原始 topic，用于回溯消息来源和过滤。 */
    val topic: String,

    /** MQTT 原始 payload，保留完整文本，方便调试与二次解析。 */
    val payload: String,

    /** QoS 等级：0 = 最多一次，1 = 至少一次，2 = 恰好一次。 */
    val qos: Int = 0,

    /** 用户是否已读，用于消息中心和未读角标。 */
    val isRead: Boolean = false,

    /** 是否被用户标星，常用于重要消息置顶或稍后处理。 */
    val isStarred: Boolean = false,

    /** 归类后的业务消息类型。 */
    val type: MessageType = MessageType.NOTIFICATION,

    /** 消息来源应用标识，适用于多业务线或多端聚合场景。 */
    val sourceApp: String = "mqtt",

    /** 供列表和通知栏展示的标题。 */
    val title: String = "",

    /** 供列表摘要展示的正文。 */
    val content: String = "",

    /** 是否已经展示过系统通知，避免重进页面后重复提醒。 */
    val isNotified: Boolean = false,

    /** 收到消息的本地时间戳（毫秒）。 */
    val receivedAt: Long = System.currentTimeMillis(),

    /** 业务过期时间；0 表示永不过期。 */
    val expiresAt: Long = 0
)

/**
 * 业务消息类型。
 *
 * - [NOTIFICATION]：普通通知，如审批、公告、聊天提醒
 * - [ALERT]：告警类消息，如设备异常、风控事件
 * - [SYSTEM]：系统内部消息，如连接状态、版本提醒
 * - [CUSTOM]：无法识别或业务自定义的扩展类型
 */
enum class MessageType(val value: String, val displayName: String) {
    NOTIFICATION("notification", "通知"),
    ALERT("alert", "告警"),
    SYSTEM("system", "系统"),
    CUSTOM("custom", "自定义");

    companion object {
        /** 从服务端下发的字符串安全映射到本地枚举，未知值自动降级为 [CUSTOM]。 */
        fun fromValue(value: String): MessageType {
            return entries.find { it.value == value } ?: CUSTOM
        }
    }
}

/**
 * 消息读取状态筛选。
 *
 * 这个枚举不直接写回数据库，而是作为 [MessageFilter] 的输入，
 * 由仓库层转换成具体 SQL 查询条件。
 */
enum class MessageStatus {
    UNREAD,
    READ,
    STARRED,
    ALL
}

/**
 * MQTT 连接状态。
 *
 * UI 层只需要观察这个密封类，就可以统一处理“连接中 / 已连接 / 重连中 / 错误”等展示逻辑。
 */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
    data object Reconnecting : ConnectionStatus()
}

/**
 * Broker 连接参数。
 *
 * 该模型会通过 Intent 在 `PushManager -> PushService / Worker` 间传递，
 * 因此保留 Parcelable 能力。当前示例覆盖了最常见的 TCP 直连、账号密码鉴权、
 * 自动重连和 KeepAlive 控制等场景。
 */
@Parcelize
data class BrokerConfig(
    /** Broker 主机名或 IP。 */
    val host: String = "127.0.0.1",
    /** Broker 端口，MQTT TCP 默认 1883。 */
    val port: Int = 1883,
    /** 客户端唯一标识；建议业务层按设备/用户维度生成。 */
    val clientId: String = "push-client-${System.currentTimeMillis()}",
    /** 可选用户名；无鉴权场景可为空。 */
    val username: String? = null,
    /** 可选密码；如使用 token 鉴权，可由上层透传。 */
    val password: String? = null,
    /** 是否通过 WebSocket 建连。 */
    val isWebSocket: Boolean = false,
    /** WebSocket 路径，仅 WebSocket 场景生效。 */
    val webSocketPath: String = "/mqtt",
    /** 是否使用 clean session / clean start 语义。 */
    val cleanSession: Boolean = true,
    /** KeepAlive 心跳间隔（秒）。 */
    val keepAliveInterval: Int = 60,
    /** 是否允许客户端自动重连。 */
    val autoReconnect: Boolean = true,
    /** 建连超时时间（秒）。 */
    val connectionTimeout: Int = 30
) : Parcelable

/**
 * 消息查询过滤条件。
 *
 * 示例：
 * - 消息中心默认列表：保持默认值即可
 * - 只看告警：`type = MessageType.ALERT`
 * - 只看某个 topic 前缀：`topic = "push/app1/device"`
 * - 搜索关键字：`keyword = "离线"`
 */
data class MessageFilter(
    /** 按已读/星标状态筛选。 */
    val status: MessageStatus = MessageStatus.ALL,
    /** 按业务类型筛选。 */
    val type: MessageType? = null,
    /** 按 topic 前缀筛选。 */
    val topic: String? = null,
    /** 按 payload / title 模糊搜索。 */
    val keyword: String? = null,
    /** 预留：开始时间，当前 UI 暂未启用。 */
    val startTime: Long? = null,
    /** 预留：结束时间，当前 UI 暂未启用。 */
    val endTime: Long? = null,
    /** 分页大小。 */
    val limit: Int = 50,
    /** 分页偏移量。 */
    val offset: Int = 0
)

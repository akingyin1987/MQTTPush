package com.push.core.model

/**
 * Push 推送配置
 * 
 * 统一配置入口，包含：
 * - 主题生成器 (TopicGenerator)
 * - 消息解析器 (MessageParser)
 * - 连接参数
 * - 业务参数
 * 
 * 使用方式：
 * ```kotlin
 * // 默认配置
 * val config = PushConfig()
 * 
 * // 自定义配置
 * val config = PushConfig(
 *     topicGenerator = CustomPrefixTopicGenerator("myapp/v2"),
 *     messageParser = JsonMessageParser(),
 *     appId = "myapp"
 * )
 * 
 * // 应用配置
 * PushManager.getInstance(context).setConfig(config)
 * ```
 */
data class PushConfig(
    
    // ── 主题生成器 ───────────────────────────────────────────────────────
    
    /** 主题生成器，默认 DefaultTopicGenerator */
    val topicGenerator: TopicGenerator = DefaultTopicGenerator(),
    
    // ── 消息解析器 ───────────────────────────────────────────────────────
    
    /** 消息解析器，默认 JsonMessageParser */
    val messageParser: MessageParser = JsonMessageParser(),
    
    // ── 应用标识 ─────────────────────────────────────────────────────────
    
    /** 应用 ID，用于构建主题前缀 */
    val appId: String = "app1",
    
    // ── 连接参数 ─────────────────────────────────────────────────────────
    
    /** 默认 MQTT 端口 */
    val defaultMqttPort: Int = 1883,
    
    /** 默认 WebSocket 端口 */
    val defaultWebSocketPort: Int = 8083,
    
    /** 默认 KeepAlive（秒） */
    val defaultKeepAlive: Int = 60,
    
    /** 是否默认订阅广播 */
    val defaultSubscribeBroadcast: Boolean = true,
    
    // ── 重连参数 ─────────────────────────────────────────────────────────
    
    /** 最大重连次数 */
    val maxReconnectAttempts: Int = 10,
    
    /** 重连初始延迟（毫秒） */
    val reconnectInitialDelay: Long = 1000L,
    
    /** 重连最大延迟（毫秒） */
    val reconnectMaxDelay: Long = 30000L,
    
    // ── 消息参数 ─────────────────────────────────────────────────────────
    
    /** 消息列表默认加载条数 */
    val messagePageSize: Int = 50,
    
    /** 顶部通知条显示条数 */
    val topNotificationCount: Int = 5,
    
    /** 消息过期时间（毫秒），0 = 永不过期 */
    val messageExpireTime: Long = 0L,
    
    // ── 通知参数 ─────────────────────────────────────────────────────────
    
    /** 通知 Channel ID */
    val notificationChannelId: String = "mqtt_push_channel",
    
    /** 通知 Channel 名称 */
    val notificationChannelName: String = "MQTT 推送",
    
    /** 通知 ID */
    val notificationId: Int = 1001,
    
    // ── 调试参数 ─────────────────────────────────────────────────────────
    
    /** 是否开启调试日志 */
    val debugMode: Boolean = false,
    
    /** 是否打印 MQTT 原始消息 */
    val logRawMessage: Boolean = true
    
) {
    
    /**
     * 创建 Builder 用于链式配置
     */
    class Builder {
        private var topicGenerator: TopicGenerator = DefaultTopicGenerator()
        private var messageParser: MessageParser = JsonMessageParser()
        private var appId: String = "app1"
        private var defaultMqttPort: Int = 1883
        private var defaultWebSocketPort: Int = 8083
        private var defaultKeepAlive: Int = 60
        private var defaultSubscribeBroadcast: Boolean = true
        private var maxReconnectAttempts: Int = 10
        private var reconnectInitialDelay: Long = 1000L
        private var reconnectMaxDelay: Long = 30000L
        private var messagePageSize: Int = 50
        private var topNotificationCount: Int = 5
        private var messageExpireTime: Long = 0L
        private var notificationChannelId: String = "mqtt_push_channel"
        private var notificationChannelName: String = "MQTT 推送"
        private var notificationId: Int = 1001
        private var debugMode: Boolean = false
        private var logRawMessage: Boolean = true
        
        fun topicGenerator(generator: TopicGenerator) = apply { this.topicGenerator = generator }
        fun messageParser(parser: MessageParser) = apply { this.messageParser = parser }
        fun appId(id: String) = apply { this.appId = id }
        fun defaultMqttPort(port: Int) = apply { this.defaultMqttPort = port }
        fun defaultWebSocketPort(port: Int) = apply { this.defaultWebSocketPort = port }
        fun defaultKeepAlive(seconds: Int) = apply { this.defaultKeepAlive = seconds }
        fun defaultSubscribeBroadcast(subscribe: Boolean) = apply { this.defaultSubscribeBroadcast = subscribe }
        fun maxReconnectAttempts(attempts: Int) = apply { this.maxReconnectAttempts = attempts }
        fun reconnectDelays(initial: Long, max: Long) = apply { 
            this.reconnectInitialDelay = initial
            this.reconnectMaxDelay = max
        }
        fun messagePageSize(size: Int) = apply { this.messagePageSize = size }
        fun topNotificationCount(count: Int) = apply { this.topNotificationCount = count }
        fun messageExpireTime(time: Long) = apply { this.messageExpireTime = time }
        fun notification(channelId: String, channelName: String, id: Int) = apply {
            this.notificationChannelId = channelId
            this.notificationChannelName = channelName
            this.notificationId = id
        }
        fun debugMode(enable: Boolean) = apply { this.debugMode = enable }
        fun logRawMessage(enable: Boolean) = apply { this.logRawMessage = enable }
        
        fun build() = PushConfig(
            topicGenerator = topicGenerator,
            messageParser = messageParser,
            appId = appId,
            defaultMqttPort = defaultMqttPort,
            defaultWebSocketPort = defaultWebSocketPort,
            defaultKeepAlive = defaultKeepAlive,
            defaultSubscribeBroadcast = defaultSubscribeBroadcast,
            maxReconnectAttempts = maxReconnectAttempts,
            reconnectInitialDelay = reconnectInitialDelay,
            reconnectMaxDelay = reconnectMaxDelay,
            messagePageSize = messagePageSize,
            topNotificationCount = topNotificationCount,
            messageExpireTime = messageExpireTime,
            notificationChannelId = notificationChannelId,
            notificationChannelName = notificationChannelName,
            notificationId = notificationId,
            debugMode = debugMode,
            logRawMessage = logRawMessage
        )
    }
    
    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = PushConfig()
        
        /**
         * 创建 Builder
         */
        fun builder() = Builder()
    }
}

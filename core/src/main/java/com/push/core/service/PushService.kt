package com.push.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.push.core.R
import com.push.core.model.*
import com.push.core.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * MQTT 推送服务（HiveMQ 版本）
 * 使用 lifecycleScope 管理协程生命周期
 */
class PushService : LifecycleService() {

    private var mqttClient: Mqtt5AsyncClient? = null
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // 观察者
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentConfig = MutableStateFlow<BrokerConfig?>(null)
    val currentConfig: StateFlow<BrokerConfig?> = _currentConfig.asStateFlow()

    private val _subscriptions = MutableStateFlow<Set<String>>(emptySet())
    val subscriptions: StateFlow<Set<String>> = _subscriptions.asStateFlow()

    // 消息仓库（延迟初始化）
    private lateinit var repository: MessageRepository

    companion object {
        const val ACTION_CONNECT = "com.push.core.CONNECT"
        const val ACTION_DISCONNECT = "com.push.core.DISCONNECT"
        const val ACTION_SUBSCRIBE = "com.push.core.SUBSCRIBE"
        const val ACTION_PUBLISH = "com.push.core.PUBLISH"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_TOPIC = "topic"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_QOS = "qos"

        const val NOTIFICATION_CHANNEL_ID = "mqtt_push_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: PushService? = null

        fun getInstance(): PushService? = instance

        val connectionStatusFlow: Flow<ConnectionStatus>
            get() = instance?.connectionStatus ?: flowOf(ConnectionStatus.Disconnected)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = MessageRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CONFIG, BrokerConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CONFIG)
                } ?: return START_NOT_STICKY
                connect(config)
            }
            ACTION_DISCONNECT -> disconnect()
            ACTION_SUBSCRIBE -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: return START_NOT_STICKY
                val qos = intent.getIntExtra(EXTRA_QOS, 0)
                subscribe(topic, qos)
            }
            ACTION_PUBLISH -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: return START_NOT_STICKY
                val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return START_NOT_STICKY
                val qos = intent.getIntExtra(EXTRA_QOS, 0)
                publish(topic, payload, qos)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }


    // ==================== 连接管理 ====================

    fun connect(config: BrokerConfig) {
        if (_connectionStatus.value == ConnectionStatus.Connecting ||
            _connectionStatus.value == ConnectionStatus.Connected) return

        _connectionStatus.value = ConnectionStatus.Connecting
        _currentConfig.value = config

        try {
            mqttClient = MqttClient.builder()
                .identifier(config.clientId)
                .serverHost(config.host)
                .serverPort(config.port)
                .automaticReconnect()
                    .initialDelay(500, TimeUnit.MILLISECONDS)
                    .maxDelay(30, TimeUnit.SECONDS)
                    .applyAutomaticReconnect()
                .useMqttVersion5()
                .buildAsync()

            // 构建连接参数
            val connect = mqttClient!!.connectWith()
                .cleanStart(config.cleanSession)
                .sessionExpiryInterval(0)
                .keepAlive(config.keepAliveInterval)
                .simpleAuth()
                    .username(config.username ?: "")
                    .password(config.password?.toByteArray() ?: ByteArray(0))
                    .applySimpleAuth()
                .send()

            connect.whenComplete { _, throwable ->
                if (throwable != null) {
                    _connectionStatus.value = ConnectionStatus.Error(throwable.message ?: "连接失败")
                } else {
                    _connectionStatus.value = ConnectionStatus.Connected
                    _subscriptions.value.forEach { subscribe(it, 0) }
                    startForeground(NOTIFICATION_ID, buildNotification("已连接"))
                }
            }

            // 设置消息回调
            mqttClient?.publishes(
                com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL
            ) { publish: Mqtt5Publish ->
                lifecycleScope.launch(Dispatchers.IO) {
                    handleIncomingMessage(publish.topic.toString(), publish)
                }
            }

        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "未知错误")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient = null
        } catch (e: Exception) {
            // ignore
        }
        _connectionStatus.value = ConnectionStatus.Disconnected
        _subscriptions.value = emptySet()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isConnected(): Boolean = _connectionStatus.value == ConnectionStatus.Connected

    // ==================== 订阅管理 ====================

    fun subscribe(topic: String, qos: Int = 0) {
        if (!isConnected()) return
        try {
            val qosLevel = when (qos) {
                0 -> MqttQos.AT_MOST_ONCE
                1 -> MqttQos.AT_LEAST_ONCE
                else -> MqttQos.EXACTLY_ONCE
            }
            mqttClient?.subscribeWith()
                ?.topicFilter(topic)
                ?.qos(qosLevel)
                ?.send()
                ?.whenComplete { _, _ ->
                    _subscriptions.value += topic
                }
        } catch (e: Exception) {}
    }

    fun unsubscribe(topic: String) {
        if (!isConnected()) return
        try {
            mqttClient?.unsubscribeWith()
                ?.topicFilter(topic)
                ?.send()
                ?.whenComplete { _, _ ->
                    _subscriptions.value = _subscriptions.value - topic
                }
        } catch (e: Exception) {}
    }

    // ==================== 消息发布 ====================

    fun publish(topic: String, payload: String, qos: Int = 0) {
        if (!isConnected()) return
        try {
            val qosLevel = when (qos) {
                0 -> MqttQos.AT_MOST_ONCE
                1 -> MqttQos.AT_LEAST_ONCE
                else -> MqttQos.EXACTLY_ONCE
            }
            mqttClient?.publishWith()
                ?.topic(MqttTopic.of(topic))
                ?.payload(payload.toByteArray(StandardCharsets.UTF_8))
                ?.qos(qosLevel)
                ?.send()
        } catch (e: Exception) {}
    }

    // ==================== 消息处理 ====================

    private suspend fun handleIncomingMessage(topic: String, publish: Mqtt5Publish) {
        val payloadStr = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
        val parsed = parsePayload(payloadStr)

        val pushMessage = PushMessage(
            topic = topic,
            payload = payloadStr,
            qos = publish.qos.ordinal,
            title = parsed.title,
            content = parsed.content,
            type = parsed.type,
            receivedAt = System.currentTimeMillis()
        )

        val id = repository.insert(pushMessage)
        if (_subscriptions.value.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                showNotification(id, pushMessage)
            }
        }
    }

    private data class ParsedPayload(val title: String, val content: String, val type: MessageType)

    private fun parsePayload(payload: String): ParsedPayload {
        return try {
            val json = gson.fromJson(payload, JsonObject::class.java)
            val title = json.get("title")?.asString ?: json.get("t")?.asString ?: ""
            val content = json.get("content")?.asString
                ?: json.get("msg")?.asString
                ?: json.get("c")?.asString
                ?: payload
            val typeStr = json.get("type")?.asString ?: "notification"
            ParsedPayload(title, content, MessageType.fromValue(typeStr))
        } catch (e: Exception) {
            ParsedPayload("", payload, MessageType.NOTIFICATION)
        }
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MQTT 推送通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "接收 MQTT 推送消息通知"; enableVibration(true) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("MQTT Push")
        .setContentText(content)
        .setSmallIcon(R.drawable.ic_push_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun showNotification(messageId: Long, message: PushMessage) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(message.title.ifBlank { message.topic })
            .setContentText(message.content.ifBlank { message.payload })
            .setSmallIcon(R.drawable.ic_push_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(messageId.toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        instance = null
    }
}

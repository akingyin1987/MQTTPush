package com.push.core.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.push.core.MessageQueue
import com.push.core.NetworkManager
import com.push.core.NetworkQuality
import com.push.core.NetworkState
import com.push.core.R
import com.push.core.model.BrokerConfig
import com.push.core.model.ConnectionStatus
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import com.push.core.repository.MessageRepository
import com.push.core.worker.MqttReconnectWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * MQTT 推送前台服务（HiveMQ MQTT 5 版本）。
 *
 * ### 架构说明
 * - 网络监听：[NetworkManager.networkSnapshot]（callbackFlow，响应式）
 * - 消息队列：[MessageQueue]（Room 持久化，弱网指数退避）
 * - 兜底续连：[MqttReconnectWorker]（Service 被杀 / 超时时由 WorkManager 重启）
 * - 状态暴露：[connectionStatus]、[currentConfig]、[subscriptions] 均为 StateFlow
 *
 * ### 弱网优化
 * - 弱网时 keepAlive 翻倍、自动重连初始/最大延迟拉大
 * - 消息发布失败时进入持久化队列，由 [MessageQueue] 按退避策略重试
 * - 重连使用指数退避，最多 [MAX_RECONNECT_ATTEMPTS] 次后交由 WorkManager
 */
internal class PushService : LifecycleService() {

    // ── 私有状态 ──────────────────────────────────────────────────────────────

    private var mqttClient: Mqtt5AsyncClient? = null
    private val gson: Gson = GsonBuilder().create()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private val _currentConfig    = MutableStateFlow<BrokerConfig?>(null)
    private val _subscriptions    = MutableStateFlow<Set<String>>(emptySet())

    // 重连控制
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    // 依赖（延迟初始化，onCreate 之后才有 Context）
    private lateinit var repository: MessageRepository
    private lateinit var networkManager: NetworkManager
    private lateinit var messageQueue: MessageQueue

    // ── 对外只读接口 ──────────────────────────────────────────────────────────

    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    val currentConfig:    StateFlow<BrokerConfig?>    = _currentConfig.asStateFlow()
    val subscriptions:    StateFlow<Set<String>>      = _subscriptions.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "PushService"

        // Intent Action
        const val ACTION_CONNECT    = "com.push.core.CONNECT"
        const val ACTION_DISCONNECT = "com.push.core.DISCONNECT"
        const val ACTION_SUBSCRIBE  = "com.push.core.SUBSCRIBE"
        const val ACTION_PUBLISH    = "com.push.core.PUBLISH"

        // Intent Extra 键
        const val EXTRA_CONFIG  = "config"
        const val EXTRA_TOPIC   = "topic"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_QOS     = "qos"

        // 通知
        const val NOTIFICATION_CHANNEL_ID = "mqtt_push_channel"
        const val NOTIFICATION_ID         = 1001

        // 重连上限，超出后交由 WorkManager 兜底
        private const val MAX_RECONNECT_ATTEMPTS = 5

        @Volatile
        private var instance: PushService? = null

        fun getInstance(): PushService? = instance

        /** 供外部直接观察连接状态（Service 未启动时返回 Disconnected） */
        val connectionStatusFlow: Flow<ConnectionStatus>
            get() = instance?.connectionStatus ?: flowOf(ConnectionStatus.Disconnected)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository    = MessageRepository.getInstance(applicationContext)
        networkManager = NetworkManager.getInstance(applicationContext)
        messageQueue  = MessageQueue.getInstance(applicationContext)

        createNotificationChannel()
        observeNetworkChanges()
        observeConnectionForQueueFlush()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_CONNECT    -> handleConnect(intent)
            ACTION_DISCONNECT -> disconnect()
            ACTION_SUBSCRIBE  -> handleSubscribe(intent)
            ACTION_PUBLISH    -> handlePublish(intent)
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        instance = null
        Log.i(TAG, "Service destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent 分发
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleConnect(intent: Intent) {
        val config: BrokerConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIG, BrokerConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIG)
        } ?: return

        if (isConnected()) {
            Log.d(TAG, "Already connected, flush pending queue instead of reconnecting")
            lifecycleScope.launch { flushPendingMessages() }
            return
        }

        connect(config)
    }

    private fun handleSubscribe(intent: Intent) {
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: return
        val qos   = intent.getIntExtra(EXTRA_QOS, 1)
        subscribe(topic, qos)
    }

    private fun handlePublish(intent: Intent) {
        val topic   = intent.getStringExtra(EXTRA_TOPIC)   ?: return
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
        val qos     = intent.getIntExtra(EXTRA_QOS, 0)
        publish(topic, payload, qos)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 网络监听（callbackFlow 驱动）
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeNetworkChanges() {
        networkManager.networkSnapshot
            .onEach { snapshot ->
                Log.d(TAG, "Network snapshot: state=${snapshot.state}, quality=${snapshot.quality}")
                when (snapshot.state) {
                    NetworkState.Disconnected -> onNetworkLost()
                    else                      -> onNetworkRestored(snapshot.quality)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun onNetworkLost() {
        if (_connectionStatus.value == ConnectionStatus.Connected) {
            Log.w(TAG, "Network lost, marking as Reconnecting")
            _connectionStatus.value = ConnectionStatus.Reconnecting
        }
    }

    private fun onNetworkRestored(quality: NetworkQuality) {
        val status = _connectionStatus.value
        if ((status == ConnectionStatus.Reconnecting || status is ConnectionStatus.Error)
            && _currentConfig.value != null) {
            Log.i(TAG, "Network restored (quality=$quality), triggering reconnect")
            connect(_currentConfig.value!!)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 连接成功后自动冲刷积压消息
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeConnectionForQueueFlush() {
        connectionStatus
            .onEach { status ->
                if (status == ConnectionStatus.Connected) {
                    flushPendingMessages()
                }
            }
            .launchIn(lifecycleScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 连接管理
    // ─────────────────────────────────────────────────────────────────────────

    fun connect(config: BrokerConfig) {
        if (_connectionStatus.value == ConnectionStatus.Connecting ||
            _connectionStatus.value == ConnectionStatus.Connected) {
            Log.d(TAG, "Already connecting/connected, skip")
            return
        }

        _connectionStatus.value = ConnectionStatus.Connecting
        _currentConfig.value    = config

        try {
            val snapshot = networkManager.currentSnapshot()
            mqttClient = buildMqttClient(config, snapshot.quality)

            val keepAlive = adjustKeepAlive(config.keepAliveInterval, snapshot.quality)

            mqttClient!!.connectWith()
                .cleanStart(config.cleanSession)
                .sessionExpiryInterval(0)
                .keepAlive(keepAlive)
                .simpleAuth().username(config.username.orEmpty())
                .password(config.password?.toByteArray() ?: ByteArray(0))
                .applySimpleAuth()
                .send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        onConnectFailed(config, throwable)
                    } else {
                        onConnectSuccess()
                    }
                }

            // 全局消息接收回调
            mqttClient?.publishes(MqttGlobalPublishFilter.ALL) { publish ->
                lifecycleScope.launch(Dispatchers.IO) {
                    handleIncomingMessage(publish.topic.toString(), publish)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "connect() threw exception: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "未知错误")
        }
    }

    private fun onConnectSuccess() {
        Log.i(TAG, "MQTT connected successfully")
        _connectionStatus.value = ConnectionStatus.Connected
        reconnectAttempts = 0
        reconnectJob?.cancel()
        restoreSubscriptions()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("已连接"))
        // 取消之前调度的 WorkManager 任务
        _currentConfig.value?.let { MqttReconnectWorker.cancel(applicationContext) }
    }

    private fun onConnectFailed(config: BrokerConfig, throwable: Throwable) {
        Log.w(TAG, "MQTT connect failed: ${throwable.message}")
        _connectionStatus.value = ConnectionStatus.Error(throwable.message ?: "连接失败")

        if (!networkManager.isNetworkAvailable()) {
            Log.w(TAG, "No network available, skip reconnect scheduling")
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Exceeded max reconnect attempts, handing off to WorkManager")
            MqttReconnectWorker.schedule(applicationContext, config)
            return
        }

        reconnectAttempts++
        val delayMs = calcReconnectDelay(reconnectAttempts, networkManager.isPoorNetwork())
        Log.i(TAG, "Scheduling reconnect #$reconnectAttempts in ${delayMs}ms")

        reconnectJob?.cancel()
        reconnectJob = lifecycleScope.launch {
            delay(delayMs)
            if (_connectionStatus.value is ConnectionStatus.Error) {
                connect(config)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "disconnect() exception: ${e.message}")
        } finally {
            mqttClient = null
        }
        _connectionStatus.value = ConnectionStatus.Disconnected
        _subscriptions.value    = emptySet()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Disconnected")
    }

    fun isConnected(): Boolean = _connectionStatus.value == ConnectionStatus.Connected

    // ─────────────────────────────────────────────────────────────────────────
    // 订阅管理
    // ─────────────────────────────────────────────────────────────────────────

    fun subscribe(topic: String, qos: Int = 1) {
        if (!isConnected()) {
            Log.w(TAG, "subscribe($topic) skipped: not connected")
            return
        }
        mqttClient?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(qos.toMqttQos())
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable == null) {
                    _subscriptions.value += topic
                    Log.d(TAG, "Subscribed to $topic")
                } else {
                    Log.w(TAG, "Subscribe failed for $topic: ${throwable.message}")
                }
            }
    }

    fun unsubscribe(topic: String) {
        if (!isConnected()) return
        mqttClient?.unsubscribeWith()
            ?.topicFilter(topic)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable == null) {
                    _subscriptions.value -= topic
                    Log.d(TAG, "Unsubscribed from $topic")
                }
            }
    }

    /** 重连成功后恢复之前的订阅 */
    fun restoreSubscriptions() {
        val topics = _subscriptions.value.toSet()
        Log.i(TAG, "Restoring ${topics.size} subscriptions")
        topics.forEach { subscribe(it, qos = 1) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 消息发布
    // ─────────────────────────────────────────────────────────────────────────

    fun publish(topic: String, payload: String, qos: Int = 0) {
        if (!isConnected() || !networkManager.isNetworkAvailable()) {
            // 离线或断网 → 直接入队
            Log.d(TAG, "Offline publish queued: $topic")
            messageQueue.enqueueAsync(topic, payload, qos)
            return
        }

        mqttClient?.publishWith()
            ?.topic(MqttTopic.of(topic))
            ?.payload(payload.toByteArray(StandardCharsets.UTF_8))
            ?.qos(qos.toMqttQos())
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.w(TAG, "Publish failed, queuing: $topic – ${throwable.message}")
                    messageQueue.enqueueAsync(topic, payload, qos)
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 消息队列冲刷（弱网优化核心）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 冲刷持久化队列中的积压消息。
     *
     * - 每批最多处理 20 条，处理完一批再取下一批，避免过载。
     * - 弱网时发布失败后由 [MessageQueue.onFailure] 自动计算下次重试时间（指数退避）。
     * - 可被 [MqttReconnectWorker] 在 Service 重启后调用。
     */
    suspend fun flushPendingMessages() = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext
        Log.i(TAG, "Flushing pending message queue")

        val isPoor = networkManager.isPoorNetwork()

        var batch = messageQueue.dequeueBatch()
        while (batch.isNotEmpty() && isConnected()) {
            batch.forEach { msg ->
                publishQueuedMessage(msg, isPoor)
            }
            // 短暂暂停后继续取下一批
            delay(if (isPoor) 500L else 100L)
            batch = messageQueue.dequeueBatch()
        }
    }

    private suspend fun publishQueuedMessage(
        message: com.push.core.QueuedMessage,
        isPoorNetwork: Boolean
    ) {
        try {
            val future = mqttClient?.publishWith()
                ?.topic(MqttTopic.of(message.topic))
                ?.payload(message.payload.toByteArray(StandardCharsets.UTF_8))
                ?.qos(message.qos.toMqttQos())
                ?.send()

            future?.whenComplete { _, throwable ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (throwable == null) {
                        messageQueue.onSuccess(message.id)
                        Log.d(TAG, "Queued message sent: ${message.topic}")
                    } else {
                        messageQueue.onFailure(message, isPoorNetwork)
                        Log.w(TAG, "Queued publish failed (retry ${message.retryCount}): ${throwable.message}")
                    }
                }
            }
        } catch (e: Exception) {
            messageQueue.onFailure(message, isPoorNetwork)
            Log.e(TAG, "publishQueuedMessage exception: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 消息接收处理
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun handleIncomingMessage(topic: String, publish: Mqtt5Publish) {
        val payloadStr = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
        val parsed     = parsePayload(payloadStr)

        val message = PushMessage(
            topic      = topic,
            payload    = payloadStr,
            qos        = publish.qos.ordinal,
            title      = parsed.title,
            content    = parsed.content,
            type       = parsed.type,
            receivedAt = System.currentTimeMillis()
        )

        val id = repository.insert(message)

        // 有订阅时才展示通知（避免静默主题也弹通知）
        if (_subscriptions.value.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                showMessageNotification(id, message)
            }
        }
    }

    private data class ParsedPayload(val title: String, val content: String, val type: MessageType)

    private fun parsePayload(payload: String): ParsedPayload {
        return try {
            val json    = gson.fromJson(payload, JsonObject::class.java)
            val title   = json["title"]?.asString ?: json["t"]?.asString ?: ""
            val content = json["content"]?.asString
                ?: json["msg"]?.asString
                ?: json["c"]?.asString
                ?: payload
            val typeStr = json["type"]?.asString ?: MessageType.NOTIFICATION.value
            ParsedPayload(title, content, MessageType.fromValue(typeStr))
        } catch (e: Exception) {
            ParsedPayload("", payload, MessageType.NOTIFICATION)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 通知
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MQTT 推送通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接收 MQTT 推送消息"
                enableVibration(true)
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(status: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MQTT Push")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_push_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun showMessageNotification(messageId: Long, message: PushMessage) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(message.title.ifBlank { message.topic })
            .setContentText(message.content.ifBlank { message.payload })
            .setSmallIcon(R.drawable.ic_push_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(messageId.toInt(), notification)
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT 客户端构建（弱网参数自适应）
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMqttClient(config: BrokerConfig, quality: NetworkQuality): Mqtt5AsyncClient {
        // 弱网下拉大初始/最大重连延迟
        val (initialDelay, maxDelay) = when (quality) {
            NetworkQuality.POOR     -> 3_000L to 120L
            NetworkQuality.MODERATE -> 1_500L to 60L
            NetworkQuality.GOOD     -> 500L   to 30L
        }
        return MqttClient.builder()
            .identifier(config.clientId)
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnect()
                .initialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .maxDelay(maxDelay, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
            .useMqttVersion5()
            .buildAsync()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具函数
    // ─────────────────────────────────────────────────────────────────────────

    /** 根据网络质量调整 keepAlive 心跳间隔（弱网翻倍，减少心跳耗电） */
    private fun adjustKeepAlive(base: Int, quality: NetworkQuality): Int = when (quality) {
        NetworkQuality.POOR     -> base * 2
        NetworkQuality.MODERATE -> (base * 1.5).toInt()
        NetworkQuality.GOOD     -> base
    }

    /**
     * 计算重连等待时间（指数退避 + 弱网加倍）。
     * 公式：min(1000 × 2^n, 30000)；弱网时上限翻倍到 60000 ms。
     */
    private fun calcReconnectDelay(attempt: Int, isPoor: Boolean): Long {
        val base = minOf(1_000L * (1L shl attempt), 30_000L)
        return if (isPoor) base * 2 else base
    }

    /** Int → MqttQos 映射（0/1/2，其他默认 AT_LEAST_ONCE） */
    private fun Int.toMqttQos(): MqttQos = when (this) {
        0    -> MqttQos.AT_MOST_ONCE
        2    -> MqttQos.EXACTLY_ONCE
        else -> MqttQos.AT_LEAST_ONCE
    }
}

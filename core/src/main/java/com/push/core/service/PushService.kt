package com.push.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.push.core.MessageQueue
import com.push.core.NetworkManager
import com.push.core.NetworkQuality
import com.push.core.NetworkState
import com.push.core.PushManager
import com.push.core.R
import com.push.core.data.userSessionDataStore
import com.push.core.model.BrokerConfig
import com.push.core.model.ConnectionStatus
import com.push.core.model.MessageParser
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import com.push.core.repository.MessageRepository
import com.push.core.service.PushService.Companion.MAX_RECONNECT_ATTEMPTS
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


    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private val _currentConfig    = MutableStateFlow<BrokerConfig?>(null)
    private val _subscriptions    = MutableStateFlow<Set<String>>(emptySet())

    // 重连控制
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    /** 服务器主动断开计数，超过上限后清 session */
    private var serverDisconnectCount = 0
    private val maxServerDisconnect = 3

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

        /** 供外部直接观察订阅列表（Service 未启动时返回空集） */
        val subscriptionsFlow: Flow<Set<String>>
            get() = instance?.subscriptions ?: flowOf(emptySet())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository    = MessageRepository.getInstance(application)
        networkManager = NetworkManager.getInstance(applicationContext)
        messageQueue  = MessageQueue.getInstance(applicationContext)

        createNotificationChannel()
        observeNetworkChanges()
        observeConnectionForQueueFlush()
    }

    /** 连接开始时间（用于计算连接耗时） */
    private var _connectStartTime: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (intent?.action == null) {
            startForeground(NOTIFICATION_ID, buildForegroundNotification("等待连接"))
            return START_REDELIVER_INTENT
        }
        
        when (intent.action) {
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
        val config: BrokerConfig? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIG, BrokerConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIG)
        }
        
        if (config == null) {
            Log.w(TAG, "No config provided in intent, showing waiting status")
            startForeground(NOTIFICATION_ID, buildForegroundNotification("等待连接配置"))
            return
        }

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
        if (_connectionStatus.value is ConnectionStatus.Connected) {
            Log.w(TAG, "Network lost, marking as Reconnecting")
            _connectionStatus.value = ConnectionStatus.Reconnecting
        }
    }

    private fun onNetworkRestored(quality: NetworkQuality) {
        val status = _connectionStatus.value
        val config = _currentConfig.value

        Log.d(TAG, "onNetworkRestored: status=$status, quality=$quality, hasConfig=${config != null}")

        when {
            // 已连接 → 检查连接是否还活着
            status is ConnectionStatus.Connected -> {
                Log.d(TAG, "Already connected, checking if connection is alive...")
                // HiveMQ 的 automaticReconnect 会自动处理，这里不需要额外操作
            }

            // 正在重连或错误状态 → 尝试重连
            (status == ConnectionStatus.Reconnecting || status is ConnectionStatus.Error) && config != null -> {
                Log.i(TAG, "Network restored, triggering reconnect")
                connect(config)
            }

            // 其他情况
            else -> {
                Log.d(TAG, "No action needed for status=$status")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 连接成功后自动冲刷积压消息
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeConnectionForQueueFlush() {
        connectionStatus
            .onEach { status ->
                if (status is ConnectionStatus.Connected) {
                    flushPendingMessages()
                }
            }
            .launchIn(lifecycleScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 连接管理
    // ─────────────────────────────────────────────────────────────────────────

    fun connect(config: BrokerConfig) {
        Log.d(TAG, "══════════════════════════════════════════════════════════════")
        Log.d(TAG, "connect() called with config:")
        Log.d(TAG, "  host      = ${config.host}")
        Log.d(TAG, "  port      = ${config.port}")
        Log.d(TAG, "  clientId  = ${config.clientId}")
        Log.d(TAG, "  username  = ${config.username ?: "(null)"}")
        Log.d(TAG, "  keepAlive = ${config.keepAliveInterval}s")
        Log.d(TAG, "  cleanSession = ${config.cleanSession}")
        Log.d(TAG, "══════════════════════════════════════════════════════════════")

        // 强制清理旧连接（确保状态干净）
        if (mqttClient != null) {
            Log.d(TAG, "Cleaning up old MQTT client...")
            try {
                mqttClient?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting old client: ${e.message}")
            }
            mqttClient = null
        }
        _connectionStatus.value = ConnectionStatus.Disconnected
        serverDisconnectCount = 0
        _subscriptions.value = emptySet()
        reconnectAttempts = 0
        reconnectJob?.cancel()

        _connectionStatus.value = ConnectionStatus.Connecting
        _currentConfig.value    = config
        _connectStartTime = System.currentTimeMillis()  // 记录连接开始时间
        Log.d(TAG, "Connection status set to: Connecting")

        try {
            val snapshot = networkManager.currentSnapshot()
            Log.d(TAG, "Network snapshot: state=${snapshot.state}, quality=${snapshot.quality}")

            mqttClient = buildMqttClient(config, snapshot.quality)
            Log.d(TAG, "MQTT client built successfully")

            val keepAlive = adjustKeepAlive(config.keepAliveInterval, snapshot.quality)
            Log.d(TAG, "Adjusted keepAlive: ${keepAlive}s (base=${config.keepAliveInterval}, quality=${snapshot.quality})")

            Log.d(TAG, "Starting MQTT connectWith()...")
            mqttClient!!.connectWith()
                .cleanStart(config.cleanSession)
                .sessionExpiryInterval(0)
                .keepAlive(keepAlive)
                .simpleAuth().username(config.username.orEmpty())
                .password(config.password?.toByteArray() ?: ByteArray(0))
                .applySimpleAuth()
                .send()
                .whenComplete { ack, throwable ->
                    if (throwable != null) {
                        Log.e(TAG, "connectWith() FAILED: ${throwable.javaClass.simpleName}: ${throwable.message}")
                        onConnectFailed(config, throwable)
                    } else {
                        // ConnAck 返回，连接成功
                        // addConnectedListener 会处理状态更新，这里只记录日志

                        Log.i(TAG, "connectWith() ConnAck received: reasonCode=${ack.reasonCode}, sessionPresent=${ack.isSessionPresent}")
                        if (ack.reasonCode.isError) {
                            Log.e(TAG, "ConnAck reasonCode is error: ${ack.reasonCode}")
                            onConnectFailed(config, Exception("ConnAck failed: ${ack.reasonCode}"))
                        }
                        // 成功的情况由 addConnectedListener 处理
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

    /**
     * 同步清 session（供 disconnect() 调用，避免循环依赖）
     * 直接写 DataStore 并通知 UI
     */
    private fun clearSession() {
        _connectionStatus.value = ConnectionStatus.SessionCleared
        try {
            lifecycleScope.launch {
                applicationContext.userSessionDataStore.updateData {
                    com.push.core.proto.UserSessionData.getDefaultInstance()
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "clearSession() failed: ${e.message}")
        }
    }

    /**
     * 异步清 session（供协程调用）
     * 内部重试耗尽时调用，清完通知 UI 跳转登录页
     */
    private suspend fun clearSessionAsync() {
        withContext(Dispatchers.IO) {
            try {
                applicationContext.userSessionDataStore.updateData {
                    com.push.core.proto.UserSessionData.getDefaultInstance()
                }
            } catch (e: Exception) {
                Log.e(TAG, "clearSessionAsync() failed: ${e.message}")
            }
        }
        _connectionStatus.value = ConnectionStatus.SessionCleared
    }

    private fun onConnectSuccess(isReconnect: Boolean = false) {
        Log.i(TAG, "onConnectSuccess: isReconnect=$isReconnect")
        serverDisconnectCount = 0
        
        val config = _currentConfig.value
        val connectDuration = if (config != null) {
            System.currentTimeMillis() - _connectStartTime
        } else 0L
        
        _connectionStatus.value = ConnectionStatus.Connected(
            connectedAt = System.currentTimeMillis(),
            connectDuration = connectDuration,
            serverAddress = "${config?.host}:${config?.port}"
        )
        
        reconnectAttempts = 0
        reconnectJob?.cancel()

        // 只有首次连接才恢复订阅（重连时订阅还在）
        if (!isReconnect) {
            restoreSubscriptions()
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification("已连接"))
        config?.let { MqttReconnectWorker.cancel(applicationContext) }
    }

    private fun onDisconnected() {
        Log.w(TAG, "onDisconnected: MQTT connection lost")
        _connectionStatus.value = ConnectionStatus.Disconnected
        startForeground(NOTIFICATION_ID, buildForegroundNotification("已断开"))
    }

    private fun onConnectFailed(config: BrokerConfig, throwable: Throwable) {
        Log.w(TAG, "MQTT connect failed: ${throwable.message}")
        _connectionStatus.value = ConnectionStatus.Error(
            message = throwable.message ?: "连接失败",
            errorAt = System.currentTimeMillis()
        )

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
        _subscriptions.value = emptySet()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 用户主动断开 → 立即清 session，UI 跳转登录页
        clearSession()
        Log.i(TAG, "Disconnected (manual)")
    }

    fun isConnected(): Boolean = _connectionStatus.value is ConnectionStatus.Connected

    // ─────────────────────────────────────────────────────────────────────────
    // 订阅管理
    // ─────────────────────────────────────────────────────────────────────────

    fun subscribe(topic: String, qos: Int = 1) {
        Log.d(TAG, "subscribe() called: topic=$topic, qos=$qos, connected=${isConnected()}")

        if (!isConnected()) {
            Log.w(TAG, "subscribe($topic) SKIPPED: not connected (status=${_connectionStatus.value})")
            return
        }

        // 校验主题过滤器格式，避免 IllegalArgumentException
        if (!isValidTopicFilter(topic)) {
            Log.w(TAG, "subscribe($topic) INVALID: '#' must appear as '/#' at end, '+' only between '/' separators")
            return
        }

        mqttClient?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(qos.toMqttQos())
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable == null) {
                    _subscriptions.value += topic
                    Log.i(TAG, "✓ Subscribed to $topic (qos=$qos)")
                } else {
                    Log.w(TAG, "✗ Subscribe FAILED for $topic: ${throwable.javaClass.simpleName}: ${throwable.message}")
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

    /** 消息解析器（从 PushManager 配置获取） */
    private val messageParser: MessageParser
        get() = PushManager.getInstance(application).config.messageParser

    private suspend fun handleIncomingMessage(topic: String, publish: Mqtt5Publish) {
        val payloadStr = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ MQTT MESSAGE RECEIVED")
        Log.d(TAG, "║ topic   = $topic")
        Log.d(TAG, "║ qos     = ${publish.qos}")
        Log.d(TAG, "║ payload = $payloadStr")
        Log.d(TAG, "╚══════════════════════════════════════════════════════════════")

        // 使用配置的 MessageParser 解析消息
        val message = messageParser.parse(topic, payloadStr) ?: PushMessage(
            topic = topic,
            payload = payloadStr,
            title = topic.substringAfterLast('/'),
            content = payloadStr,
            type = MessageType.NOTIFICATION,
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
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT 客户端构建（弱网参数自适应）
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMqttClient(config: BrokerConfig, quality: NetworkQuality): Mqtt5AsyncClient {
        val (initialDelay, maxDelay) = when (quality) {
            NetworkQuality.POOR     -> 3_000L to 120L
            NetworkQuality.MODERATE -> 1_500L to 60L
            NetworkQuality.GOOD     -> 500L   to 30L
        }
        Log.d(TAG, "buildMqttClient: host=${config.host}, port=${config.port}, clientId=${config.clientId}")
        Log.d(TAG, "buildMqttClient: quality=$quality, initialDelay=${initialDelay}ms, maxDelay=${maxDelay}s")

        return MqttClient.builder()
            .identifier(config.clientId)
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnect()
                .initialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .maxDelay(maxDelay, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
            .useMqttVersion5()
            // 连接成功监听（首次连接和自动重连）
            .addConnectedListener { context ->
                val state = context.clientConfig.state
                val isReconnect = !state.isConnected
                Log.i(TAG, "✅ MQTT Connected! state=$state, isReconnect=$isReconnect")
                lifecycleScope.launch(Dispatchers.Main) {
                    onConnectSuccess(isReconnect = isReconnect)
                }
            }
            // 断开连接监听
            .addDisconnectedListener { context ->
                val state = context.clientConfig.state
                val isServerInitiated = context.source == MqttDisconnectSource.SERVER
                val isUserInitiated = context.source == MqttDisconnectSource.CLIENT

                Log.w(TAG, "❌ MQTT Disconnected! state=$state, source=${context.source}, isServerInitiated=$isServerInitiated, isUserInitiated=$isUserInitiated")

                lifecycleScope.launch(Dispatchers.Main) {
                    // 用户主动断开 → 立即清 session，UI 跳转登录页
                    if (isUserInitiated) {
                        Log.i(TAG, "User initiated disconnect, clearing session")
                        clearSession()
                        return@launch
                    }

                    // 服务器主动断开 → 计数，超过上限则清 session
                    if (isServerInitiated) {
                        serverDisconnectCount++
                        Log.w(TAG, "Server initiated disconnect #${serverDisconnectCount}/$maxServerDisconnect")
                        if (serverDisconnectCount >= maxServerDisconnect) {
                            Log.w(TAG, "Server disconnect exceeded limit, clearing session")
                            clearSessionAsync()
                        } else {
                            _connectionStatus.value = ConnectionStatus.Error("服务器断开连接 (${serverDisconnectCount}/$maxServerDisconnect)")
                        }
                        return@launch
                    }

                    // 其他原因（网络丢失等）→ 检查网络状态
                    val networkAvailable = networkManager.isNetworkAvailable()
                    Log.d(TAG, "Network available: $networkAvailable")

                    if (!networkAvailable) {
                        // 网络不可用 → 等待网络恢复
                        Log.w(TAG, "Network lost, waiting for recovery")
                        _connectionStatus.value = ConnectionStatus.Reconnecting
                    } else if (state.isConnectedOrReconnect) {
                        // 网络正常，会自动重连
                        Log.i(TAG, "Network OK, auto reconnect enabled")
                        _connectionStatus.value = ConnectionStatus.Reconnecting
                    } else {
                        // 不会自动重连
                        Log.w(TAG, "No auto reconnect, connection lost")
                        _connectionStatus.value = ConnectionStatus.Disconnected
                    }
                }
            }
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

    /**
     * MQTT 主题过滤器格式校验。
     * - # 必须作为独立层级，放在末尾（形如 /#）
     * - + 只能作为层级占位符，不能首尾出现
     * - 不能包含非法字符（空字符串、控制字符等）
     */
    private fun isValidTopicFilter(topic: String?): Boolean {
        if (topic.isNullOrBlank()) return false

        // # 必须在末尾，且前面必须是 /
        val hashIdx = topic.indexOf('#')
        if (hashIdx != -1 && (hashIdx != topic.length - 1 || (hashIdx > 0 && topic[hashIdx - 1] != '/'))) {
            return false
        }
        // + 不能在首尾层级（+ 前后必须是 /）
        val parts = topic.split('/')
        for (part in parts) {
            if (part.isEmpty()) continue
            if (part == "+") {
                // + 单独成一级是合法的
                continue
            }
            if (part.contains('#') || part.contains('+')) {
                return false
            }
        }
        // 主题不能以 + 或 # 结尾（除非 # 合法地在末尾）
        val lastPart = parts.lastOrNull() ?: return false
        if (lastPart == "+") return false
        if (lastPart.contains('+') && !topic.endsWith("/#")) return false

        return true
    }
}

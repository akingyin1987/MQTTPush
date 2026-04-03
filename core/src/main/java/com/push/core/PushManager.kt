package com.push.core

import android.app.Application
import android.util.Log
import com.push.core.data.userSessionDataStore
import com.push.core.model.BrokerConfig
import com.push.core.model.ConnectionStatus
import com.push.core.model.LoginResult
import com.push.core.model.LogoutResult
import com.push.core.model.PushConfig
import com.push.core.model.UserSession
import com.push.core.proto.UserSessionData
import com.push.core.service.PushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Push 推送管理器（单例）
 *
 * 会话持久化：Proto DataStore（类型安全 + 响应式）
 * 数据文件：user_session.pb
 *
 * ┌─────────────────────────────────────────────────────┐
 * │  Proto DataStore (user_session.pb)                  │
 * │  UserSessionData { userId, groupIds, loginAt, ... } │
 * │         ↓ Flow<UserSessionData>                     │
 * │  currentSession: StateFlow<UserSession?>            │
 * │         ↓                                           │
 * │  isLoggedIn: StateFlow<Boolean>                     │
 * └─────────────────────────────────────────────────────┘
 *
 * 使用方式：
 * ```kotlin
 * val mgr = PushManager.getInstance(context)
 *
 * // 连接 Broker
 * mgr.connect(BrokerConfig(host = "10.0.2.2"))
 *
 * // 登录（协程）
 * lifecycleScope.launch {
 *     mgr.login(userId = "u123", groupIds = listOf("g456"))
 * }
 *
 * // 登出（协程）
 * lifecycleScope.launch { mgr.logout() }
 *
 * // 观察会话变化
 * mgr.currentSession.collect { session -> ... }
 * ```
 */
class PushManager private constructor(private val context: Application) {




    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ==================== 配置管理 ====================

    /** 当前配置 */
    private var _config: PushConfig = PushConfig.DEFAULT
    val config: PushConfig get() = _config
    
    /**
     * 设置配置（必须在 connect/login 之前调用）
     */
    fun setConfig(config: PushConfig) {
        _config = config
        Log.d(TAG, "PushConfig updated: appId=${config.appId}, topicGenerator=${config.topicGenerator::class.simpleName}")
    }

    // ==================== 响应式会话 ====================

    /**
     * 直接从 Proto DataStore Flow 派生
     * userId 为空 → null（未登录）
     * userId 非空 → UserSession（已登录）
     */
    val currentSession: StateFlow<UserSession?> = context.userSessionDataStore.data
        .map { proto -> proto.toUserSession() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val isLoggedIn: StateFlow<Boolean> = currentSession
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ==================== 连接状态 ====================

    // 直接暴露 PushService 的 connectionStatus（实时，无轮询）
    val connectionStatus: StateFlow<ConnectionStatus>
        get() = PushService.getInstance()?.connectionStatus
            ?: MutableStateFlow(ConnectionStatus.Disconnected).asStateFlow()

    // 备用：轮询方式（已废弃，改用上面的实时 Flow）
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
//    private val statusHandler = Handler(Looper.getMainLooper())
//    private val statusPoller = object : Runnable {
//        override fun run() {
//            PushService.getInstance()?.let {
//                _connectionStatus.value = it.connectionStatus.value
//            }
//            statusHandler.postDelayed(this, 500) // 缩短到 500ms
//        }
//    }



    // ==================== 连接管理 ====================

    /**
     * 连接 MQTT Broker
     * 会先断开旧连接，确保状态干净
     */
    fun connect(config: BrokerConfig) {
        // 先断开旧连接（如果有）
        PushService.getInstance()?.let { service ->
            if (service.isConnected() ||
                service.connectionStatus.value == ConnectionStatus.Connecting ||
                service.connectionStatus.value == ConnectionStatus.Reconnecting) {
                Log.d(TAG, "connect(): disconnecting old connection first")
                service.disconnect()
            }
        }

        // 发起新连接
        context.startService(
            android.content.Intent(context, PushService::class.java).apply {
                action = PushService.ACTION_CONNECT
                putExtra(PushService.EXTRA_CONFIG, config)
            }
        )
    }

    fun disconnect() {
        context.startService(
            android.content.Intent(context, PushService::class.java).apply {
                action = PushService.ACTION_DISCONNECT
            }
        )
    }

    // ==================== 登录 ====================

    /**
     * 用户登录（挂起函数）
     * 
     * 使用 PushConfig 中的 topicGenerator 和 appId
     */
    suspend fun login(
        userId: String,
        groupIds: List<String> = emptyList(),
        extras: Map<String, String> = emptyMap(),
        subscribeBroadcast: Boolean? = null,
        token: String = "",
        tokenExpiresAt: Long = 0L,
        appId: String? = null
    ): LoginResult {
        if (userId.isBlank()) return LoginResult.Error("userId 不能为空")

        // 使用配置
        val effectiveAppId = appId ?: _config.appId
        val effectiveSubscribeBroadcast = subscribeBroadcast ?: _config.defaultSubscribeBroadcast
        val topicGenerator = _config.topicGenerator

        // 取消旧订阅
        currentSession.value?.subscribedTopics?.forEach { unsubscribe(it) }

        val loginAt = System.currentTimeMillis()

        // 创建会话
        val session = UserSession(
            userId = userId,
            token = token,
            tokenExpiresAt = tokenExpiresAt,
            appId = effectiveAppId,
            groupIds = groupIds,
            extras = extras,
            loginAt = loginAt,
            subscribeBroadcast = effectiveSubscribeBroadcast,
            topicGenerator = topicGenerator
        )

        // 写入 DataStore
        context.userSessionDataStore.updateData { current ->
            current.toBuilder()
                .setUserId(userId)
                .clearGroupIds()
                .addAllGroupIds(groupIds)
                .setLoginAt(loginAt)
                .setSubscribeBroadcast(effectiveSubscribeBroadcast)
                .clearExtras()
                .putAllExtras(extras)
                .setToken(token)
                .setTokenExpiresAt(tokenExpiresAt)
                .setAppId(effectiveAppId)
                .build()
        }

        // 订阅专属主题
        session.subscribedTopics.forEach { subscribe(it, qos = 1) }

        Log.i(TAG, "Login success: userId=$userId, appId=$effectiveAppId, topics=${session.subscribedTopics.size}")
        return LoginResult.Success(session)
    }

    // ==================== 登出 ====================

    /**
     * 用户登出（挂起函数）
     *
     * 执行步骤：
     * 1. 取消所有订阅
     * 2. 清空 Proto DataStore → currentSession 自动变 null
     */
    suspend fun logout(): LogoutResult {
        val session = currentSession.value
            ?: return LogoutResult.Error("当前未登录")

        // 取消所有订阅
        session.subscribedTopics.forEach { unsubscribe(it) }

        // 清空 DataStore（重置为默认值 = userId 为空 = 未登录）
        context.userSessionDataStore.updateData {
            UserSessionData.getDefaultInstance()
        }

        return LogoutResult.Success
    }

    // ==================== 重连恢复 ====================

    /**
     * MQTT 重连成功后调用，恢复订阅
     * 建议在 PushService 连接成功回调中调用
     */
    fun restoreSubscriptions() {
        currentSession.value?.subscribedTopics?.forEach { subscribe(it, qos = 1) }
    }

    // ==================== 订阅 / 发布 ====================

    fun subscribe(topic: String, qos: Int = 0) {
        PushService.getInstance()?.subscribe(topic, qos)
    }

    fun unsubscribe(topic: String) {
        PushService.getInstance()?.unsubscribe(topic)
    }

    fun publish(topic: String, payload: String, qos: Int = 0) {
        PushService.getInstance()?.publish(topic, payload, qos)
    }

    // ==================== 生命周期 ====================

    fun destroy() {
      //  statusHandler.removeCallbacks(statusPoller)
        scope.cancel()
    }

    // ==================== Proto ↔ Domain 转换 ====================

    /**
     * Proto → UserSession（领域模型）
     * userId 为空 → null（未登录）
     */
    private fun UserSessionData.toUserSession(): UserSession? {
        if (userId.isBlank()) return null
        return UserSession(
            userId = userId,
            token = token,
            tokenExpiresAt = tokenExpiresAt,
            appId = appId.ifBlank { "app1" },
            groupIds = groupIdsList.toList(),
            extras = extrasMap.toMap(),
            loginAt = loginAt,
            subscribeBroadcast = subscribeBroadcast
        )
    }

    companion object {
        private const val TAG = "PushManager"
        @Volatile
        private var INSTANCE: PushManager? = null

        fun getInstance(application: Application): PushManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PushManager(application).also { INSTANCE = it }
            }
        }
    }

}

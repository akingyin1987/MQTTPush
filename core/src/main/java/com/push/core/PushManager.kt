package com.push.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.push.core.data.userSessionDataStore
import com.push.core.model.*
import com.push.core.proto.UserSessionData
import com.push.core.service.PushService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
class PushManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusPoller = object : Runnable {
        override fun run() {
            PushService.getInstance()?.let {
                _connectionStatus.value = it.connectionStatus.value
            }
            statusHandler.postDelayed(this, 2000)
        }
    }

    init {
        statusHandler.post(statusPoller)
    }

    // ==================== 连接管理 ====================

    fun connect(config: BrokerConfig) {
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
     * 执行步骤：
     * 1. 取消旧会话订阅
     * 2. 写入 Proto DataStore → currentSession 自动更新
     * 3. 订阅专属主题
     *
     * 订阅主题：
     *   push/app1/user/{userId}/#
     *   push/app1/group/{groupId}/#  （每个分组）
     *   push/app1/broadcast/#        （可选）
     */
    suspend fun login(
        userId: String,
        groupIds: List<String> = emptyList(),
        extras: Map<String, String> = emptyMap(),
        subscribeBroadcast: Boolean = true
    ): LoginResult {
        if (userId.isBlank()) return LoginResult.Error("userId 不能为空")

        // 取消旧订阅
        currentSession.value?.subscribedTopics?.forEach { unsubscribe(it) }

        // 写入 Proto DataStore（原子操作）
        context.userSessionDataStore.updateData { current ->
            current.toBuilder()
                .setUserId(userId)
                .clearGroupIds()
                .addAllGroupIds(groupIds)
                .setLoginAt(System.currentTimeMillis())
                .setSubscribeBroadcast(subscribeBroadcast)
                .clearExtras()
                .putAllExtras(extras)
                .build()
        }

        // 订阅专属主题（DataStore 写完后 currentSession 已更新）
        val session = UserSession(
            userId = userId,
            groupIds = groupIds,
            extras = extras,
            subscribeBroadcast = subscribeBroadcast
        )
        session.subscribedTopics.forEach { subscribe(it, qos = 1) }

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
        statusHandler.removeCallbacks(statusPoller)
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
            groupIds = groupIdsList.toList(),
            extras = extrasMap.toMap(),
            loginAt = loginAt,
            subscribeBroadcast = subscribeBroadcast
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: PushManager? = null

        fun getInstance(context: Context): PushManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PushManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

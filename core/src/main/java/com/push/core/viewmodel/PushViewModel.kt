package com.push.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.push.core.PushManager
import com.push.core.model.*
import com.push.core.repository.MessageRepository
import com.push.core.service.PushService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 推送核心 ViewModel
 * 管理：连接状态、登录/登出、消息列表、订阅管理
 */
class PushViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = MessageRepository.getInstance(application)
    val pushManager: PushManager = PushManager.getInstance(application)

    // ==================== 连接状态 ====================

    // 直接使用 PushService 的 connectionStatus（实时 StateFlow，无轮询）
    val connectionStatus: StateFlow<ConnectionStatus> = PushService.connectionStatusFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.Disconnected)

    // 订阅列表（实时更新）
    val subscriptions: StateFlow<Set<String>> = PushService.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ==================== 用户会话 ====================

    val currentSession: StateFlow<UserSession?> = pushManager.currentSession

    val isLoggedIn: StateFlow<Boolean> = pushManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val savedBrokerConfig: StateFlow<BrokerConfig?> = pushManager.savedBrokerConfig

    // ==================== 消息状态 ====================

    private val _messages = MutableStateFlow<List<PushMessage>>(emptyList())
    val messages: StateFlow<List<PushMessage>> = _messages.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val latestUnread: StateFlow<List<PushMessage>> = repository.getLatestUnread(5)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentFilter = MutableStateFlow(MessageFilter())
    val currentFilter: StateFlow<MessageFilter> = _currentFilter.asStateFlow()

    private val _selectedMessageId = MutableStateFlow<Long?>(null)
    val selectedMessageId: StateFlow<Long?> = _selectedMessageId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMessages(limit = 100).collect { list ->
                _messages.value = list
            }
        }
    }

    // ==================== 登录 / 登出 ====================

    // 登录结果单次事件
    private val _loginResult = MutableStateFlow<LoginResult?>(null)
    val loginResult: StateFlow<LoginResult?> = _loginResult.asStateFlow()

    /**
     * 用户登录（内部走协程，结果通过 loginResult Flow 回调）
     * 自动订阅：push/{appId}/user/{userId}/# + push/{appId}/group/{groupId}/#
     */
    fun login(
        userId: String,
        groupIds: List<String> = emptyList(),
        extras: Map<String, String> = emptyMap(),
        subscribeBroadcast: Boolean? = null,
        token: String = "",
        tokenExpiresAt: Long = 0L
    ) {
        viewModelScope.launch {
            val result = pushManager.login(
                userId = userId,
                groupIds = groupIds,
                extras = extras,
                subscribeBroadcast = subscribeBroadcast,
                token = token,
                tokenExpiresAt = tokenExpiresAt
            )
            _loginResult.value = result
        }
    }

    fun consumeLoginResult() { _loginResult.value = null }

    /**
     * 用户登出（内部走协程）
     * 自动取消所有订阅 + 清除 DataStore 会话
     */
    fun logout() {
        viewModelScope.launch { pushManager.logout() }
    }

    // ==================== 连接操作 ====================

    fun connect(config: BrokerConfig) {
        pushManager.connect(config)
    }

    fun restoreConnectionIfNeeded() {
        val session = currentSession.value ?: return
        val config = savedBrokerConfig.value ?: return
        if (connectionStatus.value == ConnectionStatus.Disconnected) {
            pushManager.connect(config)
        }
    }

    fun disconnect() {
        pushManager.disconnect()
    }

    fun subscribe(topic: String, qos: Int = 0) {
        pushManager.subscribe(topic, qos)
    }

    fun unsubscribe(topic: String) {
        pushManager.unsubscribe(topic)
    }

    fun publish(topic: String, payload: String, qos: Int = 0) {
        pushManager.publish(topic, payload, qos)
    }

    // ==================== 消息操作 ====================

    fun setFilter(filter: MessageFilter) {
        _currentFilter.value = filter
        viewModelScope.launch {
            repository.getMessages(filter).collect { list ->
                _messages.value = list
            }
        }
    }

    fun selectMessage(message: PushMessage?) {
        _selectedMessageId.value = message?.id
        message?.let { markAsRead(it.id) }
    }

    fun clearSelectedMessage() {
        _selectedMessageId.value = null
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { repository.markAsRead(id) }
    }

    fun markAsUnread(id: Long) {
        viewModelScope.launch { repository.markAsUnread(id) }
    }

    fun markAsRead(ids: List<Long>) {
        viewModelScope.launch { repository.markAsRead(ids) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun toggleStar(id: Long) {
        viewModelScope.launch { repository.toggleStar(id) }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch { repository.deleteByIds(ids) }
    }

    fun clearRead() {
        viewModelScope.launch { repository.clearRead() }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        pushManager.destroy()
    }
}

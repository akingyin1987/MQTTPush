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

    val connectionStatus: StateFlow<ConnectionStatus> = PushService.connectionStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionStatus.Disconnected)

    val subscriptions: StateFlow<Set<String>> = flow {
        while (true) {
            val subs = PushService.getInstance()?.subscriptions?.value ?: emptySet()
            emit(subs)
            kotlinx.coroutines.delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ==================== 用户会话 ====================

    val currentSession: StateFlow<UserSession?> = pushManager.currentSession

    val isLoggedIn: StateFlow<Boolean> = currentSession
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ==================== 消息状态 ====================

    private val _messages = MutableStateFlow<List<PushMessage>>(emptyList())
    val messages: StateFlow<List<PushMessage>> = _messages.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestUnread: StateFlow<List<PushMessage>> = repository.getLatestUnread(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentFilter = MutableStateFlow(MessageFilter())
    val currentFilter: StateFlow<MessageFilter> = _currentFilter.asStateFlow()

    private val _selectedMessage = MutableStateFlow<PushMessage?>(null)
    val selectedMessage: StateFlow<PushMessage?> = _selectedMessage.asStateFlow()

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
     * 自动订阅：push/app1/user/{userId}/# + push/app1/group/{groupId}/#
     */
    fun login(
        userId: String,
        groupIds: List<String> = emptyList(),
        extras: Map<String, String> = emptyMap(),
        subscribeBroadcast: Boolean = true
    ) {
        viewModelScope.launch {
            val result = pushManager.login(userId, groupIds, extras, subscribeBroadcast)
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
        _selectedMessage.value = message
        message?.let { markAsRead(it.id) }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { repository.markAsRead(id) }
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

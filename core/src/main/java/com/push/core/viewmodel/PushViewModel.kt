package com.push.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.push.core.model.*
import com.push.core.repository.MessageRepository
import com.push.core.service.PushService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 推送核心 ViewModel
 * 管理消息列表、连接状态、订阅管理
 */
class PushViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = MessageRepository.getInstance(application)

    // ==================== 消息状态 ====================

    private val _messages = MutableStateFlow<List<PushMessage>>(emptyList())
    val messages: StateFlow<List<PushMessage>> = _messages.asStateFlow()

    private val _unreadMessages = MutableStateFlow<List<PushMessage>>(emptyList())
    val unreadMessages: StateFlow<List<PushMessage>> = _unreadMessages.asStateFlow()

    val unreadCount: StateFlow<Int> = repository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 最新未读消息（用于 Top 5 滚动展示） */
    val latestUnread: StateFlow<List<PushMessage>> = repository.getLatestUnread(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentFilter = MutableStateFlow(MessageFilter())
    val currentFilter: StateFlow<MessageFilter> = _currentFilter.asStateFlow()

    private val _selectedMessage = MutableStateFlow<PushMessage?>(null)
    val selectedMessage: StateFlow<PushMessage?> = _selectedMessage.asStateFlow()

    // ==================== 连接状态 ====================

    val connectionStatus: StateFlow<ConnectionStatus> = PushService.connectionStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionStatus.Disconnected)

    val subscriptions: StateFlow<Set<String>> = flow {
        PushService.getInstance()?.let { service ->
            service.subscriptions.collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ==================== 消息操作 ====================

    init {
        // 观察消息列表
        viewModelScope.launch {
            repository.getMessages(limit = 100).collect { list ->
                _messages.value = list
            }
        }
        // 观察未读消息
        viewModelScope.launch {
            repository.getUnreadMessages().collect { list ->
                _unreadMessages.value = list
            }
        }
    }

    /** 应用过滤器 */
    fun setFilter(filter: MessageFilter) {
        _currentFilter.value = filter
        viewModelScope.launch {
            repository.getMessages(filter).collect { list ->
                _messages.value = list
            }
        }
    }

    /** 选择消息 */
    fun selectMessage(message: PushMessage?) {
        _selectedMessage.value = message
        if (message != null) {
            markAsRead(message.id)
        }
    }

    /** 标记已读 */
    fun markAsRead(id: Long) {
        viewModelScope.launch { repository.markAsRead(id) }
    }

    /** 批量标记已读 */
    fun markAsRead(ids: List<Long>) {
        viewModelScope.launch { repository.markAsRead(ids) }
    }

    /** 全部标记已读 */
    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    /** 切换星标 */
    fun toggleStar(id: Long) {
        viewModelScope.launch { repository.toggleStar(id) }
    }

    /** 删除消息 */
    fun deleteMessage(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** 批量删除 */
    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch { repository.deleteByIds(ids) }
    }

    /** 清空已读 */
    fun clearRead() {
        viewModelScope.launch { repository.clearRead() }
    }

    /** 清空全部 */
    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    // ==================== 连接操作 ====================

    fun connect(config: BrokerConfig) {
        val intent = android.content.Intent(getApplication(), PushService::class.java).apply {
            action = PushService.ACTION_CONNECT
            putExtra(PushService.EXTRA_CONFIG, config)
        }
        getApplication<Application>().startService(intent)
    }

    fun disconnect() {
        val intent = android.content.Intent(getApplication(), PushService::class.java).apply {
            action = PushService.ACTION_DISCONNECT
        }
        getApplication<Application>().startService(intent)
    }

    fun subscribe(topic: String, qos: Int = 0) {
        val intent = android.content.Intent(getApplication(), PushService::class.java).apply {
            action = PushService.ACTION_SUBSCRIBE
            putExtra(PushService.EXTRA_TOPIC, topic)
            putExtra(PushService.EXTRA_QOS, qos)
        }
        getApplication<Application>().startService(intent)
    }

    fun unsubscribe(topic: String) {
        PushService.getInstance()?.unsubscribe(topic)
    }

    fun publish(topic: String, payload: String, qos: Int = 0) {
        val intent = android.content.Intent(getApplication(), PushService::class.java).apply {
            action = PushService.ACTION_PUBLISH
            putExtra(PushService.EXTRA_TOPIC, topic)
            putExtra(PushService.EXTRA_PAYLOAD, payload)
            putExtra(PushService.EXTRA_QOS, qos)
        }
        getApplication<Application>().startService(intent)
    }
}

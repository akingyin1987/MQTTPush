package com.push.core.repository

import android.app.Application
import com.google.gson.Gson
import com.push.core.model.MessageDao
import com.push.core.model.MessageFilter
import com.push.core.model.MessageStatus
import com.push.core.model.PushDatabase
import com.push.core.model.PushMessage
import com.push.core.model.ReadReceipt
import com.push.core.service.PushService
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库
 * 统一管理消息的 CRUD 操作 + 已读回执同步
 */
class MessageRepository private constructor(
    database: PushDatabase,
    private val context: Application
) {

    private val dao: MessageDao = database.messageDao()
    private val gson = Gson()

    // ==================== 消息查询 ====================

    fun getMessages(limit: Int = 50, offset: Int = 0): Flow<List<PushMessage>> =
        dao.getMessages(limit, offset)

    fun getMessages(filter: MessageFilter): Flow<List<PushMessage>> =
        dao.getMessagesFiltered(
            isRead = when (filter.status) {
                MessageStatus.UNREAD -> false
                MessageStatus.READ -> true
                else -> null
            },
            type = filter.type?.value,
            topic = filter.topic,
            keyword = filter.keyword,
            limit = filter.limit,
            offset = filter.offset
        )

    fun getUnreadMessages(): Flow<List<PushMessage>> = dao.getUnreadMessages()
    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()
    fun getLatestUnread(limit: Int = 5): Flow<List<PushMessage>> = dao.getLatestUnread(limit)

    // ==================== 消息操作 ====================

    suspend fun insert(message: PushMessage): Long = dao.insert(message)
    suspend fun insertAll(messages: List<PushMessage>) = dao.insertAll(messages)

    /**
     * 标记已读 + 同步回执到服务端
     * @param id 本地消息 ID
     * @param syncToServer 是否同步到服务端（默认 true）
     */
    suspend fun markAsRead(id: Long, syncToServer: Boolean = true) {
        dao.markAsRead(id)

        if (syncToServer) {
            // 查询消息详情，获取 messageId
            val message = dao.getById(id)
            message?.let { syncReadReceipt(it) }
        }
    }

    /**
     * 批量标记已读 + 同步回执
     */
    suspend fun markAsRead(ids: List<Long>, syncToServer: Boolean = true) {
        dao.markAsRead(ids)

        if (syncToServer) {
            ids.forEach { id ->
                dao.getById(id)?.let { syncReadReceipt(it) }
            }
        }
    }

    /**
     * 全部标记已读 + 同步回执
     */
    suspend fun markAllAsRead(syncToServer: Boolean = true) {
        if (syncToServer) {
            // 先获取所有未读消息
            dao.getUnreadMessagesList().forEach { syncReadReceipt(it) }
        }
        dao.markAllAsRead()
    }

    /**
     * 同步已读回执到服务端
     * 主题：由 session.readReceiptTopic 动态生成
     * Payload: {"messageId":"xxx","userId":"u123","readAt":1234567890}
     */
    private suspend fun syncReadReceipt(message: PushMessage) {
        if (message.messageId.isBlank() || message.isReadSynced) return

        val session = com.push.core.PushManager.getInstance(context)
            .currentSession.value ?: return

        val receipt = ReadReceipt(
            messageId = message.messageId,
            userId = session.userId
        )

        // 使用 session 的主题生成器
        val topic = session.readReceiptTopic
        val payload = gson.toJson(receipt)

        PushService.getInstance()?.publish(topic, payload, qos = 1)

        // 标记已同步
        dao.markReadSynced(message.id)
    }

    suspend fun toggleStar(id: Long) = dao.toggleStar(id)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
    suspend fun clearAll() = dao.clearAll()
    suspend fun clearRead() = dao.clearRead()

    // ==================== 工厂方法 ====================

    companion object {
        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(context: Application): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildRepository(context).also { INSTANCE = it }
            }
        }

        private fun buildRepository(context: Application): MessageRepository {
            val db = PushDatabase.getInstance(context)
            return MessageRepository(db, context)
        }
    }
}

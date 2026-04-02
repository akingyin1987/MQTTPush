package com.push.core.repository

import android.content.Context
import com.push.core.model.MessageDao
import com.push.core.model.MessageFilter
import com.push.core.model.MessageStatus
import com.push.core.model.PushDatabase
import com.push.core.model.PushMessage
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库
 * 统一管理消息的 CRUD 操作
 */
class MessageRepository private constructor(database: PushDatabase) {

    private val dao: MessageDao = database.messageDao()

    // ==================== 消息查询 ====================

    /** 获取所有消息（分页） */
    fun getMessages(limit: Int = 50, offset: Int = 0): Flow<List<PushMessage>> =
        dao.getMessages(limit, offset)

    /** 按过滤器查询 */
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

    /** 获取未读消息 */
    fun getUnreadMessages(): Flow<List<PushMessage>> = dao.getUnreadMessages()

    /** 获取未读数量 */
    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    /** 获取最新未读（Top N），常用于通知条、悬浮提醒。 */
    fun getLatestUnread(limit: Int = 5): Flow<List<PushMessage>> = dao.getLatestUnread(limit)

    // ==================== 消息操作 ====================

    /** 插入消息 */
    suspend fun insert(message: PushMessage): Long = dao.insert(message)

    /** 批量插入 */
    suspend fun insertAll(messages: List<PushMessage>) = dao.insertAll(messages)

    /** 标记已读 */
    suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    /** 批量标记已读 */
    suspend fun markAsRead(ids: List<Long>) = dao.markAsRead(ids)

    /** 全部标记已读 */
    suspend fun markAllAsRead() = dao.markAllAsRead()

    /** 切换星标 */
    suspend fun toggleStar(id: Long) = dao.toggleStar(id)

    /** 删除消息 */
    suspend fun delete(id: Long) = dao.delete(id)

    /** 批量删除 */
    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    /** 清空所有消息 */
    suspend fun clearAll() = dao.clearAll()

    /** 清空已读消息 */
    suspend fun clearRead() = dao.clearRead()

    // ==================== 工厂方法 ====================

    companion object {
        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(context: Context): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildRepository(context).also { INSTANCE = it }
            }
        }

        fun getInstance(database: PushDatabase): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageRepository(database).also { INSTANCE = it }
            }
        }

        private fun buildRepository(context: Context): MessageRepository {
            val db = PushDatabase.getInstance(context)
            return MessageRepository(db)
        }
    }
}

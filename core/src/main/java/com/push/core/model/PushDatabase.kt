package com.push.core.model

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 消息 DAO
 */
@Dao
interface MessageDao {

    /** 插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PushMessage): Long

    /** 批量插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<PushMessage>)

    /** 查询所有消息（分页，按时间倒序） */
    @Query("SELECT * FROM push_messages ORDER BY receivedAt DESC LIMIT :limit OFFSET :offset")
    fun getMessages(limit: Int = 50, offset: Int = 0): Flow<List<PushMessage>>

    /** 按过滤器查询 */
    @Query("""
        SELECT * FROM push_messages
        WHERE (:isRead IS NULL OR isRead = :isRead)
        AND (:type IS NULL OR type = :type)
        AND (:topic IS NULL OR topic LIKE :topic || '%')
        AND (:keyword IS NULL OR payload LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%')
        ORDER BY receivedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getMessagesFiltered(
        isRead: Boolean? = null,
        type: String? = null,
        topic: String? = null,
        keyword: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<PushMessage>>

    /** 获取未读消息 */
    @Query("SELECT * FROM push_messages WHERE isRead = 0 ORDER BY receivedAt DESC")
    fun getUnreadMessages(): Flow<List<PushMessage>>

    /** 获取未读数量 */
    @Query("SELECT COUNT(*) FROM push_messages WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    /** 获取最近 N 条未读消息（用于 Top 5 展示） */
    @Query("SELECT * FROM push_messages WHERE isRead = 0 ORDER BY receivedAt DESC LIMIT :limit")
    fun getLatestUnread(limit: Int = 5): Flow<List<PushMessage>>

    /** 按 ID 查询 */
    @Query("SELECT * FROM push_messages WHERE id = :id")
    suspend fun getById(id: Long): PushMessage?

    /** 标记已读 */
    @Query("UPDATE push_messages SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    /** 批量标记已读（按 ID） */
    @Query("UPDATE push_messages SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markAsRead(ids: List<Long>)

    /** 全部标记已读 */
    @Query("UPDATE push_messages SET isRead = 1")
    suspend fun markAllAsRead()

    /** 切换星标 */
    @Query("UPDATE push_messages SET isStarred = NOT isStarred WHERE id = :id")
    suspend fun toggleStar(id: Long)

    /** 删除消息 */
    @Query("DELETE FROM push_messages WHERE id = :id")
    suspend fun delete(id: Long)

    /** 批量删除 */
    @Query("DELETE FROM push_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 按类型删除 */
    @Query("DELETE FROM push_messages WHERE type = :type")
    suspend fun deleteByType(type: String)

    /** 清空所有消息 */
    @Query("DELETE FROM push_messages")
    suspend fun clearAll()

    /** 清空已读消息 */
    @Query("DELETE FROM push_messages WHERE isRead = 1 AND isStarred = 0")
    suspend fun clearRead()

    /** 按时间清理过期消息 */
    @Query("DELETE FROM push_messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun clearExpired(now: Long = System.currentTimeMillis())

    /** 统计消息总数 */
    @Query("SELECT COUNT(*) FROM push_messages")
    fun getTotalCount(): Flow<Int>

    /** 获取指定主题的最新消息 */
    @Query("SELECT * FROM push_messages WHERE topic = :topic ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getLatestByTopic(topic: String): PushMessage?

    /** 更新消息通知状态 */
    @Query("UPDATE push_messages SET isNotified = 1 WHERE id = :id")
    suspend fun markAsNotified(id: Long)
}

/**
 * Room 数据库
 */
@Database(
    entities = [PushMessage::class],
    version = 1,
    exportSchema = false
)
abstract class PushDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: PushDatabase? = null

        fun getInstance(context: Context): PushDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): PushDatabase {
            return Room.databaseBuilder(
                context,
                PushDatabase::class.java,
                "push_message_db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

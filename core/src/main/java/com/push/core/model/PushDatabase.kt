package com.push.core.model

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 消息中心 DAO。
 *
 * 这里刻意只保留当前真实业务正在使用的查询和写入接口，避免“先留着以后也许会用”的方法越积越多，
 * 让仓库层和上层调用更清晰。后续如果新增场景，再按需求补回即可。
 */
@Dao
interface MessageDao {

    /** 插入单条消息；重复主键时以最新内容覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PushMessage): Long

    /** 批量插入，适合离线补录或导入历史消息。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<PushMessage>)

    /**
     * 查询消息列表。
     * 默认按接收时间倒序，适合消息中心首页直接展示。
     */
    @Query("SELECT * FROM push_messages ORDER BY receivedAt DESC LIMIT :limit OFFSET :offset")
    fun getMessages(limit: Int = 50, offset: Int = 0): Flow<List<PushMessage>>

    /**
     * 按筛选条件查询消息。
     * 其中 `topic` 采用前缀匹配，适合按业务命名空间过滤。
     */
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

    /** 获取全部未读消息，适合消息红点和通知条。 */
    @Query("SELECT * FROM push_messages WHERE isRead = 0 ORDER BY receivedAt DESC")
    fun getUnreadMessages(): Flow<List<PushMessage>>

    /** 获取未读数量。 */
    @Query("SELECT COUNT(*) FROM push_messages WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    /** 获取最近 N 条未读消息，供顶部通知条或悬浮提醒使用。 */
    @Query("SELECT * FROM push_messages WHERE isRead = 0 ORDER BY receivedAt DESC LIMIT :limit")
    fun getLatestUnread(limit: Int = 5): Flow<List<PushMessage>>

    /** 将单条消息标记为已读。 */
    @Query("UPDATE push_messages SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    /** 批量标记为已读。 */
    @Query("UPDATE push_messages SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markAsRead(ids: List<Long>)

    /** 全部标记为已读。 */
    @Query("UPDATE push_messages SET isRead = 1")
    suspend fun markAllAsRead()

    /** 切换星标状态。 */
    @Query("UPDATE push_messages SET isStarred = NOT isStarred WHERE id = :id")
    suspend fun toggleStar(id: Long)

    /** 删除单条消息。 */
    @Query("DELETE FROM push_messages WHERE id = :id")
    suspend fun delete(id: Long)

    /** 批量删除消息。 */
    @Query("DELETE FROM push_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 清空全部消息。 */
    @Query("DELETE FROM push_messages")
    suspend fun clearAll()

    /** 清空已读且未标星的消息。 */
    @Query("DELETE FROM push_messages WHERE isRead = 1 AND isStarred = 0")
    suspend fun clearRead()

    /** 根据 ID 获取单条消息 */
    @Query("SELECT * FROM push_messages WHERE id = :id")
    suspend fun getById(id: Long): PushMessage?

    /** 按服务端消息 ID 查重，避免 QoS1 重投或离线补发导致重复入库。 */
    @Query("SELECT * FROM push_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): PushMessage?

    /** 获取未读消息列表（非 Flow，用于批量处理） */
    @Query("SELECT * FROM push_messages WHERE isRead = 0")
    suspend fun getUnreadMessagesList(): List<PushMessage>

    /** 标记已读回执已同步 */
    @Query("UPDATE push_messages SET isReadSynced = 1 WHERE id = :id")
    suspend fun markReadSynced(id: Long)


}

/**
 * 消息中心本地数据库。
 *
 * 当前只承载 `push_messages` 一张表，后续如果新增会话、标签或附件缓存，
 * 再按版本演进 schema 即可。
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
                context.applicationContext,
                PushDatabase::class.java,
                "push_message_db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

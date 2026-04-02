package com.push.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────────
// 数据模型
// ─────────────────────────────────────────────────────────────────────────────

/** 消息发送状态 */
enum class QueuedMessageStatus {
    PENDING,   // 待发送
    SENDING,   // 发送中（临时占位，防止并发重复投递）
    FAILED     // 已超出最大重试次数，等待人工干预
}

/**
 * 持久化队列消息实体（存储到 Room）。
 * 与原先的内存 [ConcurrentLinkedQueue] 不同，这里的消息即使 Service 被杀死也不丢失。
 */
@Entity(tableName = "queued_messages")
data class QueuedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String,
    val payload: String,
    val qos: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: QueuedMessageStatus = QueuedMessageStatus.PENDING,
    /** 下次允许重试的时间戳（指数退避计算） */
    val nextRetryAt: Long = 0L
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface QueuedMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: QueuedMessage): Long

    @Update
    suspend fun update(message: QueuedMessage)

    @Query("DELETE FROM queued_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM queued_messages")
    suspend fun clearAll()

    /** 取出下一批可重试的 PENDING 消息（nextRetryAt ≤ now） */
    @Query(
        """
        SELECT * FROM queued_messages
        WHERE status = 'PENDING'
          AND nextRetryAt <= :now
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingMessages(now: Long = System.currentTimeMillis(), limit: Int = 20): List<QueuedMessage>

    /** 观察队列中待处理消息数量（用于 UI 展示） */
    @Query("SELECT COUNT(*) FROM queued_messages WHERE status = 'PENDING' OR status = 'SENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_messages WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>
}

// ─────────────────────────────────────────────────────────────────────────────
// Room Database（独立数据库，与 PushDatabase 解耦，便于单独迁移）
// ─────────────────────────────────────────────────────────────────────────────

@Database(entities = [QueuedMessage::class], version = 1, exportSchema = false)
abstract class MessageQueueDatabase : RoomDatabase() {
    abstract fun dao(): QueuedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: MessageQueueDatabase? = null

        fun getInstance(context: Context): MessageQueueDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessageQueueDatabase::class.java,
                    "message_queue_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 重试配置
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 指数退避重试配置。
 *
 * @param maxAttempts     最大重试次数，超过后消息标记为 FAILED
 * @param initialDelayMs  首次重试延迟（ms）
 * @param maxDelayMs      单次重试最大延迟上限（ms）
 * @param multiplier      退避倍率（默认 2.0 = 标准指数退避）
 * @param weakNetMultiplier 弱网环境下在 multiplier 基础上额外放大的系数
 */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 2_000L,
    val maxDelayMs: Long = 60_000L,
    val multiplier: Double = 2.0,
    val weakNetMultiplier: Double = 2.0
) {
    /**
     * 根据当前重试次数和网络质量计算下次重试的绝对时间戳。
     * 公式：initialDelay × (multiplier ^ retryCount)，上限 maxDelay。
     */
    fun nextRetryTimestamp(retryCount: Int, isPoorNetwork: Boolean): Long {
        val base = multiplier * if (isPoorNetwork) weakNetMultiplier else 1.0
        val delayMs = min(
            (initialDelayMs * base.pow(retryCount)).toLong(),
            if (isPoorNetwork) maxDelayMs * 2 else maxDelayMs
        )
        return System.currentTimeMillis() + delayMs
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MessageQueue
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 消息队列管理器（持久化版本）。
 *
 * 变更说明：
 * - 消息持久化到 Room，Service 被杀死后重启可继续处理未发送消息。
 * - 弱网环境下自动使用更长的指数退避间隔，避免频繁无效重试。
 * - 通过 [pendingCount] / [failedCount] Flow 暴露队列状态，方便 UI 订阅。
 */
class MessageQueue private constructor(context: Context) {

    private val db = MessageQueueDatabase.getInstance(context)
    private val dao = db.dao()

    // 独立的协程作用域，与 Service 生命周期解耦（WorkManager 也能共用）
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val retryPolicy = RetryPolicy()

    // ── 对外 Flow ─────────────────────────────────────────────────────────

    /** 观察队列中等待 / 发送中的消息数量 */
    val pendingCount: Flow<Int> = dao.observePendingCount()

    /** 观察已超出重试上限的失败消息数量 */
    val failedCount: Flow<Int> = dao.observeFailedCount()

    // ── 写入 API ──────────────────────────────────────────────────────────

    /**
     * 将新消息加入持久化队列。
     * @return 插入的行 ID（可用于后续追踪）
     */
    suspend fun enqueue(topic: String, payload: String, qos: Int = 0): Long =
        dao.insert(
            QueuedMessage(
                topic = topic,
                payload = payload,
                qos = qos,
                status = QueuedMessageStatus.PENDING,
                nextRetryAt = 0L
            )
        )

    /** [enqueue] 的非挂起快捷方法（在普通回调中使用） */
    fun enqueueAsync(topic: String, payload: String, qos: Int = 0) {
        scope.launch { enqueue(topic, payload, qos) }
    }

    // ── 消费 API ─────────────────────────────────────────────────────────

    /**
     * 获取下一批可立即发送的消息（[QueuedMessageStatus.PENDING] 且 nextRetryAt ≤ now）。
     */
    suspend fun dequeueBatch(limit: Int = 20): List<QueuedMessage> =
        withContext(Dispatchers.IO) {
            dao.getPendingMessages(limit = limit)
        }

    /**
     * 标记消息发送成功，从队列中删除。
     */
    suspend fun onSuccess(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }

    /**
     * 标记消息发送失败，根据重试策略决定重新入队或标记 FAILED。
     *
     * @param message       原始消息
     * @param isPoorNetwork 是否为弱网（影响退避时长）
     */
    suspend fun onFailure(message: QueuedMessage, isPoorNetwork: Boolean = false) =
        withContext(Dispatchers.IO) {
            val nextRetry = message.retryCount + 1
            if (nextRetry >= retryPolicy.maxAttempts) {
                // 超出重试上限 → 标记为 FAILED，不再自动重试
                dao.update(
                    message.copy(
                        retryCount = nextRetry,
                        status = QueuedMessageStatus.FAILED
                    )
                )
            } else {
                // 计算下次重试时间（指数退避）
                val nextAt = retryPolicy.nextRetryTimestamp(nextRetry, isPoorNetwork)
                dao.update(
                    message.copy(
                        retryCount = nextRetry,
                        status = QueuedMessageStatus.PENDING,
                        nextRetryAt = nextAt
                    )
                )
            }
        }

    /**
     * 将所有 FAILED 消息重置为 PENDING，允许人工触发重试。
     */
    suspend fun retryFailed() = withContext(Dispatchers.IO) {
        // 直接用 SQL 批量更新效率更高
        db.runInTransaction {
            scope.launch {
                dao.getPendingMessages(limit = Int.MAX_VALUE).forEach { msg ->
                    dao.update(
                        msg.copy(
                            status = QueuedMessageStatus.PENDING,
                            retryCount = 0,
                            nextRetryAt = 0L
                        )
                    )
                }
            }
        }
    }

    /** 清空所有队列消息（谨慎调用） */
    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clearAll() }

    /** 销毁时取消内部协程 */
    fun destroy() = scope.cancel()

    // ─────────────────────────────────────────────────────────────────────
    companion object {
        @Volatile
        private var INSTANCE: MessageQueue? = null

        fun getInstance(context: Context): MessageQueue =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageQueue(context.applicationContext).also { INSTANCE = it }
            }
    }
}

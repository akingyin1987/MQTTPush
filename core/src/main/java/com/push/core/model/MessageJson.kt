package com.push.core.model

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息 JSON 导出工具
 * 将消息列表或单条消息导出为格式化 JSON，方便调试、日志和接口对接
 */
object MessageJson {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 导出单条消息为 JSON 字符串
     */
    fun toJson(message: PushMessage): String {
        return gson.toJson(MessageJsonBean.from(message))
    }

    /**
     * 导出消息列表为 JSON 字符串
     */
    fun listToJson(messages: List<PushMessage>): String {
        return gson.toJson(messages.map { MessageJsonBean.from(it) })
    }

    /**
     * 导出消息列表为 JSON 并附带元数据
     * 输出格式: { total: Int, unread: Int, messages: [...] }
     */
    fun listToJsonWithMeta(messages: List<PushMessage>, unreadCount: Int): String {
        val meta = JsonMeta(
            total = messages.size,
            unread = unreadCount,
            exportedAt = dateFormat.format(Date()),
            messages = messages.map { MessageJsonBean.from(it) }
        )
        return gson.toJson(meta)
    }

    /**
     * 从 JSON 字符串解析为 PushMessage（支持单条和列表）
     */
    fun fromJson(json: String): List<PushMessage> {
        return try {
            val trimmed = json.trim()
            when {
                trimmed.startsWith("[") -> {
                    val beans = gson.fromJson(trimmed, Array<MessageJsonBean>::class.java)
                    beans.map { it.toPushMessage() }
                }
                trimmed.startsWith("{") -> {
                    val beans = gson.fromJson(trimmed, Array<MessageJsonBean>::class.java)
                    beans.map { it.toPushMessage() }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== 内部 Bean（用于格式化输出） ====================

    data class MessageJsonBean(
        val id: Long,
        val topic: String,
        val payload: String,
        val qos: Int,
        val isRead: Boolean,
        val isStarred: Boolean,
        val type: String,
        val typeName: String,
        val title: String,
        val content: String,
        val receivedAt: String,
        val expiresAt: String,
        val isNotified: Boolean
    ) {
        fun toPushMessage(): PushMessage {
            val receivedTs = try { dateFormat.parse(receivedAt)?.time ?: receivedAt.toLong() } catch (e: Exception) { System.currentTimeMillis() }
            val expiresTs = try { if (expiresAt == "永不过期") 0L else dateFormat.parse(expiresAt)?.time ?: 0L } catch (e: Exception) { 0L }
            return PushMessage(
                id = id, topic = topic, payload = payload, qos = qos,
                isRead = isRead, isStarred = isStarred,
                type = MessageType.fromValue(type),
                title = title, content = content,
                isNotified = isNotified,
                receivedAt = receivedTs, expiresAt = expiresTs
            )
        }

        companion object {
            fun from(msg: PushMessage): MessageJsonBean {
                return MessageJsonBean(
                    id = msg.id, topic = msg.topic, payload = msg.payload, qos = msg.qos,
                    isRead = msg.isRead, isStarred = msg.isStarred,
                    type = msg.type.value, typeName = msg.type.displayName,
                    title = msg.title, content = msg.content,
                    receivedAt = dateFormat.format(Date(msg.receivedAt)),
                    expiresAt = if (msg.expiresAt == 0L) "永不过期" else dateFormat.format(Date(msg.expiresAt)),
                    isNotified = msg.isNotified
                )
            }
        }
    }

    data class JsonMeta(
        val total: Int,
        val unread: Int,
        val exportedAt: String,
        val messages: List<MessageJsonBean>
    )
}

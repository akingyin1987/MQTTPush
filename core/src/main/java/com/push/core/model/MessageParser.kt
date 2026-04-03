package com.push.core.model

/**
 * 消息解析器接口
 * 
 * 不同业务可能有不同的消息格式，通过接口抽象支持：
 * - JSON 格式解析
 * - Protobuf 格式解析
 * - 自定义格式解析
 */
interface MessageParser {
    
    /**
     * 解析 MQTT 消息
     * 
     * @param topic MQTT 主题
     * @param payload 原始 payload 字符串
     * @return 解析后的 PushMessage，解析失败返回 null
     */
    fun parse(topic: String, payload: String): PushMessage?
}

/**
 * 默认 JSON 消息解析器
 * 
 * 支持格式：
 * ```json
 * {
 *   "id": "msg-uuid",
 *   "title": "消息标题",
 *   "content": "消息内容",
 *   "type": "notification",
 *   "extra": { ... }
 * }
 * ```
 */
class JsonMessageParser : MessageParser {
    
    private val gson = com.google.gson.Gson()
    
    override fun parse(topic: String, payload: String): PushMessage? {
        return try {
            val json = gson.fromJson(payload, com.google.gson.JsonObject::class.java)
            
            PushMessage(
                topic = topic,
                payload = payload,
                title = json.get("title")?.asString ?: "",
                content = json.get("content")?.asString ?: "",
                type = parseMessageType(json.get("type")?.asString),
                messageId = json.get("id")?.asString ?: "",
                sourceApp = json.get("sourceApp")?.asString ?: "mqtt",
                receivedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // 解析失败，返回原始 payload 作为 content
            PushMessage(
                topic = topic,
                payload = payload,
                title = topic.substringAfterLast('/'),
                content = payload,
                type = MessageType.NOTIFICATION
            )
        }
    }
    
    private fun parseMessageType(type: String?): MessageType {
        return when (type?.lowercase()) {
            "notification" -> MessageType.NOTIFICATION
            "alert" -> MessageType.ALERT
            "system" -> MessageType.SYSTEM

            else -> MessageType.NOTIFICATION
        }
    }
}

/**
 * 简单文本消息解析器
 * 
 * 直接把 payload 作为 content，topic 最后一节作为 title
 */
class SimpleTextMessageParser : MessageParser {
    
    override fun parse(topic: String, payload: String): PushMessage? {
        return PushMessage(
            topic = topic,
            payload = payload,
            title = topic.substringAfterLast('/'),
            content = payload,
            type = MessageType.NOTIFICATION,
            receivedAt = System.currentTimeMillis()
        )
    }
}

package com.push.core.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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
 *   "contentType": "text|json|link",
 *   "url": "https://example.com",
 *   "type": "notification"
 * }
 * ```
 */
class JsonMessageParser : MessageParser {

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    override fun parse(topic: String, payload: String): PushMessage? {
        return try {
            val json = gson.fromJson(payload, JsonObject::class.java)
            val contentElement = json.get("content") ?: json.get("body") ?: json.get("data")
            val explicitContentType = json.get("contentType")?.asString ?: json.get("format")?.asString
            val linkUrl = listOf("url", "link", "href")
                .firstNotNullOfOrNull { key -> json.get(key)?.takeIf { !it.isJsonNull }?.let(::jsonElementToString) }
                ?.trim()
                .orEmpty()
            val contentType = inferContentType(explicitContentType, contentElement, linkUrl)
            val content = buildContent(json, contentElement, contentType)
            val finalLinkUrl = when {
                linkUrl.isNotBlank() -> linkUrl
                contentType == MessageContentType.LINK && content.looksLikeUrl() -> content
                else -> ""
            }

            PushMessage(
                topic = topic,
                payload = payload,
                title = json.stringValue("title").ifBlank { topic.substringAfterLast('/') },
                content = content,
                contentType = contentType,
                contentUrl = finalLinkUrl,
                type = parseMessageType(json.get("type")?.asString),
                messageId = json.stringValue("id"),
                sourceApp = json.stringValue("sourceApp").ifBlank { "mqtt" },
                receivedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            buildFallbackMessage(topic, payload)
        }
    }

    private fun inferContentType(
        explicitType: String?,
        contentElement: JsonElement?,
        linkUrl: String
    ): MessageContentType {
        if (!explicitType.isNullOrBlank()) return MessageContentType.fromValue(explicitType)
        if (linkUrl.isNotBlank()) return MessageContentType.LINK
        if (contentElement?.isJsonObject == true || contentElement?.isJsonArray == true) {
            return MessageContentType.JSON
        }
        if (contentElement?.isJsonPrimitive == true && contentElement.asString.looksLikeUrl()) {
            return MessageContentType.LINK
        }
        return MessageContentType.TEXT
    }

    private fun buildContent(
        json: JsonObject,
        contentElement: JsonElement?,
        contentType: MessageContentType
    ): String {
        return when (contentType) {
            MessageContentType.TEXT -> when {
                contentElement == null || contentElement.isJsonNull -> {
                    json.stringValue("description")
                        .ifBlank { json.stringValue("summary") }
                        .ifBlank { json.stringValue("text") }
                }
                contentElement.isJsonPrimitive -> contentElement.asString
                else -> prettyJson(contentElement)
            }
            MessageContentType.JSON -> when {
                contentElement == null || contentElement.isJsonNull -> prettyJson(json)
                contentElement.isJsonPrimitive -> prettyJson(contentElement.asString)
                else -> prettyJson(contentElement)
            }
            MessageContentType.LINK -> when {
                contentElement == null || contentElement.isJsonNull -> {
                    json.stringValue("description")
                        .ifBlank { json.stringValue("summary") }
                        .ifBlank { json.stringValue("text") }
                }
                contentElement.isJsonPrimitive -> contentElement.asString
                else -> prettyJson(contentElement)
            }
        }
    }

    private fun buildFallbackMessage(topic: String, payload: String): PushMessage {
        val trimmed = payload.trim()
        val contentType = when {
            trimmed.looksLikeUrl() -> MessageContentType.LINK
            trimmed.startsWith("{") || trimmed.startsWith("[") -> MessageContentType.JSON
            else -> MessageContentType.TEXT
        }

        return PushMessage(
            topic = topic,
            payload = payload,
            title = topic.substringAfterLast('/'),
            content = when (contentType) {
                MessageContentType.JSON -> prettyJson(payload)
                else -> payload
            },
            contentType = contentType,
            contentUrl = if (contentType == MessageContentType.LINK) trimmed else "",
            type = MessageType.NOTIFICATION,
            receivedAt = System.currentTimeMillis()
        )
    }

    private fun parseMessageType(type: String?): MessageType {
        return when (type?.lowercase()) {
            "notification" -> MessageType.NOTIFICATION
            "alert" -> MessageType.ALERT
            "system" -> MessageType.SYSTEM
            else -> MessageType.NOTIFICATION
        }
    }

    private fun JsonObject.stringValue(key: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.let(::jsonElementToString).orEmpty()
    }

    private fun jsonElementToString(element: JsonElement): String {
        return runCatching { element.asString }.getOrElse { prettyJson(element) }
    }

    private fun prettyJson(element: JsonElement): String = prettyGson.toJson(element)

    private fun prettyJson(raw: String): String {
        return runCatching {
            prettyGson.toJson(JsonParser.parseString(raw))
        }.getOrElse { raw }
    }
}

/**
 * 简单文本消息解析器
 *
 * 直接把 payload 作为 content，topic 最后一节作为 title。
 * 若 payload 本身是 URL，则自动按链接消息处理。
 */
class SimpleTextMessageParser : MessageParser {

    override fun parse(topic: String, payload: String): PushMessage {
        val trimmed = payload.trim()
        val isLink = trimmed.looksLikeUrl()
        return PushMessage(
            topic = topic,
            payload = payload,
            title = topic.substringAfterLast('/'),
            content = payload,
            contentType = if (isLink) MessageContentType.LINK else MessageContentType.TEXT,
            contentUrl = if (isLink) trimmed else "",
            type = MessageType.NOTIFICATION,
            receivedAt = System.currentTimeMillis()
        )
    }
}

private fun String.looksLikeUrl(): Boolean {
    val normalized = trim()
    return normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("https://", ignoreCase = true)
}

package com.push.core.model

/**
 * 用户会话信息
 */
data class UserSession(
    val userId: String,
    val token: String = "",
    val tokenExpiresAt: Long = 0L,
    val appId: String = "app1",
    val groupIds: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val loginAt: Long = System.currentTimeMillis(),
    val subscribeBroadcast: Boolean = true,
    /** 主题生成器（默认使用 DefaultTopicGenerator） */
    val topicGenerator: TopicGenerator = DefaultTopicGenerator()
) {

    // ── 主题构造（委托给 TopicGenerator）─────────────────────────────────

    /** 用户专属订阅主题 */
    val userTopic: String
        get() = topicGenerator.userSubscribeTopic(userId, appId)

    /** 分组订阅主题列表 */
    val groupTopics: List<String>
        get() = groupIds.map { topicGenerator.groupSubscribeTopic(it, appId) }

    /** 广播订阅主题 */
    val broadcastTopic: String
        get() = topicGenerator.broadcastSubscribeTopic(appId)

    /** 已读回执发布主题 */
    val readReceiptTopic: String
        get() = topicGenerator.readReceiptTopic(userId, appId)

    /** 当前会话需要订阅的全部主题 */
    val subscribedTopics: List<String>
        get() = buildList {
            add(userTopic)
            addAll(groupTopics)
            if (subscribeBroadcast) add(broadcastTopic)
        }

    // ── Token 工具 ───────────────────────────────────────────────────────

    fun isTokenExpired(bufferMs: Long = 60_000L): Boolean {
        if (tokenExpiresAt == 0L) return false
        return System.currentTimeMillis() >= tokenExpiresAt - bufferMs
    }

    fun isTokenValid(): Boolean = token.isNotBlank() && !isTokenExpired()

    fun withRefreshedToken(newToken: String, expiresAt: Long = 0L): UserSession =
        copy(token = newToken, tokenExpiresAt = expiresAt)

    override fun toString(): String =
        "UserSession(userId=$userId, appId=$appId, " +
        "tokenValid=${isTokenValid()}, groups=${groupIds.size}, loginAt=$loginAt)"
}

sealed class LoginResult {
    data class Success(val session: UserSession) : LoginResult()
    data class Error(val message: String, val code: Int = -1) : LoginResult()
}

sealed class LogoutResult {
    data object Success : LogoutResult()
    data class Error(val message: String) : LogoutResult()
}

sealed class TokenRefreshResult {
    data class Success(val session: UserSession) : TokenRefreshResult()
    data class Failure(val reason: String, val originalSession: UserSession) : TokenRefreshResult()
    data object SessionExpired : TokenRefreshResult()
}

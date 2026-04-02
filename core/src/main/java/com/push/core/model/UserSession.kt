package com.push.core.model

/**
 * 用户会话信息
 */
data class UserSession(
    val userId: String,
    val groupIds: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val loginAt: Long = System.currentTimeMillis(),
    /** 是否订阅全量广播 */
    val subscribeBroadcast: Boolean = true
) {
    /** 用户专属主题 */
    val userTopic: String get() = "push/app1/user/$userId/#"

    /** 分组主题列表 */
    val groupTopics: List<String> get() = groupIds.map { "push/app1/group/$it/#" }

    /** 广播主题 */
    val broadcastTopic: String get() = "push/app1/broadcast/#"

    /**
     * 所有需要订阅的主题
     * - 用户专属：push/app1/user/{userId}/#
     * - 分组专属：push/app1/group/{groupId}/#
     * - 广播（可选）：push/app1/broadcast/#
     */
    val subscribedTopics: List<String>
        get() = buildList {
            add(userTopic)
            addAll(groupTopics)
            if (subscribeBroadcast) add(broadcastTopic)
        }
}

sealed class LoginResult {
    data class Success(val session: UserSession) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class LogoutResult {
    data object Success : LogoutResult()
    data class Error(val message: String) : LogoutResult()
}

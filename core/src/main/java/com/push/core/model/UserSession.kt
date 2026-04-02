package com.push.core.model

// ─────────────────────────────────────────────────────────────────────────────
// UserSession
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 用户会话信息。
 *
 * ### 变更说明（重构）
 * - 新增 [token]：用于 MQTT 连接认证（Bearer Token / JWT）。
 * - 新增 [tokenExpiresAt]：过期时间戳（ms），0 表示永不过期。
 * - 新增 [appId]：支持多 App 场景，主题前缀由 appId 动态生成，不再硬编码 "app1"。
 * - 工具方法：[isTokenExpired]、[isTokenValid]、[withRefreshedToken]。
 * - 主题构造方法抽离为扩展函数，便于单元测试和复用。
 */
data class UserSession(
    /** 用户唯一标识 */
    val userId: String,

    /**
     * 认证令牌（Bearer Token / JWT）。
     * 用于 MQTT 连接时的 username/password 鉴权：
     * - username = userId
     * - password = token
     */
    val token: String = "",

    /** token 过期时间戳（毫秒）；0 = 永不过期 */
    val tokenExpiresAt: Long = 0L,

    /** 应用标识，用于构建 MQTT 主题前缀，默认 "app1" */
    val appId: String = "app1",

    /** 所属分组 ID 列表（用于订阅分组主题） */
    val groupIds: List<String> = emptyList(),

    /** 扩展字段（业务自定义 KV，不影响核心逻辑） */
    val extras: Map<String, String> = emptyMap(),

    /** 登录时间戳（毫秒） */
    val loginAt: Long = System.currentTimeMillis(),

    /** 是否订阅全量广播 */
    val subscribeBroadcast: Boolean = true
) {

    // ── 主题构造 ─────────────────────────────────────────────────────────

    /** 用户专属主题：push/{appId}/user/{userId}/# */
    val userTopic: String
        get() = "push/$appId/user/$userId/#"

    /** 分组主题列表：push/{appId}/group/{groupId}/# */
    val groupTopics: List<String>
        get() = groupIds.map { "push/$appId/group/$it/#" }

    /** 广播主题：push/{appId}/broadcast/# */
    val broadcastTopic: String
        get() = "push/$appId/broadcast/#"

    /**
     * 当前会话需要订阅的全部主题。
     * 顺序：用户专属 → 分组 → 广播（可选）
     */
    val subscribedTopics: List<String>
        get() = buildList {
            add(userTopic)
            addAll(groupTopics)
            if (subscribeBroadcast) add(broadcastTopic)
        }

    // ── Token 工具 ───────────────────────────────────────────────────────

    /**
     * 检查 token 是否已过期。
     * - [tokenExpiresAt] == 0L 视为永不过期，始终返回 false。
     * - 提前 [bufferMs]（默认 60 s）触发过期，为刷新留出时间窗口。
     */
    fun isTokenExpired(bufferMs: Long = 60_000L): Boolean {
        if (tokenExpiresAt == 0L) return false
        return System.currentTimeMillis() >= tokenExpiresAt - bufferMs
    }

    /** token 是否有效（非空且未过期） */
    fun isTokenValid(): Boolean = token.isNotBlank() && !isTokenExpired()

    /**
     * 用新 token 创建更新后的会话副本（immutable 更新）。
     *
     * 使用示例：
     * ```kotlin
     * val refreshed = session.withRefreshedToken(newToken, expiresAt)
     * ```
     */
    fun withRefreshedToken(newToken: String, expiresAt: Long = 0L): UserSession =
        copy(token = newToken, tokenExpiresAt = expiresAt)

    // ── 日志友好展示（脱敏） ─────────────────────────────────────────────

    override fun toString(): String =
        "UserSession(userId=$userId, appId=$appId, " +
        "tokenValid=${isTokenValid()}, groups=${groupIds.size}, loginAt=$loginAt)"
}

// ─────────────────────────────────────────────────────────────────────────────
// 登录 / 登出结果
// ─────────────────────────────────────────────────────────────────────────────

sealed class LoginResult {
    data class Success(val session: UserSession) : LoginResult()
    data class Error(val message: String, val code: Int = -1) : LoginResult()
}

sealed class LogoutResult {
    data object Success : LogoutResult()
    data class Error(val message: String) : LogoutResult()
}

// ─────────────────────────────────────────────────────────────────────────────
// Token 刷新结果
// ─────────────────────────────────────────────────────────────────────────────

sealed class TokenRefreshResult {
    /** 刷新成功，携带更新后的会话 */
    data class Success(val session: UserSession) : TokenRefreshResult()

    /** 刷新失败（网络错误、服务端拒绝等），携带原会话，上层自行决定是否降级 */
    data class Failure(val reason: String, val originalSession: UserSession) : TokenRefreshResult()

    /** Token 已完全失效（如被踢出登录），需要重新登录 */
    data object SessionExpired : TokenRefreshResult()
}

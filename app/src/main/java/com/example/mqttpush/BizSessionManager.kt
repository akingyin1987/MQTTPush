package com.example.mqttpush

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 业务系统登录信息
 *
 * 这里只是一个示例模型，实际项目中替换成你们业务系统的用户模型即可。
 * 例如：来自 JWT 解析、SSO 回调、或本地持久化的登录态。
 */
data class BizUser(
    /** 业务系统的唯一用户 ID（工号、用户名、UUID 等皆可）*/
    val userId: String,
    /** 用户所属角色/分组，用于订阅分组推送主题（可选）*/
    val groupIds: List<String> = emptyList(),
    /**
     * MQTT 鉴权 token（可选）。
     *
     * 如果 Broker 开启了账号密码鉴权，可以让后端在登录时一并返回 mqttToken，
     * 这里透传给 BrokerConfig.password。
     * 如果 Broker 不鉴权，留空即可。
     */
    val mqttToken: String = ""
)

/**
 * 业务系统登录状态管理器（单例）
 *
 * 职责：
 * 1. 持久化业务登录信息（SharedPreferences / DataStore / 你们自己的 SessionStore 均可）
 * 2. 暴露 [userFlow]，让 [MyApplication] 监听登录/登出事件，自动驱动推送登录/登出
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 实际项目替换指引：
 * - 把 SharedPreferences 替换成你们的用户 Store / DataStore
 * - 把 login() / logout() 的调用时机改为业务系统登录成功/退出登录的回调
 * - BizUser 里的字段按业务需要增删
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
object BizSessionManager {

    private const val PREFS_NAME  = "biz_session"
    private const val KEY_USER_ID = "userId"
    private const val KEY_GROUPS  = "groupIds"
    private const val KEY_TOKEN   = "mqttToken"

    private val _userFlow = MutableStateFlow<BizUser?>(null)

    /**
     * 当前登录用户 Flow（null = 未登录）
     *
     * MyApplication 会 collect 这个 Flow：
     * - emit BizUser → 自动调用 pushManager.login()
     * - emit null   → 自动调用 pushManager.logout()
     */
    val userFlow: StateFlow<BizUser?> = _userFlow.asStateFlow()

    /**
     * 初始化：从持久化存储恢复上次登录态（App 重启后自动恢复）
     *
     * 在 Application.onCreate() 最早调用。
     */
    fun init(context: Context) {
        val prefs = prefs(context)
        val savedUserId = prefs.getString(KEY_USER_ID, null)
        if (!savedUserId.isNullOrBlank()) {
            val groups = prefs.getString(KEY_GROUPS, "")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val token = prefs.getString(KEY_TOKEN, "") ?: ""
            _userFlow.value = BizUser(
                userId   = savedUserId,
                groupIds = groups,
                mqttToken = token
            )
        }
    }

    /**
     * 业务系统登录成功后调用此方法。
     *
     * 示例调用时机：
     * - SSO/OAuth 回调拿到用户信息后
     * - JWT 验证通过，从 token 解析出 userId 后
     * - 自有账号密码登录接口返回成功后
     */
    fun login(context: Context, userId: String, groupIds: List<String> = emptyList(), mqttToken: String = "") {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_GROUPS, groupIds.joinToString(","))
            .putString(KEY_TOKEN, mqttToken)
            .apply()
        _userFlow.value = BizUser(userId = userId, groupIds = groupIds, mqttToken = mqttToken)
    }

    /**
     * 业务系统退出登录后调用此方法。
     */
    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
        _userFlow.value = null
    }

    /**
     * 获取当前缓存的 userId（用于构建稳定 ClientId，无需等 Flow 就绪）
     */
    fun getCachedUserId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

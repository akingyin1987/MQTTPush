package com.example.mqttpush.navigation

import com.push.core.model.ConnectionStatus
import com.push.core.model.UserSession
import kotlinx.serialization.Serializable

/**
 * 应用导航路由定义
 * 
 * 使用 Kotlinx Serialization 实现类型安全的导航参数传递
 */
sealed interface Route {

    /**
     * 连接配置页（初始状态 / 断开后）
     */
    @Serializable
    data object Connect : Route

    /**
     * 登录页（已连接 broker，但未登录）
     * @param isSessionCleared true = 从登出跳转，broker 仍连接；false = 首次登录
     */
    @Serializable
    data class Login(val isSessionCleared: Boolean = false) : Route

    /**
     * 连接中加载页
     * @param isReconnect true = 重连中，false = 首次连接
     */
    @Serializable
    data class Connecting(val isReconnect: Boolean = false) : Route

    /**
     * 主内容页（已连接 + 已登录）
     */
    @Serializable
    data class Main(
        val userId: String,
        val appId: String = "app1"
    ) : Route
}

/**
 * 根据连接状态和登录状态决定目标路由
 */
fun resolveRoute(
    connectionStatus: ConnectionStatus,
    isLoggedIn: Boolean,
    session: UserSession?
): Route = when {
    // 未连接或错误 → 连接配置页
    connectionStatus == ConnectionStatus.Disconnected ||
    connectionStatus is ConnectionStatus.Error -> Route.Connect

    // SessionCleared → 登录页（broker 已连接）
    connectionStatus == ConnectionStatus.SessionCleared -> Route.Login(isSessionCleared = true)

    // 连接中 / 重连中
    connectionStatus == ConnectionStatus.Connecting -> Route.Connecting(isReconnect = false)
    connectionStatus == ConnectionStatus.Reconnecting -> Route.Connecting(isReconnect = true)

    // 已连接但未登录 → 登录页
    !isLoggedIn -> Route.Login(isSessionCleared = false)

    // 已连接且已登录 → 主内容页
    else -> Route.Main(
        userId = session?.userId ?: "unknown",
        appId = session?.appId ?: "app1"
    )
}

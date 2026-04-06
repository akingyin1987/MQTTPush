package com.example.mqttpush.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.mqttpush.ui.*
import com.push.core.model.ConnectionStatus
import com.push.core.viewmodel.PushViewModel
import com.push.ui.compose.screen.LoginScreen

/**
 * 应用导航图
 * 
 * 根据连接状态和登录状态自动路由到对应页面
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: PushViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),

) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val session by viewModel.currentSession.collectAsState()

    // 自动路由：状态变化时自动导航到对应页面
    LaunchedEffect(connectionStatus, isLoggedIn) {
        val target = resolveRoute(connectionStatus, isLoggedIn, session)
        // 只在当前路由与目标路由不同时导航
        val current = navController.currentDestination?.route
        if (current != target.routeName()) {
            navController.navigate(target) {
                // 清空 back stack，避免回退到错误状态
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Connect,
        modifier = modifier
    ) {
        // 连接配置页
        composable<Route.Connect> {
            ConnectionSetupScreen(
                connectionStatus = connectionStatus,
                onConnect = viewModel::connect
            )
        }

        // 登录页
        composable<Route.Login> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Login>()
            LoginScreen(
                viewModel = viewModel,
                isSessionCleared = route.isSessionCleared,
                onLoginSuccess = { session ->
                    // 登录成功 → 导航到主内容页
                    navController.navigate(Route.Main(
                        userId = session.userId,
                        appId = session.appId
                    )) {
                        popUpTo(Route.Connect) { inclusive = false }
                    }
                }
            )
        }

        // 连接中加载页
        composable<Route.Connecting> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Connecting>()
            ConnectingScreen(
                status = if (route.isReconnect) {
                    ConnectionStatus.Reconnecting
                } else {
                    ConnectionStatus.Connecting
                },
                onError = {
                    navController.navigate(Route.Connect) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // 主内容页
        composable<Route.Main> {
            MainContent(viewModel)
        }
    }
}

/**
 * 获取路由名称（用于比较）
 */
private fun Route.routeName(): String = when (this) {
    is Route.Connect -> "Connect"
    is Route.Login -> "Login"
    is Route.Connecting -> "Connecting"
    is Route.Main -> "Main"
}

package com.example.mqttpush.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.mqttpush.ui.ConnectingScreen
import com.example.mqttpush.ui.ConnectionScreen
import com.example.mqttpush.ui.MainContent
import com.push.core.model.ConnectionStatus
import com.push.core.viewmodel.PushViewModel
import com.push.ui.compose.screen.LoginScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: PushViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),

) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    val savedBrokerConfig by viewModel.savedBrokerConfig.collectAsState()

    // 记录上一次导航到的路由，避免重复导航
    var lastRoute by remember { mutableStateOf<String?>(null) }
    var hasAttemptedAutoRestore by remember { mutableStateOf(false) }

    LaunchedEffect(session, savedBrokerConfig) {
        if (session == null || savedBrokerConfig == null) {
            hasAttemptedAutoRestore = false
        }
    }

    LaunchedEffect(connectionStatus, session, savedBrokerConfig, hasAttemptedAutoRestore) {
        if (!hasAttemptedAutoRestore && session != null && savedBrokerConfig != null && connectionStatus == ConnectionStatus.Disconnected) {
            hasAttemptedAutoRestore = true
            viewModel.restoreConnectionIfNeeded()
        }
    }

    // 监听连接状态和登录状态变化，触发路由导航
    LaunchedEffect(connectionStatus, isLoggedIn, session) {
        val target = resolveRoute(connectionStatus, isLoggedIn, session)
        val targetName = target.routeName()
        if (lastRoute != targetName) {
            lastRoute = targetName
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Connect,
        modifier = modifier
    ) {
        composable<Route.Connect> {
            ConnectionScreen(
                connectionStatus = connectionStatus,
                currentSession = session,
                initialConfig = savedBrokerConfig,
                onConnect = viewModel::connect,
                onLogout = { viewModel.logout() },
                onDisconnect = {
                    navController.navigate(Route.Connect) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<Route.Login> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Login>()
            LoginScreen(
                viewModel = viewModel,

                isSessionCleared = route.isSessionCleared,
                onLoginSuccess = { s ->
                    navController.navigate(Route.Main(
                        userId = s.userId,
                        appId = s.appId
                    )) {
                        popUpTo(Route.Connect) { inclusive = false }
                    }
                },

            )
        }

        composable<Route.Connecting> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Connecting>()
            ConnectingScreen(
                status = if (route.isReconnect) ConnectionStatus.Reconnecting else ConnectionStatus.Connecting,
                onError = {
                    navController.navigate(Route.Connect) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<Route.Main> {
            MainContent(
                viewModel = viewModel,
                initialConfig = savedBrokerConfig
            )
        }
    }
}

private fun Route.routeName(): String = when (this) {
    is Route.Connect -> "Connect"
    is Route.Login -> "Login"
    is Route.Connecting -> "Connecting"
    is Route.Main -> "Main"
}

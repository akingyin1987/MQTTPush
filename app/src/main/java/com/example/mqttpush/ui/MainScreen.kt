package com.example.mqttpush.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.push.core.model.BrokerConfig
import com.push.core.model.ConnectionStatus
import com.push.core.viewmodel.PushViewModel
import com.push.ui.compose.screen.LoginScreen
import com.push.ui.compose.screen.MessageListScreen
import com.push.ui.compose.screen.SessionCard
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainScreen(viewModel: PushViewModel = viewModel()) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Debug 日志
    LaunchedEffect(connectionStatus) {
        Log.d("MainScreen", "connectionStatus changed: $connectionStatus, isLoggedIn: $isLoggedIn")
    }

    // 第一步：未连接或错误 → 显示连接配置
    if (connectionStatus == ConnectionStatus.Disconnected || connectionStatus is ConnectionStatus.Error) {
        ConnectionSetupScreen(
            connectionStatus = connectionStatus,
            onConnect = viewModel::connect
        )
        return
    }

    // 连接中 → 显示加载页
    if (connectionStatus == ConnectionStatus.Connecting || connectionStatus == ConnectionStatus.Reconnecting) {
        ConnectingScreen(connectionStatus)
        return
    }

    // 第二步：已连接 → 检查登录状态
    // Connected 状态下，根据登录状态显示不同页面
    if (!isLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { /* 登录成功自动跳转 */ }
        )
        return
    }

    // 第三步：已登录 → 显示主界面
    MainContent(viewModel = viewModel)
}

/**
 * 连接中页面
 */
@Composable
fun ConnectingScreen(status: ConnectionStatus) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            when (status) {
                is ConnectionStatus.Connecting -> "正在连接 Broker..."
                is ConnectionStatus.Reconnecting -> "正在重连..."
                else -> "连接中..."
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "请稍候",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 连接配置页面（首页）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupScreen(
    connectionStatus: ConnectionStatus,
    onConnect: (BrokerConfig) -> Unit
) {
    var host by remember { mutableStateOf("10.0.2.2") }
    var port by remember { mutableStateOf("1883") }
    var clientId by remember { mutableStateOf("android-client-${System.currentTimeMillis() % 10000}") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf("") }

    // 直接用 connectionStatus 判断状态
    val isConnecting = connectionStatus == ConnectionStatus.Connecting ||
                       connectionStatus == ConnectionStatus.Reconnecting

    val isError = connectionStatus is ConnectionStatus.Error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Logo + 标题
        Text("📡", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "MQTT Push",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "配置 Broker 连接",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))

        // 连接状态指示器（直接用 connectionStatus，不需要额外的 timeout 状态）
        ConnectionStatusIndicator(connectionStatus)

        Spacer(Modifier.height(16.dp))

        // 配置表单
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 服务器 + 端口
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; showError = "" },
                        label = { Text("服务器") },
                        placeholder = { Text("IP 或域名") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Cloud, null) },
                        isError = showError.contains("服务器") || showError.contains("网络")
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it; showError = "" },
                        label = { Text("端口") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Numbers, null) },
                        isError = showError.contains("端口")
                    )
                }

                // Client ID
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    placeholder = { Text("唯一标识") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Fingerprint, null) }
                )

                HorizontalDivider()

                // 认证（可选）
                Text(
                    "认证（可选）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 连接按钮
        Button(
            onClick = {
                showError = ""
                if (host.isBlank()) {
                    showError = "服务器地址不能为空"
                    return@Button
                }
                val portNum = port.toIntOrNull()
                if (portNum == null || portNum <= 0 || portNum > 65535) {
                    showError = "端口号无效 (1-65535)"
                    return@Button
                }
                if (clientId.isBlank()) {
                    clientId = "client-${System.currentTimeMillis()}"
                }

                onConnect(
                    BrokerConfig(
                        host = host.trim(),
                        port = portNum,
                        clientId = clientId.trim(),
                        username = username.takeIf { it.isNotBlank() },
                        password = password.takeIf { it.isNotBlank() }
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isConnecting,
            shape = MaterialTheme.shapes.medium
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("连接中...", fontWeight = FontWeight.Medium)
            } else {
                Icon(Icons.Default.Cable, null)
                Spacer(Modifier.width(8.dp))
                Text("连接 Broker", fontWeight = FontWeight.Bold)
            }
        }

        // 错误提示（本地校验错误 或 连接错误）
        AnimatedVisibility(visible = showError.isNotBlank() || isError) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        // 优先显示本地校验错误，否则显示连接错误
                        showError.ifBlank {
                            if (isError) "连接失败: ${(connectionStatus as ConnectionStatus.Error).message}"
                            else ""
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 提示
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "模拟器用 10.0.2.2，真机用电脑局域网 IP (如 192.168.x.x)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 连接状态指示器（直接用 connectionStatus，不需要额外状态）
 */
@Composable
fun ConnectionStatusIndicator(status: ConnectionStatus) {
    val isConnecting = status == ConnectionStatus.Connecting || status == ConnectionStatus.Reconnecting

    val (color, icon, text) = when (status) {
        is ConnectionStatus.Connected -> Triple(
            Color(0xFF00C853),
            Icons.Default.CloudDone,
            "已连接"
        )
        is ConnectionStatus.Connecting -> Triple(
            Color(0xFFFFAB00),
            Icons.Default.HourglassTop,
            "正在连接..."
        )
        is ConnectionStatus.Reconnecting -> Triple(
            Color(0xFFFFAB00),
            Icons.Default.Refresh,
            "正在重连..."
        )
        is ConnectionStatus.Error -> Triple(
            Color(0xFFFF5252),
            Icons.Default.Error,
            "连接失败: ${status.message}"
        )
        else -> Triple(
            Color(0xFF9E9E9E),
            Icons.Default.CloudOff,
            "未连接"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 主内容（已登录后显示）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: PushViewModel) {
    val currentSession by viewModel.currentSession.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val uiState by viewModel.messages.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("消息", "连接", "订阅").forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) Badge { Text("$unreadCount") }
                                    }
                                ) { Icon(Icons.Default.Inbox, null) }
                                1 -> Icon(Icons.Default.Cable, null)
                                else -> Icon(Icons.Default.Subscriptions, null)
                            }
                        },
                        label = { Text(title) },
                        selected = currentTab == index,
                        onClick = { currentTab = index }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                0 -> MessageListScreen(
                    messages = uiState,
                    unreadCount = unreadCount,
                    filter = currentFilter,
                    connectionStatus = connectionStatus,
                    onFilterChange = viewModel::setFilter,
                    onMessageClick = viewModel::selectMessage,
                    onMarkRead = viewModel::markAsRead,
                    onMarkAllRead = viewModel::markAllAsRead,
                    onToggleStar = viewModel::toggleStar,
                    onDelete = viewModel::deleteMessage,
                    onClearRead = viewModel::clearRead,
                    onClearAll = viewModel::clearAll
                )
                1 -> ConnectionScreen(
                    connectionStatus = connectionStatus,
                    currentSession = currentSession,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onLogout = { viewModel.logout() }
                )
                2 -> SubscriptionScreen(
                    subscriptions = viewModel.subscriptions,
                    currentSession = currentSession,
                    onSubscribe = viewModel::subscribe,
                    onUnsubscribe = viewModel::unsubscribe
                )
            }
        }
    }
}

@Composable
fun ConnectionScreen(
    connectionStatus: ConnectionStatus,
    currentSession: com.push.core.model.UserSession?,
    onConnect: (BrokerConfig) -> Unit,
    onDisconnect: () -> Unit,
    onLogout: () -> Unit
) {
    var host by remember { mutableStateOf("10.0.2.2") }
    var port by remember { mutableStateOf("1883") }
    var clientId by remember { mutableStateOf("android-client") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("连接管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // 当前会话卡片
        currentSession?.let { session ->
            SessionCard(
                session = session,
                onLogout = onLogout
            )
        }

        // Broker 配置
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Broker 配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it },
                        label = { Text("服务器") }, modifier = Modifier.weight(2f), singleLine = true
                    )
                    OutlinedTextField(
                        value = port, onValueChange = { port = it },
                        label = { Text("端口") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                OutlinedTextField(value = clientId, onValueChange = { clientId = it },
                    label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("用户名（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("密码（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }

        // 连接/断开按钮
        if (connectionStatus is ConnectionStatus.Connected) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.LinkOff, null)
                Spacer(Modifier.width(8.dp))
                Text("断开连接", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    onConnect(BrokerConfig(
                        host = host.ifBlank { "127.0.0.1" },
                        port = port.toIntOrNull() ?: 1883,
                        clientId = clientId.ifBlank { "demo-${System.currentTimeMillis()}" },
                        username = username.takeIf { it.isNotBlank() },
                        password = password.takeIf { it.isNotBlank() }
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = connectionStatus != ConnectionStatus.Connecting,
                shape = MaterialTheme.shapes.medium
            ) {
                if (connectionStatus == ConnectionStatus.Connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Cable, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (connectionStatus == ConnectionStatus.Connecting) "连接中..." else "连接", fontWeight = FontWeight.Bold)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.08f))) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Default.Info, null, tint = Color(0xFF2196F3))
                Spacer(Modifier.width(8.dp))
                Text("模拟器连接宿主机用 10.0.2.2，真机用电脑局域网 IP。", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun SubscriptionScreen(
    subscriptions: StateFlow<Set<String>>,
    currentSession: com.push.core.model.UserSession?,
    onSubscribe: (String, Int) -> Unit,
    onUnsubscribe: (String) -> Unit
) {
    val subs by subscriptions.collectAsState()
    var topic by remember { mutableStateOf("") }
    var qos by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("订阅管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // 当前会话订阅主题
        currentSession?.let { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${session.userId} 的专属主题", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    session.subscribedTopics.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 手动添加订阅
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("手动订阅", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = topic, onValueChange = { topic = it },
                    label = { Text("主题") }, placeholder = { Text("例如: sensor/#") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QoS:", modifier = Modifier.align(Alignment.CenterVertically))
                    listOf(0, 1, 2).forEach { q ->
                        FilterChip(selected = qos == q, onClick = { qos = q }, label = { Text("QoS $q") })
                    }
                }
                Button(
                    onClick = { if (topic.isNotBlank()) { onSubscribe(topic, qos); topic = "" } },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加订阅")
                }
            }
        }

        // 当前所有订阅
        if (subs.isNotEmpty()) {
            Text("当前订阅 (${subs.size})", style = MaterialTheme.typography.titleMedium)
            subs.forEach { t ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        IconButton(onClick = { onUnsubscribe(t) }) {
                            Icon(Icons.Default.Close, "取消", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
package com.example.mqttpush.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.push.ui.compose.components.TopNotificationBar
import com.push.ui.compose.screen.*
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PushViewModel = viewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val uiState by viewModel.messages.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    // 未登录 → 显示登录页
    if (!isLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { /* 登录成功自动跳转 */ }
        )
        return
    }

    // 已登录 → 主界面
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
    var clientId by remember { mutableStateOf("demo-client") }
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
        if (connectionStatus == ConnectionStatus.Connected) {
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
                        Icon(Icons.Default.Label, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
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

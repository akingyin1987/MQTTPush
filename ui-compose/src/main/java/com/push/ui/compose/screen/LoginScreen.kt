package com.push.ui.compose.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.ConnectionStatus
import com.push.core.model.LoginResult
import com.push.core.model.UserSession
import com.push.core.viewmodel.PushViewModel

@Composable
fun LoginScreen(
    viewModel: PushViewModel,
    onLoginSuccess: ((UserSession) -> Unit)? = null
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isLoggedIn       by viewModel.isLoggedIn.collectAsState()
    val loginResult      by viewModel.loginResult.collectAsState()

    var userId           by remember { mutableStateOf("") }
    var groupIdsInput    by remember { mutableStateOf("") }
    var subscribeBroadcast by remember { mutableStateOf(true) }
    var errorMsg         by remember { mutableStateOf("") }
    var isLoading        by remember { mutableStateOf(false) }

    // 处理登录结果
    LaunchedEffect(loginResult) {
        when (val r = loginResult) {
            is LoginResult.Success -> {
                isLoading = false
                errorMsg  = ""
                viewModel.consumeLoginResult()
                onLoginSuccess?.invoke(r.session)
            }
            is LoginResult.Error -> {
                isLoading = false
                errorMsg  = r.message
                viewModel.consumeLoginResult()
            }
            null -> Unit
        }
    }

    // 已登录直接回调
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.currentSession.value?.let { onLoginSuccess?.invoke(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // 标题
        Column {
            Text("📡 MQTT Push", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "登录后自动订阅专属消息通道",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 连接状态
        ConnectionStatusCard(connectionStatus)

        // 表单
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("用户信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it; errorMsg = "" },
                    label = { Text("用户 ID") },
                    placeholder = { Text("例如: u123") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMsg.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = groupIdsInput,
                    onValueChange = { groupIdsInput = it },
                    label = { Text("分组 ID（可选，逗号分隔）") },
                    placeholder = { Text("例如: g001,g002") },
                    leadingIcon = { Icon(Icons.Default.Group, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("订阅全量广播", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "接收系统公告等广播消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(checked = subscribeBroadcast, onCheckedChange = { subscribeBroadcast = it })
                }
            }
        }

        // 错误提示
        AnimatedVisibility(visible = errorMsg.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        // 登录按钮
        Button(
            onClick = {
                if (userId.isBlank()) { errorMsg = "用户 ID 不能为空"; return@Button }
                if (connectionStatus != ConnectionStatus.Connected) { errorMsg = "请先连接 MQTT Broker"; return@Button }
                isLoading = true
                val groupIds = groupIdsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                viewModel.login(
                    userId = userId.trim(),
                    groupIds = groupIds,
                    subscribeBroadcast = subscribeBroadcast
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !isLoading && connectionStatus == ConnectionStatus.Connected,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Login, null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isLoading) "登录中..." else "登录", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // 订阅主题预览
        if (userId.isNotBlank()) {
            SubscriptionPreviewCard(
                userId = userId.trim(),
                groupIds = groupIdsInput.split(",").map { it.trim() }.filter { it.isNotBlank() },
                subscribeBroadcast = subscribeBroadcast
            )
        }
    }
}

// ==================== 已登录会话卡片 ====================

@Composable
fun SessionCard(
    session: UserSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Person, null, tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.userId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已登录 · ${session.subscribedTopics.size} 个主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("登出")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text("已订阅主题", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            session.subscribedTopics.forEach { topic ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(topic, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ==================== 内部组件 ====================

@Composable
private fun ConnectionStatusCard(status: ConnectionStatus) {
    val (color, icon, text) = when (status) {
        is ConnectionStatus.Connected    -> Triple(Color(0xFF00C853), Icons.Default.Wifi,     "Broker 已连接，可以登录")
        is ConnectionStatus.Connecting   -> Triple(Color(0xFFFFAB00), Icons.Default.WifiFind,  "正在连接 Broker...")
        is ConnectionStatus.Reconnecting -> Triple(Color(0xFFFFAB00), Icons.Default.WifiFind,  "正在重连...")
        is ConnectionStatus.Disconnected -> Triple(Color(0xFFFF5252), Icons.Default.WifiOff,   "未连接 Broker，请先连接")
        is ConnectionStatus.Error        -> Triple(Color(0xFFFF5252), Icons.Default.WifiOff,   "连接错误: ${status.message}")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SubscriptionPreviewCard(
    userId: String,
    groupIds: List<String>,
    subscribeBroadcast: Boolean
) {
    val topics = buildList {
        add("push/app1/user/$userId/#")
        groupIds.forEach { add("push/app1/group/$it/#") }
        if (subscribeBroadcast) add("push/app1/broadcast/#")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("登录后将订阅以下主题", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            topics.forEach { topic ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subscriptions, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(topic, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                }
            }
        }
    }
}

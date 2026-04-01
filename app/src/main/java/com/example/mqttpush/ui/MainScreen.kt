package com.example.mqttpush.ui

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
import com.push.ui.compose.screen.MessageListScreen

/**
 * 涓荤晫闈紙绀轰緥 App锛? * 灞曠ず: 娑堟伅鍒楄〃 + 杩炴帴绠＄悊 + Top 5 婊氬姩閫氱煡
 */

@Composable
fun MainScreen(viewModel: PushViewModel = viewModel()) {
    val uiState by viewModel.messages.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("娑堟伅", "杩炴帴", "璁㈤槄").forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Inbox, null)
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
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect
                )
                2 -> SubscriptionScreen(
                    subscriptions = viewModel.subscriptions,
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
    onConnect: (BrokerConfig) -> Unit,
    onDisconnect: () -> Unit
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
        Text("杩炴帴绠＄悊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it },
                        label = { Text("鏈嶅姟鍣?) }, modifier = Modifier.weight(2f), singleLine = true
                    )
                    OutlinedTextField(
                        value = port, onValueChange = { port = it },
                        label = { Text("绔彛") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                OutlinedTextField(value = clientId, onValueChange = { clientId = it },
                    label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("鐢ㄦ埛鍚嶏紙鍙€夛級") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("瀵嗙爜锛堝彲閫夛級") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (connectionStatus == ConnectionStatus.Connected) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.LinkOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text("鏂紑", fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = connectionStatus != ConnectionStatus.Connecting,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (connectionStatus == ConnectionStatus.Connecting) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Cable, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (connectionStatus == ConnectionStatus.Connecting) "杩炴帴涓?.." else "杩炴帴", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (connectionStatus is ConnectionStatus.Error) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(8.dp))
                    Text((connectionStatus as ConnectionStatus.Error).message, color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.08f))) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Default.Info, null, tint = Color(0xFF2196F3))
                Spacer(Modifier.width(8.dp))
                Text("妯℃嫙鍣ㄨ繛鎺ュ涓绘満鐢?10.0.2.2锛岀湡鏈虹敤鐢佃剳灞€鍩熺綉 IP銆?, fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun SubscriptionScreen(
    subscriptions: StateFlow<Set<String>>,
    onSubscribe: (String, Int) -> Unit,
    onUnsubscribe: (String) -> Unit
) {
    val subs by subscriptions.collectAsState()
    var topic by remember { mutableStateOf("test/#") }
    var qos by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("璁㈤槄绠＄悊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = topic, onValueChange = { topic = it },
                    label = { Text("涓婚") }, placeholder = { Text("渚嬪: sensor/#") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QoS:", modifier = Modifier.align(Alignment.CenterVertically))
                    listOf(0, 1, 2).forEach { q ->
                        FilterChip(selected = qos == q, onClick = { qos = q }, label = { Text("QoS $q") })
                    }
                }
                Button(
                    onClick = { onSubscribe(topic, qos) },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("娣诲姞璁㈤槄")
                }
            }
        }

        if (subs.isNotEmpty()) {
            Text("宸茶闃?(${subs.size})", style = MaterialTheme.typography.titleMedium)
            subs.forEach { t ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Label, null, tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { onUnsubscribe(t) }) {
                            Icon(Icons.Default.Close, "鍙栨秷", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Subscriptions, null, Modifier.size(48.dp), tint = Color.Black.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Text("鏆傛棤璁㈤槄", color = Color.Black.copy(alpha = 0.4f))
                }
            }
        }
    }
}


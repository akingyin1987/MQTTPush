package com.push.ui.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.*
import com.push.ui.compose.components.MessageListItem
import com.push.ui.compose.components.TopNotificationBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    messages: List<PushMessage>,
    unreadCount: Int,
    filter: MessageFilter,
    connectionStatus: ConnectionStatus,
    onFilterChange: (MessageFilter) -> Unit,
    onMessageClick: (PushMessage) -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onToggleStar: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearRead: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf<PushMessage?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部工具栏
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("消息中心", fontWeight = FontWeight.Bold)
                    if (unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge { Text("$unreadCount") }
                    }
                }
            },
            actions = {
                // 状态灯
                ConnectionIndicator(connectionStatus)
                // 筛选
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Default.FilterList, "筛选")
                }
                // 全部已读
                if (unreadCount > 0) {
                    IconButton(onClick = onMarkAllRead) {
                        Icon(Icons.Default.DoneAll, "全部已读")
                    }
                }
                // 更多
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("清空已读") }, onClick = { onClearRead(); showMenu = false })
                    DropdownMenuItem(
                        text = { Text("清空全部", color = MaterialTheme.colorScheme.error) },
                        onClick = { onClearAll(); showMenu = false }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // 未读 Top 5 滚动条（集成入口）
        TopNotificationBar(
            messages = messages.filter { !it.isRead }.take(5),
            onMessageClick = onMessageClick,
            onDismiss = onMarkRead,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 过滤器标签
        FilterChips(
            currentFilter = filter,
            onFilterChange = onFilterChange,
            unreadCount = unreadCount,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // 消息列表
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("暂无消息", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageListItem(
                        message = msg,
                        onClick = onMessageClick,
                        onMarkRead = onMarkRead,
                        onStar = onToggleStar,
                        onDelete = onDelete
                    )
                }
            }
        }
    }

    // 筛选底部弹窗
    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = filter,
            onApply = {
                onFilterChange(it)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // 消息详情弹窗
    showDetail?.let { msg ->
        MessageDetailDialog(
            message = msg,
            onDismiss = { showDetail = null },
            onMarkRead = { onMarkRead(msg.id) },
            onToggleStar = { onToggleStar(msg.id) },
            onDelete = { onDelete(msg.id); showDetail = null }
        )
    }
}

@Composable
fun ConnectionIndicator(status: ConnectionStatus) {
    val (color, text) = when (status) {
        is ConnectionStatus.Connected -> Color(0xFF00E676) to "已连接"
        is ConnectionStatus.Connecting -> Color(0xFFFFD740) to "连接中"
        is ConnectionStatus.Reconnecting -> Color(0xFFFFD740) to "重连中"
        is ConnectionStatus.Disconnected -> Color(0xFFFF5252) to "未连接"
        is ConnectionStatus.Error -> Color(0xFFFF5252) to "错误"
        is ConnectionStatus.SessionCleared -> Color(0xFFFFD740) to "会话已清除"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(7.dp).background(color, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(5.dp))
            Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun FilterChips(
    currentFilter: MessageFilter,
    onFilterChange: (MessageFilter) -> Unit,
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            "全部" to (if (currentFilter.status == MessageStatus.ALL) null else MessageStatus.ALL),
            "未读(${unreadCount})" to (if (currentFilter.status == MessageStatus.UNREAD) null else MessageStatus.UNREAD),
            "已读" to (if (currentFilter.status == MessageStatus.READ) null else MessageStatus.READ),
            "星标" to (if (currentFilter.status == MessageStatus.STARRED) null else MessageStatus.STARRED)
        )
        tabs.forEach { (label, status) ->
            FilterChip(
                selected = currentFilter.status == status ||
                    (status == MessageStatus.ALL && currentFilter.status == MessageStatus.ALL),
                onClick = {
                    onFilterChange(currentFilter.copy(status = status ?: MessageStatus.ALL))
                },
                label = { Text(label, fontSize = 13.sp) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentFilter: MessageFilter,
    onApply: (MessageFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(currentFilter.type) }
    var topic by remember { mutableStateOf(currentFilter.topic ?: "") }
    var keyword by remember { mutableStateOf(currentFilter.keyword ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("筛选条件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("主题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("关键词") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = if (type == t) null else t },
                        label = { Text(t.displayName, fontSize = 12.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onApply(currentFilter.copy(type = type, topic = topic.takeIf { it.isNotBlank() }, keyword = keyword.takeIf { it.isNotBlank() }))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("应用")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun MessageDetailDialog(
    message: PushMessage,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.title.ifBlank { message.topic },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleStar) {
                    Icon(
                        if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        "星标",
                        tint = if (message.isStarred) Color(0xFFFFD740) else LocalContentColor.current
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    Text("主题:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                    Text(message.topic, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                Row {
                    Text("类型:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                    Text(message.type.displayName)
                }
                Row {
                    Text("时间:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                    Text(timeFormat.format(Date(message.receivedAt)), fontSize = 13.sp)
                }
                HorizontalDivider()
                Text("内容:", fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        message.payload,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                if (!message.isRead) {
                    TextButton(onClick = { onMarkRead(); onDismiss() }) { Text("标记已读") }
                }
            }
        }
    )
}

@Composable
private fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()

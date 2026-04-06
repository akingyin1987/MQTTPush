package com.push.ui.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.ConnectionStatus
import com.push.core.model.MessageContentType
import com.push.core.model.MessageFilter
import com.push.core.model.MessageStatus
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
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
    onMarkUnread: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onToggleStar: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearRead: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }

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
                        onMarkUnread = onMarkUnread,
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
    onMarkUnread: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        message.displayTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailTag(text = message.type.displayName, color = when (message.type) {
                            MessageType.NOTIFICATION -> MaterialTheme.colorScheme.primary
                            MessageType.ALERT -> Color(0xFFFF5252)
                            MessageType.SYSTEM -> Color(0xFF7B2FF7)
                            MessageType.CUSTOM -> Color(0xFFFFD740)
                        })
                        DetailTag(text = message.contentType.displayName, color = when (message.contentType) {
                            MessageContentType.TEXT -> MaterialTheme.colorScheme.outline
                            MessageContentType.JSON -> Color(0xFF1976D2)
                            MessageContentType.LINK -> Color(0xFF2E7D32)
                        })
                        DetailTag(
                            text = if (message.isRead) "已读" else "未读",
                            color = if (message.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row {
                    Text("时间:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                    Text(timeFormat.format(Date(message.receivedAt)), fontSize = 13.sp)
                }
                Row {
                    Text("主题:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                    Text(message.topic, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                if (message.contentType == MessageContentType.LINK && message.contentUrl.isNotBlank()) {
                    Row {
                        Text("链接:", fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                        Text(
                            text = message.contentUrl,
                            color = Color(0xFF1976D2),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { uriHandler.openUri(message.contentUrl) }
                        )
                    }
                }
                HorizontalDivider()
                Text("内容", fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (message.contentType) {
                                MessageContentType.LINK -> {
                                    if (message.displayContent.isNotBlank()) {
                                        Text(
                                            message.displayContent,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (message.contentUrl.isNotBlank()) {
                                        Button(onClick = { uriHandler.openUri(message.contentUrl) }) {
                                            Text("打开链接")
                                        }
                                    }
                                }
                                MessageContentType.JSON -> {
                                    Text(
                                        message.displayContent,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                MessageContentType.TEXT -> {
                                    Text(
                                        message.displayContent,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                if (message.payload != message.displayContent) {
                    Text("原始 Payload", fontWeight = FontWeight.Medium)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        SelectionContainer {
                            Text(
                                text = message.payload,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
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
                TextButton(onClick = {
                    if (message.isRead) onMarkUnread() else onMarkRead()
                }) {
                    Text(if (message.isRead) "标记未读" else "标记已读")
                }
            }
        }
    )
}

@Composable
private fun DetailTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun rememberScrollState() = rememberScrollState()

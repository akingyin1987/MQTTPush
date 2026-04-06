package com.push.ui.compose.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.ConnectionStatus
import com.push.core.model.PushMessage
import kotlinx.coroutines.delay

/**
 * 统一状态通知栏
 *
 * 合并连接状态通知和消息滚动通知，按优先级显示：
 * 1. 连接失败/重连中 → 显示连接错误条（优先级高，隐藏消息）
 * 2. 连接正常 + 有未读消息 → 显示消息滚动条
 *
 * 使用示例：
 * ```kotlin
 * StatusNotificationBar(
 *     connectionStatus = connectionStatus,
 *     unreadMessages = latestUnread,
 *     onConnectionBarClick = { /* 跳转连接设置 */ },
 *     onMessageClick = { msg -> /* 查看消息详情 */ },
 *     onMessageDismiss = { msg -> /* 标记已读 */ }
 * )
 * ```
 *
 * @param connectionStatus 连接状态
 * @param unreadMessages 未读消息列表（连接正常时显示）
 * @param onConnectionBarClick 连接错误条点击回调
 * @param onMessageClick 消息点击回调
 * @param onMessageDismiss 消息关闭回调（标记已读）
 * @param autoScroll 是否自动滚动消息（默认 true）
 * @param scrollInterval 滚动间隔毫秒（默认 4000）
 * @param modifier Modifier
 */
@Composable
fun StatusNotificationBar(
    modifier: Modifier = Modifier,
    connectionStatus: ConnectionStatus,
    unreadMessages: List<PushMessage> = emptyList(),
    onConnectionBarClick: () -> Unit = {},
    onMessageClick: (PushMessage) -> Unit = {},
    onMessageDismiss: (PushMessage) -> Unit = {},
    autoScroll: Boolean = true,
    scrollInterval: Long = 4000L,

) {
    // 判断是否显示连接错误条（Error / Reconnecting）
    val showConnectionError = connectionStatus is ConnectionStatus.Error ||
        connectionStatus == ConnectionStatus.Reconnecting

    // 判断是否显示消息条（已连接 + 有消息 + 无连接错误）
    val showMessageBar = connectionStatus is ConnectionStatus.Connected &&
        unreadMessages.isNotEmpty() &&
        !showConnectionError

    // 动画切换
    AnimatedContent(
        targetState = when {
            showConnectionError -> BarState.ConnectionError
            showMessageBar -> BarState.Messages
            else -> BarState.None
        },
        transitionSpec = {
            when {
                targetState == BarState.None -> {
                    val exitTransition = slideOutVertically { -it } + fadeOut()
                    val enterTransition = fadeIn()
                    ContentTransform(enterTransition, exitTransition)
                }
                initialState == BarState.None -> {
                    val enterTransition = slideInVertically { -it } + fadeIn()
                    val exitTransition = fadeOut()
                    ContentTransform(enterTransition, exitTransition)
                }
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "StatusNotificationBar",
        modifier = modifier
    ) { state ->
        when (state) {
            BarState.ConnectionError -> {
                ConnectionErrorBar(
                    connectionStatus = connectionStatus,
                    onClick = onConnectionBarClick
                )
            }
            BarState.Messages -> {
                MessagesScrollBar(
                    messages = unreadMessages,
                    autoScroll = autoScroll,
                    scrollInterval = scrollInterval,
                    onClick = onMessageClick,
                    onDismiss = onMessageDismiss
                )
            }
            BarState.None -> {
                // 不显示任何内容
                Spacer(modifier = Modifier.height(0.dp))
            }
        }
    }
}

// ==================== 内部状态 ====================

private sealed class BarState {
    data object ConnectionError : BarState()
    data object Messages : BarState()
    data object None : BarState()
}

// ==================== 内部组件 ====================

/**
 * 连接错误通知条
 */
@Composable
private fun ConnectionErrorBar(
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val (color, icon, text) = when (connectionStatus) {
        is ConnectionStatus.Error -> Triple(
            colorScheme.error,
            Icons.Default.WifiOff,
            "连接失败: ${connectionStatus.message}"
        )
        is ConnectionStatus.Reconnecting -> Triple(
            colorScheme.tertiary,
            Icons.Default.WifiFind,
            "正在重连中..."
        )
        else -> Triple(colorScheme.outline, Icons.Default.WifiOff, "连接已断开")
    }

    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 重连中显示脉动动画
            if (connectionStatus == ConnectionStatus.Reconnecting) {
                var pulse by remember { mutableFloatStateOf(1f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        pulse = 0.5f
                        delay(500)
                        pulse = 1f
                        delay(500)
                    }
                }
                Icon(
                    icon, null,
                    tint = color.copy(alpha = pulse),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(10.dp))
            Text(
                text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 非重连状态显示"点击查看"
            if (connectionStatus !is ConnectionStatus.Reconnecting) {
                Text(
                    "点击查看",
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 消息滚动通知条
 */
@Composable
private fun MessagesScrollBar(
    messages: List<PushMessage>,
    autoScroll: Boolean,
    scrollInterval: Long,
    onClick: (PushMessage) -> Unit,
    onDismiss: (PushMessage) -> Unit
) {
    if (messages.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { messages.size })

    // 自动滚动
    LaunchedEffect(autoScroll, messages.size) {
        if (autoScroll && messages.size > 1) {
            while (true) {
                delay(scrollInterval)
                val nextPage = (pagerState.currentPage + 1) % messages.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Surface(
        color = colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val message = messages[page]
                MessageItem(
                    message = message,
                    onClick = { onClick(message) },
                    onDismiss = { onDismiss(message) }
                )
            }

            // 页面指示器
            if (messages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(messages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(if (isSelected) 6.dp else 4.dp),
                            content = {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSelected) colorScheme.primary
                                    else colorScheme.outline.copy(alpha = 0.5f)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单条消息项
 */
@Composable
private fun MessageItem(
    message: PushMessage,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 未读圆点
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .padding(end = 8.dp),
                content = {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = colorScheme.primary
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.title.ifBlank { message.topic },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface
                )
                Text(
                    text = message.content.ifBlank { message.payload },
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close, null,
                    tint = colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

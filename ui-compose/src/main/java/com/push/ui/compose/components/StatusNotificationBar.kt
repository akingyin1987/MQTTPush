package com.push.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.ConnectionStatus
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一状态通知栏
 *
 * 合并连接状态通知和消息滚动通知,按优先级显示:
 * 1. 连接失败/重连中 → 显示连接错误条(优先级高,隐藏消息)
 * 2. 连接正常 + 有未读消息 → 显示消息滚动条
 *
 * 使用示例:
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
    // 判断是否显示连接错误条(Error / Reconnecting)
    val showConnectionError = remember(connectionStatus) {
        connectionStatus is ConnectionStatus.Error ||
                connectionStatus == ConnectionStatus.Reconnecting
    }

    // 判断是否显示消息条(已连接 + 有消息 + 无连接错误)
    val showMessageBar = remember(connectionStatus, unreadMessages, showConnectionError) {
        connectionStatus is ConnectionStatus.Connected &&
                unreadMessages.isNotEmpty() &&
                !showConnectionError
    }

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
    
    // 使用主题颜色，支持暗黑模式
    val errorColor = colorScheme.error
    val errorContainer = colorScheme.errorContainer
    val onErrorContainer = colorScheme.onErrorContainer
    val tertiaryColor = colorScheme.tertiary
    val tertiaryContainer = colorScheme.tertiaryContainer
    
    val (gradientColors, icon, text, iconColor, textColor) = remember(
        connectionStatus,
        errorColor,
        errorContainer,
        tertiaryColor,
        tertiaryContainer
    ) {
        when (connectionStatus) {
            is ConnectionStatus.Error -> Quintuple(
                listOf(
                    errorContainer.copy(alpha = 0.3f),
                    errorContainer.copy(alpha = 0.15f)
                ),
                Icons.Default.WifiOff,
                "连接失败: ${connectionStatus.message}",
                errorColor,
                onErrorContainer
            )
            is ConnectionStatus.Reconnecting -> Quintuple(
                listOf(
                    tertiaryContainer.copy(alpha = 0.3f),
                    tertiaryContainer.copy(alpha = 0.15f)
                ),
                Icons.Default.WifiFind,
                "正在重连中...",
                tertiaryColor,
                tertiaryColor
            )
            else -> Quintuple(
                listOf(
                    errorContainer.copy(alpha = 0.25f),
                    errorContainer.copy(alpha = 0.12f)
                ),
                Icons.Default.WifiOff,
                "连接已断开",
                errorColor,
                onErrorContainer
            )
        }
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.horizontalGradient(colors = gradientColors))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 重连中显示脉动动画
                if (connectionStatus == ConnectionStatus.Reconnecting) {
                    PulsingIcon(icon = icon, color = iconColor)
                } else {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(12.dp))
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 非重连状态显示"点击查看"
                if (connectionStatus !is ConnectionStatus.Reconnecting) {
                    Text(
                        text = "点击查看",
                        color = iconColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight, null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 脉动动画图标（用于重连状态）
 */
@Composable
private fun PulsingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
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
        modifier = Modifier.size(20.dp)
    )
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
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
        color = colorScheme.primaryContainer.copy(alpha = 0.85f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                MessageItem(
                    message = messages[page],
                    onClick = { onClick(messages[page]) },
                    onDismiss = { onDismiss(messages[page]) }
                )
            }

            // 页面指示器
            if (messages.size > 1) {
                PagerIndicator(
                    pageCount = messages.size,
                    currentPage = pagerState.currentPage,
                    activeColor = colorScheme.primary,
                    inactiveColor = colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * 页面指示器
 */
@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(if (isSelected) 7.dp else 5.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) activeColor else inactiveColor
                ) {
                    Box(modifier = Modifier.fillMaxSize())
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
    val timeText = remember(message.receivedAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.receivedAt))
    }

    // 根据消息类型选择图标和颜色
    val (icon, iconColor) = remember(message.type) {
        when (message.type) {
            MessageType.NOTIFICATION -> Icons.Default.Notifications to colorScheme.primary
            MessageType.ALERT -> Icons.Default.Warning to Color(0xFFFF5252)
            MessageType.SYSTEM -> Icons.Default.Settings to Color(0xFF7B2FF7)
            MessageType.CUSTOM -> Icons.Default.Info to Color(0xFFFFB300)
        }
    }

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            MessageIcon(icon = icon, iconColor = iconColor)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.title.ifBlank { message.topic },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = message.previewContent,
                    fontSize = 12.sp,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
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
                    tint = colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 消息图标组件
 */
@Composable
private fun MessageIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(iconColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ==================== 辅助数据类 ====================

/**
 * 五元组数据类，用于减少重复计算
 */
@Stable
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)


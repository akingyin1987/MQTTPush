package com.push.ui.compose.components

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.PushMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
//  一句话集成 API - 任意业务页面顶部直接放一行即可
// ============================================================

/**
 * 🚀 一句话顶部未读消息通知条
 *
 * 用法（最简，一句话搞定）：
 * ```kotlin
 * // 方式 1：直接一行，零配置
 * PushNotificationBar(viewModel)
 *
 * // 方式 2：带回调
 * PushNotificationBar(viewModel, onMessageClick = { /* 查看详情 */ })
 *
 * // 方式 3：只显示最新3条
 * PushNotificationBar(viewModel, maxMessages = 3)
 * ```
 *
 * @param viewModel 共享的 PushViewModel（建议放在 Application 层全局共享）
 * @param maxMessages 最大显示条数（默认5）
 * @param autoScroll 是否自动轮播（默认 true）
 * @param scrollIntervalMs 轮播间隔（默认 4000ms）
 * @param onMessageClick 消息点击回调
 * @param onDismiss 消息关闭/标记已读回调（默认自动标记已读）
 * @param modifier Compose Modifier
 */
@Composable
fun PushNotificationBar(
    viewModel: com.push.core.viewmodel.PushViewModel,
    modifier: Modifier = Modifier,
    maxMessages: Int = 5,
    autoScroll: Boolean = true,
    scrollIntervalMs: Long = 4000,
    onMessageClick: ((PushMessage) -> Unit)? = null,
    onDismiss: ((Long) -> Unit)? = null
) {
    val latestUnread by viewModel.latestUnread.collectAsState()

    PushNotificationBarImpl(
        messages = latestUnread.take(maxMessages),
        modifier = modifier,
        autoScroll = autoScroll,
        scrollIntervalMs = scrollIntervalMs,
        onMessageClick = onMessageClick ?: { viewModel.selectMessage(it) },
        onDismiss = onDismiss ?: { viewModel.markAsRead(it) }
    )
}

/**
 * 🚀 带未读计数的业务页面封装
 *
 * 用法：在任意 Scaffold 顶部加一行，自动显示未读数和滚动通知
 *
 * ```kotlin
 * Scaffold(
 *     topBar = {
 *         PushBusinessTopBar(
 *             viewModel = viewModel,
 *             title = "我的业务页面"
 *         )
 *     }
 * ) { ... }
 * ```
 *
 * @param viewModel PushViewModel
 * @param title 页面标题
 * @param actions 标题栏右侧操作（可选）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushBusinessTopBar(
    viewModel: com.push.core.viewmodel.PushViewModel,
    title: String,
    modifier: Modifier = Modifier,
    onMessageCenterClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val unreadCount by viewModel.unreadCount.collectAsState()

    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        modifier = modifier,
        actions = {
            // 未读计数徽章
            if (unreadCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                    }
                ) {
                    IconButton(onClick = { onMessageCenterClick?.invoke() }) {
                        Icon(Icons.Default.Notifications, "消息中心")
                    }
                }
            } else {
                IconButton(onClick = { onMessageCenterClick?.invoke() }) {
                    Icon(Icons.Default.Notifications, "消息中心")
                }
            }
            actions()
        }
    )
}

// ============================================================
//  完整配置版本（支持所有自定义选项）
// ============================================================

/**
 * 滚动通知条配置
 */
data class NotificationBarConfig(
    val maxMessages: Int = 5,
    val autoScroll: Boolean = true,
    val scrollIntervalMs: Long = 4000,
    val backgroundColor: Color? = null,
    val contentColor: Color? = null,
    val iconTint: Color? = null,
    val showIndicator: Boolean = true,
    val showTime: Boolean = true,
    val showCloseButton: Boolean = true,
    val enableClick: Boolean = true,
    val enableSwipe: Boolean = true
)

/**
 * 完整版：支持完整配置的通知条
 *
 * 用法：
 * ```kotlin
 * PushNotificationBarWithConfig(
 *     messages = viewModel.latestUnread,
 *     config = NotificationBarConfig(
 *         maxMessages = 3,
 *         autoScroll = true,
 *         backgroundColor = Color(0xFF2196F3)
 *     ),
 *     onMessageClick = { ... },
 *     onDismiss = { ... }
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushNotificationBarWithConfig(
    messages: List<PushMessage>,
    config: NotificationBarConfig = NotificationBarConfig(),
    onMessageClick: (PushMessage) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    PushNotificationBarImpl(
        messages = messages.take(config.maxMessages),
        modifier = modifier,
        autoScroll = config.autoScroll,
        scrollIntervalMs = config.scrollIntervalMs,
        backgroundColor = config.backgroundColor,
        contentColor = config.contentColor,
        iconTint = config.iconTint,
        showIndicator = config.showIndicator,
        showTime = config.showTime,
        showCloseButton = config.showCloseButton,
        onMessageClick = onMessageClick,
        onDismiss = onDismiss
    )
}

// ============================================================
//  内部实现
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PushNotificationBarImpl(
    messages: List<PushMessage>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    scrollIntervalMs: Long = 4000,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    iconTint: Color? = null,
    showIndicator: Boolean = true,
    showTime: Boolean = true,
    showCloseButton: Boolean = true,
    onMessageClick: (PushMessage) -> Unit = {},
    onDismiss: (Long) -> Unit = {}
) {
    if (messages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { messages.size })

    // 自动轮播
    LaunchedEffect(pagerState.pageCount) {
        if (autoScroll && messages.size > 1) {
            while (true) {
                delay(scrollIntervalMs)
                val next = (pagerState.currentPage + 1) % messages.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor ?: MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // 通知图标
            Surface(
                modifier = Modifier.padding(start = 12.dp).size(28.dp),
                shape = CircleShape,
                color = (iconTint ?: MaterialTheme.colorScheme.primary)
            ) {
                Box( contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Notifications,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            if (messages.size == 1) {
                // 单条直接显示
                NotificationItemContent(
                    message = messages[0],
                    contentColor = contentColor,
                    showTime = showTime,
                    showCloseButton = showCloseButton,
                    onClick = { onMessageClick(messages[0]) },
                    onDismiss = { onDismiss(messages[0].id) }
                )
            } else {
                Box(Modifier.weight(1f)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        NotificationItemContent(
                            message = messages[page],
                            contentColor = contentColor,
                            showTime = showTime,
                            showCloseButton = showCloseButton,
                            onClick = { onMessageClick(messages[page]) },
                            onDismiss = { onDismiss(messages[page].id) }
                        )
                    }
                }

                // 分页指示器
                if (showIndicator) {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(messages.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
                                    .clip(CircleShape)
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = if (index == pagerState.currentPage)
                                        (iconTint ?: MaterialTheme.colorScheme.primary)
                                    else
                                        (contentColor ?: MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.4f),
                                    shape = CircleShape
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemContent(
    message: PushMessage,
    contentColor: Color?,
    showTime: Boolean,
    showCloseButton: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val txtColor = contentColor ?: MaterialTheme.colorScheme.onPrimaryContainer
    val subColor = txtColor.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = message.title.ifBlank { message.topic },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = txtColor
            )
            Text(
                text = message.content.ifBlank { message.payload },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = subColor
            )
        }

        if (showTime) {
            Text(
                text = timeFormat.format(Date(message.receivedAt)),
                fontSize = 10.sp,
                color = txtColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        if (showCloseButton) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "关闭",
                    tint = txtColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

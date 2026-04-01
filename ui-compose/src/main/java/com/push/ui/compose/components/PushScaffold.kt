package com.push.ui.compose.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.PushMessage
import com.push.core.viewmodel.PushViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
//  🚀 PushScaffold - 业务页面一句话集成（推荐方式）
// ============================================================

/**
 * 带推送通知的业务 Scaffold
 *
 * 用法：把普通的 Scaffold 换成 PushScaffold，一行搞定顶部通知条
 *
 * ```kotlin
 * // 最简（零配置）
 * PushScaffold(viewModel = viewModel()) {
 *     // content...
 * }
 *
 * // 带 topBar
 * PushScaffold(viewModel, topBar = { TopAppBar(title = { Text("我的页面") }) }) { padding ->
 *     // content...
 * }
 *
 * // 自定义通知条
 * PushScaffold(
 *     viewModel = viewModel,
 *     topBar = { TopAppBar(...) },
 *     showNotification = true,
 *     maxNotifications = 3,
 *     onNotificationClick = { msg -> ... }
 * ) { padding ->
 *     // content...
 * }
 * ```
 *
 * @param viewModel PushViewModel（必须）
 * @param topBar 标题栏（可选）
 * @param bottomBar 底部栏（可选）
 * @param floatingActionButton FAB（可选）
 * @param snackbarHost 底部提示（可选）
 * @param containerColor 背景色（可选）
 * @param showNotification 是否显示通知条（默认 true，自动检测有无未读）
 * @param maxNotifications 最大显示条数（默认 5）
 * @param notificationBarColor 通知条背景色（默认 primaryContainer）
 * @param notificationBarHeight 通知条高度（默认 52.dp）
 * @param autoScrollNotification 自动轮播（默认 true）
 * @param scrollIntervalMs 轮播间隔（默认 4000ms）
 * @param onNotificationClick 通知点击回调（默认调用 viewModel.selectMessage）
 * @param content 页面内容，padding 已自动扣除通知条高度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScaffold(
    viewModel: PushViewModel,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    // 通知条配置
    showNotification: Boolean = true,
    maxNotifications: Int = 5,
    notificationBarColor: Color = MaterialTheme.colorScheme.primaryContainer,
    notificationBarHeight: Dp = 52.dp,
    autoScrollNotification: Boolean = true,
    scrollIntervalMs: Long = 4000,
    onNotificationClick: ((PushMessage) -> Unit)? = null,
    onNotificationDismiss: ((Long) -> Unit)? = null,
    // 页面内容（padding 已自动扣除通知条高度）
    content: @Composable (PaddingValues) -> Unit
) {
    val latestUnread by viewModel.latestUnread.collectAsState()
    val notifications = if (showNotification) latestUnread.take(maxNotifications) else emptyList()
    val hasNotifications = notifications.isNotEmpty()

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // 通知条（显示在 topBar 下方）
            AnimatedVisibility(
                visible = hasNotifications,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                PushNotificationBarSimple(
                    messages = notifications,
                    backgroundColor = notificationBarColor,
                    height = notificationBarHeight,
                    autoScroll = autoScrollNotification,
                    scrollIntervalMs = scrollIntervalMs,
                    onMessageClick = onNotificationClick ?: { viewModel.selectMessage(it) },
                    onDismiss = onNotificationDismiss ?: { viewModel.markAsRead(it) }
                )
            }

            // 页面内容（padding 已包含通知条高度）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (hasNotifications) 0.dp else contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    )
            ) {
                content(PaddingValues(top = if (hasNotifications) 0.dp else contentPadding.calculateTopPadding()))
            }
        }
    }
}

// ============================================================
//  简洁版通知条（内部使用）
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PushNotificationBarSimple(
    messages: List<PushMessage>,
    backgroundColor: Color,
    height: Dp,
    autoScroll: Boolean,
    scrollIntervalMs: Long,
    onMessageClick: (PushMessage) -> Unit,
    onDismiss: (Long) -> Unit
) {
    if (messages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { messages.size })

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
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        color = backgroundColor,
        tonalElevation = 3.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 通知图标
            Surface(
                modifier = Modifier.padding(start = 12.dp).size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(ContentAlignment.Center) {
                    Icon(
                        Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            if (messages.size == 1) {
                SimpleNotificationItem(
                    message = messages[0],
                    onClick = { onMessageClick(messages[0]) },
                    onDismiss = { onDismiss(messages[0].id) }
                )
            } else {
                Box(Modifier.weight(1f)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        SimpleNotificationItem(
                            message = messages[page],
                            onClick = { onMessageClick(messages[page]) },
                            onDismiss = { onDismiss(messages[page].id) }
                        )
                    }
                }
                // 分页指示器
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                                shape = CircleShape
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleNotificationItem(
    message: PushMessage,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = message.content.ifBlank { message.payload },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }

        Text(
            text = timeFormat.format(Date(message.receivedAt)),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ============================================================
//  Column 版 - 非 Scaffold 场景
// ============================================================

/**
 * 在任意 Column 中嵌入通知条
 * 适合已经在 Column 里的业务页面
 *
 * ```kotlin
 * Column {
 *     // topBar
 *     TopAppBar(...)
 *
 *     // 通知条（一行代码）
 *     PushNotificationColumn(viewModel)
 *
 *     // 内容
 *     LazyColumn { ... }
 * }
 * ```
 *
 * @param viewModel PushViewModel
 * @param maxNotifications 最大显示条数
 * @param autoScroll 是否自动轮播
 * @param onClick 消息点击回调
 * @param onDismiss 关闭回调
 * @param modifier Modifier
 * @param height 通知条高度（默认 52.dp）
 * @param backgroundColor 背景色（默认 primaryContainer）
 */
@Composable
fun PushNotificationColumn(
    viewModel: PushViewModel,
    modifier: Modifier = Modifier,
    maxNotifications: Int = 5,
    autoScroll: Boolean = true,
    scrollIntervalMs: Long = 4000,
    height: Dp = 52.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: ((PushMessage) -> Unit)? = null,
    onDismiss: ((Long) -> Unit)? = null
) {
    val latestUnread by viewModel.latestUnread.collectAsState()
    val notifications = latestUnread.take(maxNotifications)

    AnimatedVisibility(
        visible = notifications.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        PushNotificationBarSimple(
            messages = notifications,
            backgroundColor = backgroundColor,
            height = height,
            autoScroll = autoScroll,
            scrollIntervalMs = scrollIntervalMs,
            onMessageClick = onClick ?: { viewModel.selectMessage(it) },
            onDismiss = onDismiss ?: { viewModel.markAsRead(it) }
        )
    }
}

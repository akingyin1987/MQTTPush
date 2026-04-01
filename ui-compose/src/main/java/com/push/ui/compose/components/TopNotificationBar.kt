package com.push.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.push.core.model.PushMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部滚动未读消息通知条
 * 支持自动轮播 + 手动滑动
 * 集成方式: 在业务页面顶部直接添加此组件即可
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopNotificationBar(
    messages: List<PushMessage>,
    onMessageClick: (PushMessage) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    scrollIntervalMs: Long = 4000
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Surface(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 轮播内容
            if (messages.size == 1) {
                // 只有一个时直接显示
                NotificationItem(
                    message = messages[0],
                    onClick = { onMessageClick(messages[0]) },
                    onDismiss = { onDismiss(messages[0].id) }
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        NotificationItem(
                            message = messages[page],
                            onClick = { onMessageClick(messages[page]) },
                            onDismiss = { onDismiss(messages[page].id) }
                        )
                    }
                }

                // 分页指示器
                Row(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(messages.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 6.dp else 5.dp)
                                .clip(CircleShape)
                                .padding(0.5.dp)
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
private fun NotificationItem(
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
        Column(modifier = Modifier.weight(1f)) {
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
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 集成示例 — 在任何业务页面顶部添加一行即可:
 *
 * ```kotlin
 * Column {
 *     TopNotificationBar(
 *         messages = viewModel.latestUnread,
 *         onMessageClick = { viewModel.selectMessage(it) },
 *         onDismiss = { viewModel.markAsRead(it) }
 *     )
 *     // 业务内容...
 * }
 * ```
 */

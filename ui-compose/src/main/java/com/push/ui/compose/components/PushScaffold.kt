package com.push.ui.compose.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.push.core.model.PushMessage
import com.push.core.viewmodel.PushViewModel

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
 * @param autoScrollNotification 自动轮播（默认 true）
 * @param scrollIntervalMs 轮播间隔（默认 4000ms）
 * @param onNotificationClick 通知点击回调（默认调用 viewModel.selectMessage）
 * @param onConnectionBarClick 连接错误条点击回调
 * @param content 页面内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScaffold(
    viewModel: PushViewModel,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit) = {},
    bottomBar: (@Composable () -> Unit) = {},
    floatingActionButton: (@Composable () -> Unit) = {},
    snackbarHost: (@Composable () -> Unit) = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    // 通知条配置
    showNotification: Boolean = true,
    maxNotifications: Int = 5,
    autoScrollNotification: Boolean = true,
    scrollIntervalMs: Long = 4000,
    onNotificationClick: ((PushMessage) -> Unit)? = null,
    onNotificationDismiss: ((Long) -> Unit)? = null,
    onConnectionBarClick: (() -> Unit)? = null,
    // 页面内容
    content: @Composable (PaddingValues) -> Unit
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val latestUnread by viewModel.latestUnread.collectAsState()
    val notifications = if (showNotification) latestUnread.take(maxNotifications) else emptyList()

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // 统一状态通知栏（连接错误 / 消息滚动）
            StatusNotificationBar(
                connectionStatus = connectionStatus,
                unreadMessages = notifications,
                onConnectionBarClick = onConnectionBarClick ?: {},
                onMessageClick = onNotificationClick ?: { viewModel.selectMessage(it) },
                onMessageDismiss = { onNotificationDismiss?.invoke(it.id) ?: viewModel.markAsRead(it.id) },
                autoScroll = autoScrollNotification,
                scrollInterval = scrollIntervalMs
            )

            // 页面内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    )
            ) {
                content(PaddingValues())
            }
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
 * @param onConnectionBarClick 连接错误条点击回调
 * @param modifier Modifier
 */
@Composable
fun PushNotificationColumn(
    viewModel: PushViewModel,
    modifier: Modifier = Modifier,
    maxNotifications: Int = 5,
    autoScroll: Boolean = true,
    scrollIntervalMs: Long = 4000,
    onClick: ((PushMessage) -> Unit)? = null,
    onDismiss: ((Long) -> Unit)? = null,
    onConnectionBarClick: (() -> Unit)? = null
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val latestUnread by viewModel.latestUnread.collectAsState()
    val notifications = latestUnread.take(maxNotifications)

    StatusNotificationBar(
        connectionStatus = connectionStatus,
        unreadMessages = notifications,
        onConnectionBarClick = onConnectionBarClick ?: {},
        onMessageClick = onClick ?: { viewModel.selectMessage(it) },
        onMessageDismiss = { onDismiss?.invoke(it.id) ?: viewModel.markAsRead(it.id) },
        autoScroll = autoScroll,
        scrollInterval = scrollIntervalMs,
        modifier = modifier
    )
}

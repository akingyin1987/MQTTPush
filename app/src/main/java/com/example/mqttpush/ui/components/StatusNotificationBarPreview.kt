package com.example.mqttpush.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mqttpush.ui.theme.MQTTPushTheme
import com.push.core.model.ConnectionStatus
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import com.push.ui.compose.components.StatusNotificationBar

/**
 * StatusNotificationBar 预览 - 浅色模式
 */
@Preview(name = "连接失败-浅色", showBackground = true)
@Composable
private fun PreviewConnectionErrorLight() {
    MQTTPushTheme(darkTheme = false) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Error("网络超时"),
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "重连中-浅色", showBackground = true)
@Composable
private fun PreviewReconnectingLight() {
    MQTTPushTheme(darkTheme = false) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Reconnecting,
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "单条消息-浅色", showBackground = true)
@Composable
private fun PreviewSingleMessageLight() {
    val sampleMessage = PushMessage(
        id = 1,
        topic = "push/app/notification",
        payload = "您有一条新的审批通知",
        title = "审批通知",
        content = "您的请假申请已被批准",
        type = MessageType.NOTIFICATION,
        receivedAt = System.currentTimeMillis() - 60000
    )

    MQTTPushTheme(darkTheme = false) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = listOf(sampleMessage)
            )
        }
    }
}

@Preview(name = "多条消息-浅色", showBackground = true)
@Composable
private fun PreviewMultipleMessagesLight() {
    val messages = listOf(
        PushMessage(
            id = 1,
            topic = "push/app/alert",
            payload = "服务器CPU使用率超过90%",
            title = "系统告警",
            content = "生产环境服务器CPU异常",
            type = MessageType.ALERT,
            receivedAt = System.currentTimeMillis() - 120000
        ),
        PushMessage(
            id = 2,
            topic = "push/app/notification",
            payload = "新版本v2.1.0已发布",
            title = "版本更新",
            content = "请及时更新到最新版本",
            type = MessageType.SYSTEM,
            receivedAt = System.currentTimeMillis() - 300000
        ),
        PushMessage(
            id = 3,
            topic = "push/custom/event",
            payload = "自定义业务事件触发",
            title = "业务通知",
            content = "订单#12345已完成支付",
            type = MessageType.CUSTOM,
            receivedAt = System.currentTimeMillis() - 600000
        )
    )

    MQTTPushTheme(darkTheme = false) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = messages,
                autoScroll = false
            )
        }
    }
}

@Preview(name = "无消息-浅色", showBackground = true)
@Composable
private fun PreviewNoMessagesLight() {
    MQTTPushTheme(darkTheme = false) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "完整场景-浅色", showBackground = true)
@Composable
private fun PreviewAllStatesLight() {
    MQTTPushTheme(darkTheme = false) {
        Column {
            Text("1. 连接失败:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Error("认证失败"),
                unreadMessages = emptyList()
            )

            Text("2. 重连中:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Reconnecting,
                unreadMessages = emptyList()
            )

            Text("3. 有消息:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = listOf(
                    PushMessage(
                        id = 1,
                        topic = "push/test",
                        payload = "测试消息内容",
                        title = "测试标题",
                        type = MessageType.NOTIFICATION,
                        receivedAt = System.currentTimeMillis()
                    )
                ),
                autoScroll = false
            )

            Text("4. 无消息:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = emptyList()
            )
        }
    }
}

// ==================== 深色模式预览 ====================

@Preview(name = "连接失败-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewConnectionErrorDark() {
    MQTTPushTheme(darkTheme = true) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Error("网络超时"),
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "重连中-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewReconnectingDark() {
    MQTTPushTheme(darkTheme = true) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Reconnecting,
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "单条消息-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSingleMessageDark() {
    val sampleMessage = PushMessage(
        id = 1,
        topic = "push/app/notification",
        payload = "您有一条新的审批通知",
        title = "审批通知",
        content = "您的请假申请已被批准",
        type = MessageType.NOTIFICATION,
        receivedAt = System.currentTimeMillis() - 60000
    )

    MQTTPushTheme(darkTheme = true) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = listOf(sampleMessage)
            )
        }
    }
}

@Preview(name = "多条消息-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewMultipleMessagesDark() {
    val messages = listOf(
        PushMessage(
            id = 1,
            topic = "push/app/alert",
            payload = "服务器CPU使用率超过90%",
            title = "系统告警",
            content = "生产环境服务器CPU异常",
            type = MessageType.ALERT,
            receivedAt = System.currentTimeMillis() - 120000
        ),
        PushMessage(
            id = 2,
            topic = "push/app/notification",
            payload = "新版本v2.1.0已发布",
            title = "版本更新",
            content = "请及时更新到最新版本",
            type = MessageType.SYSTEM,
            receivedAt = System.currentTimeMillis() - 300000
        ),
        PushMessage(
            id = 3,
            topic = "push/custom/event",
            payload = "自定义业务事件触发",
            title = "业务通知",
            content = "订单#12345已完成支付",
            type = MessageType.CUSTOM,
            receivedAt = System.currentTimeMillis() - 600000
        )
    )

    MQTTPushTheme(darkTheme = true) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = messages,
                autoScroll = false
            )
        }
    }
}

@Preview(name = "无消息-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNoMessagesDark() {
    MQTTPushTheme(darkTheme = true) {
        Column {
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = emptyList()
            )
        }
    }
}

@Preview(name = "完整场景-深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAllStatesDark() {
    MQTTPushTheme(darkTheme = true) {
        Column {
            val labelColor = if (androidx.compose.foundation.isSystemInDarkTheme()) 
                Color.White.copy(alpha = 0.6f) else Color.Gray
                
            Text("1. 连接失败:", fontSize = 12.sp, color = labelColor, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Error("认证失败"),
                unreadMessages = emptyList()
            )

            Text("2. 重连中:", fontSize = 12.sp, color = labelColor, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Reconnecting,
                unreadMessages = emptyList()
            )

            Text("3. 有消息:", fontSize = 12.sp, color = labelColor, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = listOf(
                    PushMessage(
                        id = 1,
                        topic = "push/test",
                        payload = "测试消息内容",
                        title = "测试标题",
                        type = MessageType.NOTIFICATION,
                        receivedAt = System.currentTimeMillis()
                    )
                ),
                autoScroll = false
            )

            Text("4. 无消息:", fontSize = 12.sp, color = labelColor, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
            StatusNotificationBar(
                connectionStatus = ConnectionStatus.Connected(),
                unreadMessages = emptyList()
            )
        }
    }
}

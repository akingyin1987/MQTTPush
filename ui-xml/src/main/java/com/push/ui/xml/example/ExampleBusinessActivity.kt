package com.push.ui.xml.example

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.push.core.viewmodel.PushViewModel
import com.push.ui.xml.R
import com.push.ui.xml.databinding.ActivityExampleMainBinding
import com.push.ui.xml.widget.PushNotificationBar
import kotlinx.coroutines.launch

/**
 * XML 业务页面集成示例
 *
 * 效果：topBar 下方自动显示未读消息滚动通知条
 */
class ExampleBusinessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExampleMainBinding

    // 共享 PushViewModel（建议放在 Application 层全局管理）
    private val viewModel: PushViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExampleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNotificationBar()
        observeMessages()
    }

    /**
     * 一句话绑定通知条
     */
    private fun setupNotificationBar() {
        // 🚀 一句话：自动观察未读消息，有消息显示，无消息隐藏，自动滚动
        binding.notificationBar.bind(viewModel)

        // 自定义配置（可选）
        binding.notificationBar.setAutoScroll(true)
        binding.notificationBar.setScrollInterval(4000L)

        // 自定义回调（可选，默认自动标记已读）
        binding.notificationBar.setOnNotificationListener(object : PushNotificationBar.OnNotificationListener {
            override fun onClick(message: com.push.core.model.PushMessage) {
                // 跳转到消息详情
                viewModel.selectMessage(message)
            }

            override fun onDismiss(messageId: Long) {
                // 标记已读
                viewModel.markAsRead(messageId)
            }
        })
    }

    /**
     * 观察未读消息（实时更新通知条）
     */
    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察最新未读
                viewModel.latestUnread.collect { messages ->
                    // 🚀 一句话：自动显示/隐藏 + 自动滚动
                    binding.notificationBar.setMessages(messages)
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
}

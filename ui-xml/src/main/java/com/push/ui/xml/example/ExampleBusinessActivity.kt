package com.push.ui.xml.example

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.push.core.viewmodel.PushViewModel
import com.push.ui.xml.databinding.ExampleActivityMainBinding
import com.push.ui.xml.widget.PushNotificationBar

/**
 * XML 业务页面集成示例
 *
 * 效果：topBar 下方自动显示未读消息滚动通知条
 */
class ExampleBusinessActivity : AppCompatActivity() {

    private lateinit var binding: ExampleActivityMainBinding

    // 共享 PushViewModel（建议放在 Application 层全局管理）
    private val viewModel: PushViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ExampleActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNotificationBar()
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

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }
}

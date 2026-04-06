package com.push.ui.xml

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.push.core.model.MessageFilter
import com.push.core.model.MessageStatus
import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import com.push.core.viewmodel.PushViewModel
import com.push.ui.xml.databinding.ActivityMessageCenterBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * XML 版消息中心页面
 *
 * 功能：
 * - 消息列表（未读/已读/星标/全部筛选）
 * - 点击消息 → 标记已读 + 展开详情
 * - 长按 → 弹出菜单（标记已读/未读、星标、删除）
 * - 一键全部已读、清空已读消息
 */
class MessageCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageCenterBinding
    private val viewModel: PushViewModel by lazy { PushViewModel(application) }
    private val adapter = MessageAdapter()

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFilterChips()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { viewModel.setFilter(MessageFilter(status = MessageStatus.ALL)) }
        binding.chipUnread.setOnClickListener { viewModel.setFilter(MessageFilter(status = MessageStatus.UNREAD)) }
        binding.chipRead.setOnClickListener { viewModel.setFilter(MessageFilter(status = MessageStatus.READ)) }
        binding.chipStarred.setOnClickListener { viewModel.setFilter(MessageFilter(status = MessageStatus.STARRED)) }
    }

    private fun setupClickListeners() {
        binding.btnMarkAllRead.setOnClickListener { viewModel.markAllAsRead() }
        binding.btnClearRead.setOnClickListener { viewModel.clearRead() }
        binding.btnClearAll.setOnClickListener { viewModel.clearAll() }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { list ->
                adapter.submitList(list)
                updateEmptyState(list.isEmpty())
                updateUnreadBadge(list.count { !it.isRead })
            }
        }

        lifecycleScope.launch {
            viewModel.unreadCount.collectLatest { count ->
                binding.tvUnreadCount.text = if (count > 0) "($count)" else ""
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.recyclerMessages.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun updateUnreadBadge(count: Int) {
        binding.tvUnreadCount.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.tvUnreadCount.text = count.toString()
    }

    // ==================== Adapter ====================

    inner class MessageAdapter : ListAdapter<PushMessage, MessageAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
            private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
            private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
            private val tvType: TextView = itemView.findViewById(R.id.tv_type)
            private val tvUnreadDot: View = itemView.findViewById(R.id.unread_dot)
            private val tvStar: TextView = itemView.findViewById(R.id.tv_star)

            fun bind(msg: PushMessage) {
                tvTitle.text = msg.title.ifBlank { msg.topic }
                tvContent.text = msg.content.ifBlank { msg.payload }
                tvTime.text = timeFormat.format(Date(msg.receivedAt))
                tvType.text = msg.type.displayName
                tvUnreadDot.visibility = if (msg.isRead) View.GONE else View.VISIBLE
                tvStar.visibility = if (msg.isStarred) View.VISIBLE else View.GONE

                // 类型颜色
                val color = when (msg.type) {
                    MessageType.NOTIFICATION -> 0xFF2196F3.toInt()  // 蓝
                    MessageType.ALERT -> 0xFFFF5722.toInt()        // 橙
                    MessageType.SYSTEM -> 0xFF9E9E9E.toInt()       // 灰
                    MessageType.CUSTOM -> 0xFF7B1FA2.toInt()       // 紫
                }
                tvType.setTextColor(color)

                // 点击 → 标记已读
                itemView.setOnClickListener {
                    viewModel.markAsRead(msg.id)
                }

                // 长按菜单
                itemView.setOnLongClickListener {
                    showContextMenu(msg)
                    true
                }
            }
        }
    }

    private fun showContextMenu(msg: PushMessage) {
        // 简单实现：弹出 AlertDialog
        val items = mutableListOf<String>()
        if (msg.isRead) items.add("标记未读") else items.add("标记已读")
        if (msg.isStarred) items.add("取消星标") else items.add("加星标")
        items.add("删除")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(msg.title.ifBlank { msg.topic })
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> if (msg.isRead) viewModel.markAsRead(listOf(msg.id)) else viewModel.markAsRead(msg.id)
                    1 -> viewModel.toggleStar(msg.id)
                    2 -> viewModel.deleteMessage(msg.id)
                }
            }
            .show()
    }

    object Diff : DiffUtil.ItemCallback<PushMessage>() {
        override fun areItemsTheSame(old: PushMessage, new: PushMessage) = old.id == new.id
        override fun areContentsTheSame(old: PushMessage, new: PushMessage) = old == new
    }
}

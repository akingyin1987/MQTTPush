package com.push.ui.xml

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.push.core.model.MessageType
import com.push.core.model.PushMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息列表适配器（XML 版）
 * 支持: 未读标记、星标、滑动删除
 */
class MessageAdapter(
    private val onItemClick: (PushMessage) -> Unit,
    private val onStarClick: (Long) -> Unit,
    private val onDeleteClick: (Long) -> Unit
) : ListAdapter<PushMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTopic: TextView = itemView.findViewById(R.id.tv_topic)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val tvUnread: TextView = itemView.findViewById(R.id.tv_unread_dot)
        private val btnStar: ImageButton = itemView.findViewById(R.id.btn_star)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        private val cardView: View = itemView.findViewById(R.id.card_message)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(msg: PushMessage) {
            tvTopic.text = msg.topic
            tvContent.text = msg.content.ifBlank { msg.payload }
            tvTime.text = timeFormat.format(Date(msg.receivedAt))

            // 未读指示
            tvUnread.visibility = if (!msg.isRead) View.VISIBLE else View.GONE
            cardView.alpha = if (msg.isRead) 0.75f else 1.0f

            // 类型标签
            val (typeColor, typeText) = when (msg.type) {
                MessageType.NOTIFICATION -> 0xFF2196F3.toInt() to "通知"
                MessageType.ALERT -> 0xFFF44336.toInt() to "告警"
                MessageType.SYSTEM -> 0xFF9C27B0.toInt() to "系统"
                MessageType.CUSTOM -> 0xFFFF9800.toInt() to "自定义"
            }
            tvType.text = typeText
            tvType.setTextColor(typeColor)
            tvType.background.setTint(typeColor and 0x16FFFFFF)

            // 星标
            btnStar.setImageResource(
                if (msg.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            itemView.setOnClickListener { onItemClick(msg) }
            btnStar.setOnClickListener { onStarClick(msg.id) }
            btnDelete.setOnClickListener { onDeleteClick(msg.id) }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PushMessage>() {
        override fun areItemsTheSame(oldItem: PushMessage, newItem: PushMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PushMessage, newItem: PushMessage) = oldItem == newItem
    }
}

/**
 * 顶部滚动通知适配器（XML ViewPager2）
 */
class TopNotificationAdapter(
    private val onMessageClick: (PushMessage) -> Unit,
    private val onDismissClick: (Long) -> Unit
) : ListAdapter<PushMessage, TopNotificationAdapter.TopViewHolder>(MessageAdapter.MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_notification, parent, false)
        return TopViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_notification_title)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_notification_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_notification_time)
        private val btnClose: ImageView = itemView.findViewById(R.id.btn_close_notification)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(msg: PushMessage) {
            tvTitle.text = msg.title.ifBlank { msg.topic }
            tvContent.text = msg.content.ifBlank { msg.payload }
            tvTime.text = timeFormat.format(Date(msg.receivedAt))

            itemView.setOnClickListener { onMessageClick(msg) }
            btnClose.setOnClickListener { onDismissClick(msg.id) }
        }
    }
}

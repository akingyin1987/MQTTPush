package com.push.ui.xml

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * 用于 MessageCenterActivity 的简单列表展示
 */
class SimpleMessageAdapter(
    private val onItemClick: (PushMessage) -> Unit = {}
) : ListAdapter<PushMessage, SimpleMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val tvUnreadDot: View = itemView.findViewById(R.id.unread_dot)
        private val tvStar: TextView = itemView.findViewById(R.id.tv_star)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(msg: PushMessage) {
            tvTitle.text = msg.title.ifBlank { msg.topic }
            tvContent.text = msg.content.ifBlank { msg.payload }
            tvTime.text = timeFormat.format(Date(msg.receivedAt))
            tvType.text = msg.type.displayName
            tvUnreadDot.visibility = if (!msg.isRead) View.VISIBLE else View.GONE
            tvStar.visibility = if (msg.isStarred) View.VISIBLE else View.GONE

            // 类型颜色
            val typeColor = when (msg.type) {
                MessageType.NOTIFICATION -> 0xFF2196F3.toInt()
                MessageType.ALERT -> 0xFFF44336.toInt()
                MessageType.SYSTEM -> 0xFF9C27B0.toInt()
                MessageType.CUSTOM -> 0xFFFF9800.toInt()
            }
            tvType.setTextColor(typeColor)

            itemView.setOnClickListener { onItemClick(msg) }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PushMessage>() {
        override fun areItemsTheSame(oldItem: PushMessage, newItem: PushMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PushMessage, newItem: PushMessage) = oldItem == newItem
    }
}

package com.push.ui.xml.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.push.core.model.PushMessage
import com.push.ui.xml.R
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * XML 版顶部通知条组件
 *
 * 用法（在 XML 布局中）:
 *
 * ```xml
 * <com.push.ui.xml.widget.PushNotificationBar
 *     android:id="@+id/notification_bar"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:visibility="gone" />
 * ```
 *
 * 用法（在 Activity/Fragment 中）:
 *
 * ```kotlin
 * // 观察未读消息，自动显示/隐藏 + 自动滚动
 * notificationBar.bind(viewModel)
 *
 * // 或手动传入消息列表
 * notificationBar.setMessages(messages)
 * notificationBar.show()
 *
 * // 回调
 * notificationBar.setOnNotificationListener(object : OnNotificationListener {
 *     override fun onClick(message: PushMessage) {
 *         viewModel.selectMessage(message)
 *     }
 *     override fun onDismiss(messageId: Long) {
 *         viewModel.markAsRead(messageId)
 *     }
 * })
 * ```
 *
 * @param context Context
 * @param attrs XML 属性（可选，支持 backgroundColor、textColor、iconTint、autoScroll、scrollInterval）
 */
class PushNotificationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // ==================== 视图 ====================
    private val viewPager: ViewPager2
    private val pageIndicator: LinearLayout
    private val tvEmpty: TextView

    private val adapter = NotificationAdapter()

    // ==================== 配置 ====================
    private var autoScroll: Boolean = true
    private var scrollIntervalMs: Long = 4000
    private var currentPage: Int = 0
    private var scrollTask: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ==================== 回调 ====================
    private var listener: OnNotificationListener? = null

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        orientation = VERTICAL
        visibility = GONE

        // 加载默认布局
        LayoutInflater.from(context).inflate(R.layout.push_notification_bar, this, true)

        viewPager = findViewById(R.id.pager_notifications)
        pageIndicator = findViewById(R.id.indicator_notifications)
        tvEmpty = findViewById(R.id.tv_notification_empty)

        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicator(position)
            }
        })

        adapter.setListener(object : OnItemListener {
            override fun onItemClick(message: PushMessage) {
                listener?.onClick(message)
            }
            override fun onDismissClick(message: PushMessage) {
                listener?.onDismiss(message.id)
            }
        })

        // 解析 XML 属性
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.PushNotificationBar) {
                autoScroll = getBoolean(R.styleable.PushNotificationBar_autoScroll, true)
                scrollIntervalMs = getInt(R.styleable.PushNotificationBar_scrollInterval, 4000).toLong()
            }
        }
    }

    // ==================== 公开 API ====================

    /**
     * 直接设置消息列表（自动显示/隐藏）
     */
    fun setMessages(messages: List<PushMessage>) {
        if (messages.isEmpty()) {
            hide()
            return
        }
        adapter.submitList(messages)
        updateIndicator(0)
        viewPager.setCurrentItem(0, false)
        show()
        if (autoScroll && messages.size > 1) {
            startAutoScroll(messages.size)
        }
    }

    /**
     * 一句话绑定 ViewModel（自动观察最新未读消息）
     * 
     * 使用方式：
     * ```kotlin
     * binding.notificationBar.bind(viewModel)
     * ```
     */
    fun bind(viewModel: com.push.core.viewmodel.PushViewModel) {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.latestUnread.collectLatest { messages ->
                    setMessages(messages)
                }
            }
        }

    }

    /**
     * 显示通知条（带滑入动画）
     */
    fun show() {
        if (isVisible) return
        visibility = VISIBLE
        val animator = ValueAnimator.ofInt(-height, 0)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val v = it.animatedValue as Int
            translationY = v.toFloat()
            layoutParams = layoutParams.apply { height = LayoutParams.WRAP_CONTENT }
        }
        animator.start()
    }

    /**
     * 隐藏通知条（带滑出动画）
     */
    fun hide() {
        if (isGone) return
        val animator = ValueAnimator.ofInt(0, -height)
        animator.duration = 250
        animator.interpolator = AccelerateInterpolator()
        animator.addUpdateListener {
            val v = it.animatedValue as Int
            translationY = v.toFloat()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                visibility = GONE
                translationY = 0f
            }
        })
        animator.start()
        stopAutoScroll()
    }

    /**
     * 设置监听器
     */
    fun setOnNotificationListener(listener: OnNotificationListener) {
        this.listener = listener
    }

    /**
     * 设置背景色
     */
    fun setBarBackgroundColor(color: Int) {
        setBackgroundColor(color)
    }

    /**
     * 设置是否自动滚动
     */
    fun setAutoScroll(enabled: Boolean) {
        autoScroll = enabled
    }

    /**
     * 设置滚动间隔（毫秒）
     */
    fun setScrollInterval(intervalMs: Long) {
        scrollIntervalMs = intervalMs
    }

    // ==================== 内部方法 ====================

    private fun updateIndicator(position: Int) {
        pageIndicator.removeAllViews()
        val count = adapter.itemCount
        if (count <= 1) return

        for (i in 0 until count) {
            val isActive = (i == position)
            val dot = ImageView(context).apply {
                val size = if (isActive) dp(6f) else dp(5f)
                layoutParams = LayoutParams(size, size).apply {
                    marginStart = dp(2f)
                    marginEnd = dp(2f)
                }
                
                // 使用颜色创建圆形 drawable
                val color = if (isActive) {
                    ContextCompat.getColor(context, R.color.primary)
                } else {
                    ContextCompat.getColor(context, R.color.indicator_inactive)
                }
                
                val drawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                    this.paint.color = color
                    intrinsicWidth = size
                    intrinsicHeight = size
                }
                
                setImageDrawable(drawable)
            }
            pageIndicator.addView(dot)
        }
    }

    private fun startAutoScroll(count: Int) {
        stopAutoScroll()
        scrollTask = object : Runnable {
            override fun run() {
                val next = (viewPager.currentItem + 1) % count
                viewPager.setCurrentItem(next, true)
                handler.postDelayed(this, scrollIntervalMs)
            }
        }
        handler.postDelayed(scrollTask!!, scrollIntervalMs)
    }

    private fun stopAutoScroll() {
        scrollTask?.let { handler.removeCallbacks(it) }
        scrollTask = null
    }

    private fun dp(value: Float): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoScroll()
    }

    // ==================== 内部适配器 ====================

    inner class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {
        private var list: List<PushMessage> = emptyList()
        private var itemListener: OnItemListener? = null

        fun submitList(newList: List<PushMessage>) {
            list = newList
            notifyDataSetChanged()
        }

        fun setListener(l: OnItemListener) {
            itemListener = l
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_push_notification, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tv_notification_title)
            private val tvContent: TextView = itemView.findViewById(R.id.tv_notification_content)
            private val tvTime: TextView = itemView.findViewById(R.id.tv_notification_time)
            private val btnClose: ImageView = itemView.findViewById(R.id.btn_notification_close)

            fun bind(message: PushMessage) {
                tvTitle.text = message.title.ifBlank { message.topic }
                tvContent.text = message.content.ifBlank { message.payload }
                tvTime.text = timeFormat.format(Date(message.receivedAt))

                itemView.setOnClickListener { itemListener?.onItemClick(message) }
                btnClose.setOnClickListener { itemListener?.onDismissClick(message) }
            }
        }


    }
    interface OnItemListener {
        fun onItemClick(message: PushMessage)
        fun onDismissClick(message: PushMessage)
    }

    // ==================== 回调接口 ====================

    interface OnNotificationListener {
        fun onClick(message: PushMessage)
        fun onDismiss(messageId: Long)
    }

    // ==================== 样式属性 ====================

    object Styleable {
        const val autoScroll = android.R.attr.enabled
        const val scrollInterval = android.R.attr.text
    }
}

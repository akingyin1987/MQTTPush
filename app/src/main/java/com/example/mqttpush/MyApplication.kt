package com.example.mqttpush

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.push.core.PushManager
import com.push.core.model.BrokerConfig
import com.push.core.model.PushConfig
import com.push.core.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 业务系统 Application 集成示例
 *
 * ━━━━━━━━━ 核心流程 ━━━━━━━━━
 *
 *  App 启动
 *    │
 *    ├─ ① 初始化推送 SDK（MessageRepository + PushManager）
 *    ├─ ② 从 BizSessionManager 恢复上次登录态
 *    ├─ ③ 后台静默连接 Broker（用户无感知）
 *    └─ ④ 监听业务登录状态 Flow
 *            │
 *            ├─ 业务系统登录成功 → pushManager.login(userId, groupIds, token)
 *            └─ 业务系统退出登录 → pushManager.logout()
 *
 * ━━━━━━━━━ 使用方式 ━━━━━━━━━
 *
 * 1. 在 AndroidManifest.xml 的 <application> 标签加上：
 *        android:name=".MyApplication"
 *
 * 2. 你的业务登录成功回调里调用：
 *        BizSessionManager.login(context, userId, groupIds, mqttToken)
 *
 * 3. 退出登录时调用：
 *        BizSessionManager.logout(context)
 *
 * 4. 不需要改推送 SDK 任何代码，用户对推送完全无感知。
 */
class MyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // ① 初始化 SDK（必须最先做，建立 Room 数据库实例）
        MessageRepository.getInstance(this)

        // ② 配置推送参数
        //    appId：与后端约定的应用标识，决定订阅主题前缀
        //          e.g. 主题为 push/erp_app/user/{userId}/#
        val pushManager = PushManager.getInstance(this)
        pushManager.setConfig(
            PushConfig.builder()
                .appId("erp_app")               // ← 改成你们的 appId
                .defaultSubscribeBroadcast(true) // 是否订阅广播消息
                .debugMode(true)                 // 调试阶段开启，上线后关闭
                .build()
        )

        // ③ 从业务持久化存储恢复上次登录态
        //    这样 App 重启后，BizSessionManager.userFlow 会立即 emit 上次的用户，
        //    后面监听时直接触发 login()，实现重启自动恢复会话。
        BizSessionManager.init(this)

        // ④ 后台静默连接 Broker
        //    如果上次已有缓存的 BrokerConfig（PushManager 持久化过），优先复用；
        //    否则用硬编码/配置中心的地址。
        val brokerConfig = pushManager.savedBrokerConfig.value
            ?: buildBrokerConfig() // 第一次启动，没有缓存配置，用默认值
        pushManager.connect(brokerConfig)

        // ⑤ 监听业务登录状态，自动驱动推送 login/logout
        appScope.launch {
            BizSessionManager.userFlow.collect { user ->
                if (user != null) {
                    // 业务系统已登录 → 静默登录推送
                    val result = pushManager.login(
                        userId   = user.userId,
                        groupIds = user.groupIds,
                        token    = user.mqttToken   // Broker 鉴权密码（无鉴权时留空）
                    )
                    Log.i(TAG, "Push auto-login: userId=${user.userId}, result=$result")
                } else {
                    // 业务系统退出登录 → 退出推送
                    val result = pushManager.logout()
                    Log.i(TAG, "Push auto-logout: result=$result")
                }
            }
        }
    }

    /**
     * 构建 Broker 连接配置
     *
     * ━━━━━━━━━ 实际项目替换指引 ━━━━━━━━━
     * - host / port：改成你们的 Broker 地址，可以从 BuildConfig 或配置中心拉取
     * - clientId：保持稳定（同一设备+用户每次重启都用相同的 clientId），
     *             这是离线消息补收（持久会话）的关键
     * - cleanSession = false：开启持久会话，App 离线期间的消息在重连后自动补发
     * - username/password：Broker 不鉴权时不传，鉴权时传 userId/mqttToken
     */
    private fun buildBrokerConfig(): BrokerConfig {
        val userId = BizSessionManager.getCachedUserId(this) ?: "anon"
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        return BrokerConfig(
            host         = BROKER_HOST,
            port         = BROKER_PORT,
            // 稳定 ClientId = appId + userId + deviceId，重启不变，持久会话必须
            clientId     = "erp_app-$userId-$androidId",
            // Broker 开启鉴权时传 username/password，不鉴权时注释掉这两行
            // username  = userId,
            // password  = BizSessionManager.userFlow.value?.mqttToken,
            cleanSession         = false,       // 持久会话：离线消息不丢
            sessionExpirySeconds = 7 * 24 * 3600 // 会话保留 7 天
        )
    }

    companion object {
        private const val TAG = "MyApplication"

        // ← 改成你们的 Broker 地址
        // 建议通过 BuildConfig 注入，例如：
        //   buildConfigField("String", "MQTT_HOST", "\"mqtt.yourcompany.com\"")
        private const val BROKER_HOST = "10.10.1.100"
        private const val BROKER_PORT = 1883
    }
}

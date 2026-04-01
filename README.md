# MQTT Push Module - Android 模块化推送架构

## 📁 项目结构

```
F:\AndroidMQTTPush\
├── core/                          ← 核心库（必须引入）
│   └── src\main\java\com\push\core\
│       ├── model\
│       │   ├── PushMessage.kt      ← 数据模型（Room Entity）
│       │   ├── PushDatabase.kt     ← Room 数据库 + DAO
│       │   └── MessageJson.kt      ← JSON 导出工具
│       ├── repository\            ← 消息仓库
│       ├── service\               ← HiveMQ MQTT 服务（Foreground）
│       └── viewmodel\             ← MVVM ViewModel
│
├── ui-compose/                   ← Compose UI（可选）
│   └── components\
│       ├── PushNotificationBar.kt ← 🚀 一句话集成组件
│       ├── PushScaffold.kt       ← 🚀 替代 Scaffold
│       └── TopNotificationBar.kt  ← 完整配置版
│
├── ui-xml/                      ← XML UI（可选）
│   └── widget\
│       └── PushNotificationBar.kt  ← 🚀 XML 版自定义控件
│
└── app/                         ← 示例 App
```

## 🔧 快速集成

### 1. settings.gradle.kts
```kotlin
include(":core")
include(":ui-compose")
// include(":ui-xml")
```

### 2. 业务 App build.gradle.kts
```kotlin
dependencies {
    implementation(project(":core"))          // 必须
    implementation(project(":ui-compose"))  // 或 ui-xml，二选一
}
```

### 3. 全局初始化（Application 中）
```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化数据库（内部自动单例）
        MessageRepository.getInstance(this)
    }
}
```

---

## 🚀 一句话集成

### Compose（推荐）

**方式 A：PushScaffold（直接替代 Scaffold）**
```kotlin
// 把 Scaffold 换成 PushScaffold，topBar 下方自动显示通知条
PushScaffold(viewModel = viewModel()) { padding ->
    Text("Hello")
}
```

**方式 B：Column 中嵌入**
```kotlin
Column {
    TopAppBar(title = { Text("我的页面") })
    // 一行代码
    PushNotificationColumn(viewModel)
    LazyColumn { ... }
}
```

**配置参数**
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxNotifications` | 5 | 最大显示条数 |
| `notificationBarColor` | primaryContainer | 背景色 |
| `notificationBarHeight` | 52.dp | 高度 |
| `autoScrollNotification` | true | 自动轮播 |
| `onNotificationClick` | viewModel.selectMessage() | 点击回调 |

---

### XML

**布局中加一行**
```xml
<LinearLayout android:orientation="vertical">
    <MaterialToolbar ... />

    <!-- 🚀 通知条（topBar 下方） -->
    <com.push.ui.xml.widget.PushNotificationBar
        android:id="@+id/notification_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone" />

    <RecyclerView ... />
</LinearLayout>
```

**Activity 中一句话绑定**
```kotlin
// 🚀 自动观察未读 + 显示/隐藏 + 自动滚动
binding.notificationBar.bind(viewModel)

// 自定义回调
binding.notificationBar.setOnNotificationListener(object : OnNotificationListener {
    override fun onClick(message: PushMessage) { viewModel.selectMessage(message) }
    override fun onDismiss(messageId: Long) { viewModel.markAsRead(messageId) }
})
```

---

## 🔌 连接与订阅

```kotlin
// 连接
viewModel.connect(BrokerConfig(host = "192.168.1.100", port = 1883))

// 订阅
viewModel.subscribe("sensor/#", qos = 0)

// 发布
viewModel.publish("app/notify", """{"title":"测试","content":"消息内容"}""")
```

---

## 📦 JSON 导出

```kotlin
// 单条
val json = MessageJson.toJson(message)

// 列表 + 元数据
val json = MessageJson.listToJsonWithMeta(messages, unreadCount)
```

---

## 📱 技术栈

| 层级 | 技术 |
|------|------|
| 消息传输 | HiveMQ MQTT Client 1.3.13 |
| 数据持久化 | Room 2.6.1 + KSP |
| 后台服务 | Foreground Service + lifecycleScope |
| UI - Compose | Material 3, Compose BOM 2024.06 |
| UI - XML | Material Components, ViewPager2 |
| 架构 | MVVM + Repository Pattern + StateFlow |
| 版本管理 | Gradle Version Catalog (libs.versions.toml) |

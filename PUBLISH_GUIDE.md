# MQTT Push 本地发布指南

## 📦 模块说明

本项目包含三个可独立发布的模块：

| 模块 | Artifact ID | 说明 | 依赖 |
|------|------------|------|------|
| **core** | `mqtt-push-core` | 核心功能模块<br>• MQTT 连接管理<br>• 消息持久化 (Room)<br>• 数据序列化 (ProtoBuf)<br>• 推送服务 | 无 |
| **ui-compose** | `mqtt-push-ui-compose` | Jetpack Compose UI 组件<br>• 消息列表<br>• 通知栏<br>• 状态指示器 | 依赖 core |
| **ui-xml** | `mqtt-push-ui-xml` | 传统 View 系统 UI 组件<br>• 消息中心 Activity<br>• 适配器<br>• 自定义控件 | 依赖 core |

---

## 🚀 快速开始

### 1. 发布到本地仓库

#### Windows 用户
```bash
# 发布默认版本 (1.0.0)
publish-local.bat

# 发布指定版本
publish-local.bat 1.2.0
```

#### Mac/Linux 用户
```bash
# 添加执行权限
chmod +x publish-local.sh

# 发布默认版本 (1.0.0)
./publish-local.sh

# 发布指定版本
./publish-local.sh 1.2.0
```

### 2. 手动发布（可选）

```bash
# 更新版本号（编辑 build.gradle.kts）
# extra["publishVersion"] = "1.0.0"

# 清理并构建
./gradlew clean

# 发布所有模块
./gradlew :core:publishReleasePublicationToLocalRepoRepository \
          :ui-compose:publishReleasePublicationToLocalRepoRepository \
          :ui-xml:publishReleasePublicationToLocalRepoRepository
```

---

## 📥 在其它项目中使用

### 1. 添加本地 Maven 仓库

在你的项目 `build.gradle.kts` 或 `settings.gradle.kts` 中：

```kotlin
repositories {
    // 添加本地仓库（替换为实际路径）
    maven {
        url = uri("file:///F:/AndroidMQTTPush/local-repo")
    }
    
    // 其他仓库...
    mavenCentral()
    google()
}
```

### 2. 添加依赖

#### 方式一：只使用核心功能
```kotlin
dependencies {
    implementation("com.push.mqtt:mqtt-push-core:1.0.0")
}
```

#### 方式二：使用 Compose UI
```kotlin
dependencies {
    implementation("com.push.mqtt:mqtt-push-core:1.0.0")
    implementation("com.push.mqtt:mqtt-push-ui-compose:1.0.0")
}
```

#### 方式三：使用传统 View UI
```kotlin
dependencies {
    implementation("com.push.mqtt:mqtt-push-core:1.0.0")
    implementation("com.push.mqtt:mqtt-push-ui-xml:1.0.0")
}
```

---

## 🔧 配置说明

### 修改 Group ID

编辑根目录 `build.gradle.kts`：

```kotlin
extra["publishGroupId"] = "com.yourcompany.mqtt"  // 修改为你的组ID
```

### 修改版本号

编辑根目录 `build.gradle.kts`：

```kotlin
extra["publishVersion"] = "2.0.0"  // 修改版本号
```

或使用发布脚本时指定：
```bash
./publish-local.sh 2.0.0
```

### 修改本地仓库路径

编辑根目录 `build.gradle.kts`：

```kotlin
extra["publishLocalPath"] = "${rootProject.projectDir}/my-local-repo"
```

---

## 📋 版本管理建议

### 语义化版本规范

遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)：

- **主版本号 (MAJOR)**：不兼容的 API 修改
- **次版本号 (MINOR)**：向下兼容的功能性新增
- **修订号 (PATCH)**：向下兼容的问题修正

示例：
- `1.0.0` - 首次发布
- `1.0.1` - Bug 修复
- `1.1.0` - 新增功能
- `2.0.0` - 重大变更

### 发布流程

1. **开发阶段**: `1.0.0-SNAPSHOT`
2. **测试阶段**: `1.0.0-RC1`, `1.0.0-RC2`
3. **正式发布**: `1.0.0`
4. **Bug 修复**: `1.0.1`, `1.0.2`
5. **功能更新**: `1.1.0`

---

## 🗂️ 仓库结构

发布后的本地仓库结构：

```
local-repo/
└── com/
    └── push/
        └── mqtt/
            ├── mqtt-push-core/
            │   ├── 1.0.0/
            │   │   ├── mqtt-push-core-1.0.0.aar
            │   │   ├── mqtt-push-core-1.0.0.pom
            │   │   └── mqtt-push-core-1.0.0.module
            ├── mqtt-push-ui-compose/
            │   └── 1.0.0/
            │       ├── mqtt-push-ui-compose-1.0.0.aar
            │       └── ...
            └── mqtt-push-ui-xml/
                └── 1.0.0/
                    ├── mqtt-push-ui-xml-1.0.0.aar
                    └── ...
```

---

## ❓ 常见问题

### Q1: 如何查看已发布的版本？

查看 `local-repo/com/push/mqtt/` 目录下的文件结构。

### Q2: 如何删除某个版本？

直接删除对应版本的文件夹：
```bash
rm -rf local-repo/com/push/mqtt/mqtt-push-core/1.0.0
```

### Q3: 其它开发者如何使用？

有两种方式：

**方式一：共享本地仓库**
- 将 `local-repo` 文件夹打包共享
- 接收方配置相同的本地仓库路径

**方式二：搭建私有 Maven 服务器**
- 使用 Nexus、Artifactory 等
- 配置上传到远程仓库

### Q4: 如何发布到 Maven Central？

需要额外配置：
1. 注册 Sonatype 账号
2. 配置 GPG 签名
3. 添加发布插件（如 `maven-publish` + `signing`）
4. 配置 OSSRH 仓库

详细步骤参考：[Maven Central 发布指南](https://central.sonatype.org/publish/publish-guide/)

### Q5: 依赖冲突怎么办？

确保使用相同版本：
```kotlin
// ✅ 正确 - 版本一致
implementation("com.push.mqtt:mqtt-push-core:1.0.0")
implementation("com.push.mqtt:mqtt-push-ui-compose:1.0.0")

// ❌ 错误 - 版本不一致可能导致问题
implementation("com.push.mqtt:mqtt-push-core:1.0.0")
implementation("com.push.mqtt:mqtt-push-ui-compose:1.1.0")
```

---

## 📝 更新日志

### v1.0.0 (2024-xx-xx)
- ✨ 初始版本发布
- 📦 支持三个独立模块
- 🔧 完整的本地发布配置
- 📖 详细的使用文档

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

Apache License 2.0

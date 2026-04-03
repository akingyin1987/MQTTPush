package com.push.core.model

/**
 * MQTT 主题生成器接口
 * 
 * 通过接口抽象，支持：
 * - 不同业务自定义主题格式
 * - 单元测试 mock
 * - 多租户 / 多 App 场景
 */
interface TopicGenerator {
    
    /** 用户专属订阅主题 */
    fun userSubscribeTopic(userId: String, appId: String): String
    
    /** 分组订阅主题 */
    fun groupSubscribeTopic(groupId: String, appId: String): String
    
    /** 广播订阅主题 */
    fun broadcastSubscribeTopic(appId: String): String
    
    /** 已读回执发布主题 */
    fun readReceiptTopic(userId: String, appId: String): String
    
    /** 用户消息发布主题 */
    fun userPublishTopic(userId: String, appId: String, type: String): String
    
    /** 分组消息发布主题 */
    fun groupPublishTopic(groupId: String, appId: String, type: String): String
    
    /** 广播消息发布主题 */
    fun broadcastPublishTopic(appId: String, type: String): String
}

/**
 * 默认主题生成器
 * 
 * 主题格式：
 * - 用户订阅：push/{appId}/user/{userId}/#
 * - 分组订阅：push/{appId}/group/{groupId}/#
 * - 广播订阅：push/{appId}/broadcast/#
 * - 已读回执：push/{appId}/user/{userId}/read
 * - 用户消息：push/{appId}/user/{userId}/{type}
 * - 分组消息：push/{appId}/group/{groupId}/{type}
 * - 广播消息：push/{appId}/broadcast/{type}
 */
class DefaultTopicGenerator : TopicGenerator {
    
    override fun userSubscribeTopic(userId: String, appId: String): String =
        "push/$appId/user/$userId/#"
    
    override fun groupSubscribeTopic(groupId: String, appId: String): String =
        "push/$appId/group/$groupId/#"
    
    override fun broadcastSubscribeTopic(appId: String): String =
        "push/$appId/broadcast/#"
    
    override fun readReceiptTopic(userId: String, appId: String): String =
        "push/$appId/user/$userId/read"
    
    override fun userPublishTopic(userId: String, appId: String, type: String): String =
        "push/$appId/user/$userId/$type"
    
    override fun groupPublishTopic(groupId: String, appId: String, type: String): String =
        "push/$appId/group/$groupId/$type"
    
    override fun broadcastPublishTopic(appId: String, type: String): String =
        "push/$appId/broadcast/$type"
}

/**
 * 自定义前缀主题生成器
 * 
 * 支持自定义根前缀，如 "myapp/push" 或 "v2/messages"
 */
class CustomPrefixTopicGenerator(
    private val rootPrefix: String = "push"
) : TopicGenerator {
    
    override fun userSubscribeTopic(userId: String, appId: String): String =
        "$rootPrefix/$appId/user/$userId/#"
    
    override fun groupSubscribeTopic(groupId: String, appId: String): String =
        "$rootPrefix/$appId/group/$groupId/#"
    
    override fun broadcastSubscribeTopic(appId: String): String =
        "$rootPrefix/$appId/broadcast/#"
    
    override fun readReceiptTopic(userId: String, appId: String): String =
        "$rootPrefix/$appId/user/$userId/read"
    
    override fun userPublishTopic(userId: String, appId: String, type: String): String =
        "$rootPrefix/$appId/user/$userId/$type"
    
    override fun groupPublishTopic(groupId: String, appId: String, type: String): String =
        "$rootPrefix/$appId/group/$groupId/$type"
    
    override fun broadcastPublishTopic(appId: String, type: String): String =
        "$rootPrefix/$appId/broadcast/$type"
}

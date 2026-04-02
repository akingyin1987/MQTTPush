package com.push.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.push.core.proto.UserSessionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Proto DataStore 单例
 *
 * 使用方式：
 * ```kotlin
 * // 读（响应式 Flow）
 * context.userSessionDataStore.data.collect { session -> ... }
 *
 * // 写
 * context.userSessionDataStore.updateData { current ->
 *     current.toBuilder().setUserId("u123").build()
 * }
 *
 * // 清除（登出）
 * context.userSessionDataStore.updateData { UserSessionData.getDefaultInstance() }
 * ```
 */
object UserSessionDataStore {

    private const val FILE_NAME = "user_session.pb"

    @Volatile
    private var INSTANCE: DataStore<UserSessionData>? = null

    fun getInstance(context: Context): DataStore<UserSessionData> {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: DataStoreFactory.create(
                serializer = UserSessionSerializer,
                produceFile = { context.applicationContext.dataStoreFile(FILE_NAME) },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            ).also { INSTANCE = it }
        }
    }
}

/** 便捷扩展属性，与 Preferences DataStore 风格一致 */
val Context.userSessionDataStore: DataStore<UserSessionData>
    get() = UserSessionDataStore.getInstance(this)

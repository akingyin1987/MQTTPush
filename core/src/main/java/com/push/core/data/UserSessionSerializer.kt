package com.push.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.push.core.proto.UserSessionData
import java.io.InputStream
import java.io.OutputStream

/**
 * Proto DataStore Serializer
 * 负责 UserSessionData 的序列化 / 反序列化
 */
internal object UserSessionSerializer : Serializer<UserSessionData> {

    /** 默认值 = 未登录状态（userId 为空） */
    override val defaultValue: UserSessionData
        get() = UserSessionData.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserSessionData {
        return try {
            UserSessionData.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Proto DataStore 数据损坏，无法反序列化 UserSessionData", e)
        }
    }

    override suspend fun writeTo(t: UserSessionData, output: OutputStream) {
        t.writeTo(output)
    }
}

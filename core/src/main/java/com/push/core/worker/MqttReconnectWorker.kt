package com.push.core.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.push.core.model.BrokerConfig
import com.push.core.service.PushService
import java.util.concurrent.TimeUnit

/**
 * MQTT 重连 Worker（WorkManager 兜底策略）。
 *
 * 这里的职责尽量收敛：
 * 1. 在满足网络约束时拉起 / 唤醒 PushService
 * 2. 把连接参数交回 Service 统一处理
 * 3. 不依赖进程内单例状态，不直接访问 PushService 实例
 *
 * 这样即便 Worker 运行在独立进程，或者 Service 已被系统回收，
 * 也不会因为跨进程访问 getInstance() 失效而导致逻辑错误。
 */
internal class MqttReconnectWorker(

    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = inputData.getString(KEY_HOST) ?: return Result.failure()
        val port = inputData.getInt(KEY_PORT, 1883)
        val clientId = inputData.getString(KEY_CLIENT_ID) ?: return Result.failure()
        val username = inputData.getString(KEY_USERNAME)?.takeIf { it.isNotBlank() }
        val password = inputData.getString(KEY_PASSWORD)?.takeIf { it.isNotBlank() }

        Log.i(TAG, "Starting reconnect attempt #$runAttemptCount for $host:$port")

        if (runAttemptCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Exceeded max retry count ($MAX_RETRY_COUNT), giving up.")
            return Result.failure(workDataOf(KEY_FAILURE_REASON to "exceeded_max_retries"))
        }

        return try {
            val config = BrokerConfig(
                host = host,
                port = port,
                clientId = clientId,
                username = username,
                password = password
            )

            startPushService(config)
            Log.i(TAG, "Reconnect request delivered to PushService")

            // 这里不再等待 Service 内部连接结果，也不依赖进程内实例状态判断。
            // 连接成功后的订阅恢复、积压消息冲刷都由 PushService 自己完成。
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reconnect failed with exception: ${e.message}", e)
            Result.retry()
        }
    }

    private fun startPushService(config: BrokerConfig) {
        val intent = Intent(applicationContext, PushService::class.java).apply {
            action = PushService.ACTION_CONNECT
            putExtra(PushService.EXTRA_CONFIG, config)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }

    companion object {
        private const val TAG = "MqttReconnectWorker"

        /** WorkManager 唯一任务名，防止重复入队 */
        const val UNIQUE_WORK_NAME = "mqtt_reconnect_work"

        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_FAILURE_REASON = "failure_reason"

        /** 最大重试次数（超出后停止调度） */
        private const val MAX_RETRY_COUNT = 5

        fun schedule(context: Context, config: BrokerConfig) {
            val request = OneTimeWorkRequestBuilder<MqttReconnectWorker>()
                .setInputData(buildInputData(config))
                .setConstraints(defaultConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)

            Log.i(TAG, "Scheduled reconnect worker for ${config.host}:${config.port}")
        }

        fun scheduleWithDelay(context: Context, config: BrokerConfig, delaySeconds: Long = 10) {
            val request = OneTimeWorkRequestBuilder<MqttReconnectWorker>()
                .setInputData(buildInputData(config))
                .setConstraints(defaultConstraints())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)

            Log.i(TAG, "Scheduled delayed reconnect worker (delay=${delaySeconds}s)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Cancelled reconnect worker")
        }

        private fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun buildInputData(config: BrokerConfig): Data = workDataOf(
            KEY_HOST to config.host,
            KEY_PORT to config.port,
            KEY_CLIENT_ID to config.clientId,
            KEY_USERNAME to (config.username ?: ""),
            KEY_PASSWORD to (config.password ?: "")
        )
    }
}

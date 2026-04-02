package com.push.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 系统启动接收器
 * 监听系统启动事件，自动启动推送服务
 */
internal class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // 系统启动完成，启动推送服务
            Log.d("BootReceiver", "System boot completed, starting push service")
            
            // 这里可以根据需要决定是否自动启动服务
            // 例如，检查是否有保存的连接配置
            // 如果有，自动连接
            
            // 示例：启动服务
            val serviceIntent = Intent(context, PushService::class.java)
            serviceIntent.action = PushService.ACTION_CONNECT
            // 这里需要传入保存的配置
            // serviceIntent.putExtra(PushService.EXTRA_CONFIG, savedConfig)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

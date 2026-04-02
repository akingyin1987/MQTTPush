package com.push.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// 数据模型
// ─────────────────────────────────────────────────────────────────────────────

/** 网络连接类型 */
enum class NetworkState {
    Disconnected,  // 无网络
    WiFi,          // Wi-Fi
    Cellular,      // 移动数据
    Other          // 其他（以太网、VPN 等）
}

/** 网络质量等级（根据带宽 / 网络类型综合评估） */
enum class NetworkQuality {
    GOOD,      // 良好（WiFi / LTE，下行 ≥ 5 Mbps）
    MODERATE,  // 中等（3G 或带宽受限）
    POOR       // 差（弱网 / 无信号）
}

/**
 * 网络快照：一次性捕获当前网络状态与质量，方便在逻辑中一起使用。
 */
data class NetworkSnapshot(
    val state: NetworkState,
    val quality: NetworkQuality
) {
    val isAvailable: Boolean get() = state != NetworkState.Disconnected
    val isPoor: Boolean get() = quality == NetworkQuality.POOR
}

// ─────────────────────────────────────────────────────────────────────────────
// NetworkManager
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 网络状态管理器。
 *
 * 所有监听通过 [callbackFlow] 实现，上游系统回调→Flow，下游协程收集，
 * 不再维护可变的 MutableStateFlow，状态由 Flow 算子推导。
 *
 * ### 使用示例
 * ```kotlin
 * networkManager.networkSnapshot
 *     .onEach { snap ->
 *         if (snap.isPoor) adaptToWeakNetwork() else reconnect()
 *     }
 *     .launchIn(lifecycleScope)
 * ```
 */
internal class NetworkManager private constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ── 核心 Flow：将 NetworkCallback 包装为 callbackFlow ──────────────────

    /**
     * 网络快照流。
     * - 每当网络可用性或能力改变时发射新值。
     * - [conflate] 保证只保留最新值，避免背压堆积。
     * - [distinctUntilChanged] 过滤重复事件。
     */
    val networkSnapshot: Flow<NetworkSnapshot> = callbackFlow {
        // 首次订阅时立即发射当前状态
        trySend(buildSnapshot())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(buildSnapshot())
            }

            override fun onLost(network: Network) {
                trySend(NetworkSnapshot(NetworkState.Disconnected, NetworkQuality.POOR))
            }

            override fun onUnavailable() {
                trySend(NetworkSnapshot(NetworkState.Disconnected, NetworkQuality.POOR))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(buildSnapshot(capabilities))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Flow 取消时反注册，防止内存泄漏
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    /** 仅关心网络类型的子 Flow */
    val networkState: Flow<NetworkState> = networkSnapshot
        .map { it.state }
        .distinctUntilChanged()

    /** 仅关心网络质量的子 Flow */
    val networkQuality: Flow<NetworkQuality> = networkSnapshot
        .map { it.quality }
        .distinctUntilChanged()

    // ── 同步工具方法（非阻塞快照，适合在普通函数中使用）──────────────────

    /** 同步检查网络是否可用（非响应式场景使用） */
    fun isNetworkAvailable(): Boolean = buildSnapshot().isAvailable

    /** 同步检查是否处于弱网环境（非响应式场景使用） */
    fun isPoorNetwork(): Boolean = buildSnapshot().isPoor

    /** 获取当前完整快照（非响应式场景使用） */
    fun currentSnapshot(): NetworkSnapshot = buildSnapshot()

    // ── 快照构建 ──────────────────────────────────────────────────────────

    private fun buildSnapshot(): NetworkSnapshot {
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        return buildSnapshot(capabilities)
    }

    private fun buildSnapshot(capabilities: NetworkCapabilities?): NetworkSnapshot {
        if (capabilities == null) {
            return NetworkSnapshot(NetworkState.Disconnected, NetworkQuality.POOR)
        }
        val state = resolveState(capabilities)
        val quality = resolveQuality(capabilities)
        return NetworkSnapshot(state, quality)
    }

    private fun resolveState(cap: NetworkCapabilities): NetworkState = when {
        cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> NetworkState.WiFi
        cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.Cellular
        else                                                      -> NetworkState.Other
    }

    /**
     * 网络质量评估策略：
     * 1. Wi-Fi → 直接 GOOD（带宽充足）
     * 2. 移动数据 → 根据下行带宽：≥5 Mbps = GOOD，≥1 Mbps = MODERATE，其余 = POOR
     * 3. 其他网络 → 统一 MODERATE
     */
    private fun resolveQuality(cap: NetworkCapabilities): NetworkQuality {
        return when {
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkQuality.GOOD

            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // getLinkDownstreamBandwidthKbps 返回 Kbps，API 21+
                val downstreamKbps = cap.linkDownstreamBandwidthKbps
                when {
                    downstreamKbps >= 5_000  -> NetworkQuality.GOOD      // ≥ 5 Mbps
                    downstreamKbps >= 1_000  -> NetworkQuality.MODERATE   // ≥ 1 Mbps
                    else                     -> NetworkQuality.POOR
                }
            }

            else -> NetworkQuality.MODERATE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        @Volatile
        private var INSTANCE: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

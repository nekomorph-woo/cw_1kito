package com.cw2.cw_1kito.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 网络状态工具类
 *
 * 提供网络连接状态检测、Wi-Fi 检测、网络类型判断等功能。
 *
 * ## 功能特性
 * - **Wi-Fi 检测**：判断当前是否使用 Wi-Fi 网络
 * - **移动数据检测**：判断当前是否使用移动数据
 * - **网络状态监听**：实时监听网络状态变化
 * - **网络可用性**：判断是否有可用网络连接
 *
 * ## 使用方法
 * ```kotlin
 * // 检查是否连接 Wi-Fi
 * if (NetworkUtils.isWifiConnected(context)) {
 *     // 执行需要 Wi-Fi 的操作
 * }
 *
 * // 监听网络状态变化
 * NetworkUtils.observeNetworkState(context).collect { state ->
 *     when (state) {
 *         NetworkState.Wifi -> // Wi-Fi 网络
 *         NetworkState.Mobile -> // 移动数据
 *         NetworkState.None -> // 无网络
 *     }
 * }
 * ```
 *
 * @see NetworkState
 */
object NetworkUtils {

    /**
     * 检查是否连接 Wi-Fi
     *
     * @param context 上下文
     * @return 是否连接到 Wi-Fi 网络
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            Logger.w(e, "[NetworkUtils] 检查 Wi-Fi 状态失败")
            // 兼容旧 API
            isWifiConnectedLegacy(connectivityManager)
        }
    }

    /**
     * 检查是否使用移动数据
     *
     * @param context 上下文
     * @return 是否使用移动数据网络
     */
    fun isMobileData(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (e: Exception) {
            Logger.w(e, "[NetworkUtils] 检查移动数据状态失败")
            false
        }
    }

    /**
     * 检查是否有可用网络连接
     *
     * @param context 上下文
     * @return 是否有可用网络（Wi-Fi 或移动数据）
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Logger.w(e, "[NetworkUtils] 检查网络可用性失败")
            false
        }
    }

    /**
     * 获取当前网络状态
     *
     * @param context 上下文
     * @return 当前网络状态
     */
    fun getNetworkState(context: Context): NetworkState {
        return when {
            isWifiConnected(context) -> NetworkState.Wifi
            isMobileData(context) -> NetworkState.Mobile
            isNetworkAvailable(context) -> NetworkState.Other
            else -> NetworkState.None
        }
    }

    /**
     * 监听网络状态变化
     *
     * 返回一个 Flow，持续推送网络状态变化。
     *
     * @param context 上下文
     * @return 网络状态变化的 Flow
     */
    fun observeNetworkState(context: Context): Flow<NetworkState> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (connectivityManager == null) {
            trySend(NetworkState.None)
            awaitClose()
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = getNetworkState(context)
                trySend(state)
                Logger.d("[NetworkUtils] 网络可用: $state")
            }

            override fun onLost(network: Network) {
                trySend(NetworkState.None)
                Logger.d("[NetworkUtils] 网络断开")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val state = getNetworkState(context)
                trySend(state)
                Logger.d("[NetworkUtils] 网络类型变化: $state")
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // 发送初始状态
        trySend(getNetworkState(context))

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()

    /**
     * 监听 Wi-Fi 连接状态
     *
     * 返回一个 Flow，持续推送 Wi-Fi 连接状态。
     *
     * @param context 上下文
     * @return Wi-Fi 连接状态的 Flow
     */
    fun observeWifiState(context: Context): Flow<Boolean> = observeNetworkState(context)
        .distinctUntilChanged()
        .map { it == NetworkState.Wifi }

    /**
     * 获取网络类型描述
     *
     * @param context 上下文
     * @return 网络类型描述字符串
     */
    fun getNetworkTypeName(context: Context): String {
        return when (getNetworkState(context)) {
            NetworkState.Wifi -> "Wi-Fi"
            NetworkState.Mobile -> "移动数据"
            NetworkState.Other -> "其他网络"
            NetworkState.None -> "无网络"
        }
    }

    /**
     * 检查网络是否计费（非 Wi-Fi）
     *
     * @param context 上下文
     * @return 是否使用计费网络（移动数据等）
     */
    fun isMeteredNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // 默认假设计费

        return try {
            val network = connectivityManager.activeNetwork ?: return true
            connectivityManager.isActiveNetworkMetered
        } catch (e: Exception) {
            Logger.w(e, "[NetworkUtils] 检查网络计费状态失败")
            true // 默认假设计费
        }
    }

    /**
     * 检查是否满足下载条件
     *
     * 根据配置判断当前网络是否适合下载大文件。
     *
     * @param context 上下文
     * @param requireWifi 是否要求 Wi-Fi
     * @param allowMetered 是否允许计费网络
     * @return 是否满足下载条件
     */
    fun canDownload(
        context: Context,
        requireWifi: Boolean = true,
        allowMetered: Boolean = false
    ): DownloadCondition {
        when {
            !isNetworkAvailable(context) -> return DownloadCondition.NoNetwork
            requireWifi && !isWifiConnected(context) -> return DownloadCondition.WifiRequired
            !allowMetered && isMeteredNetwork(context) -> return DownloadCondition.MeteredNetwork
            else -> return DownloadCondition.Satisfied
        }
    }

    // ========== 私有方法 ==========

    /**
     * 使用旧 API 检查 Wi-Fi（兼容 Android 5.0 以下）
     */
    @Suppress("DEPRECATION")
    private fun isWifiConnectedLegacy(connectivityManager: ConnectivityManager): Boolean {
        try {
            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            return networkInfo?.isConnected == true
        } catch (e: Exception) {
            Logger.w(e, "[NetworkUtils] 旧 API 检查 Wi-Fi 失败")
            return false
        }
    }
}

/**
 * 网络状态枚举
 */
enum class NetworkState {
    /** Wi-Fi 网络 */
    Wifi,
    /** 移动数据网络 */
    Mobile,
    /** 其他网络（如以太网） */
    Other,
    /** 无网络连接 */
    None
}

/**
 * 下载条件检查结果
 */
enum class DownloadCondition {
    /** 满足下载条件 */
    Satisfied,
    /** 无网络连接 */
    NoNetwork,
    /** 需要 Wi-Fi */
    WifiRequired,
    /** 计费网络（可能产生流量费用） */
    MeteredNetwork
}

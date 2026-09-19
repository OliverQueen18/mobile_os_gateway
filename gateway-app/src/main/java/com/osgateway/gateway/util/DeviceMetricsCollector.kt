package com.osgateway.gateway.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.osgateway.gateway.BuildConfig
import com.osgateway.shared.model.HeartbeatRequest

class DeviceMetricsCollector(private val context: Context) {

    data class LiveMetrics(
        val batteryPercent: Int,
        val temperatureCelsius: Float?,
        val networkLabel: String,
        val networkType: String,
        val internet: Boolean,
        val gpsLat: Double?,
        val gpsLng: Double?,
        val gpsStatus: String,
        val memoryUsedMb: Long,
        val memoryTotalMb: Long,
        val storageFreeMb: Long,
        val storageTotalMb: Long,
        val androidVersion: String,
        val appVersion: String,
        val imei: String?,
        val simOperator: String?,
        val signalStrength: Int?,
    )

    /** Lecture synchrone (cache / last-known uniquement). */
    fun collectLive(): LiveMetrics = buildMetrics(LocationHelper.lastKnown(context))

    /** Lecture avec refresh GPS (recommandé pour Monitoring / heartbeat). */
    suspend fun collectLiveFresh(): LiveMetrics = buildMetrics(LocationHelper.resolve(context))

    suspend fun toHeartbeat(): HeartbeatRequest {
        val m = collectLiveFresh()
        val strength = when {
            !m.internet -> 0
            m.networkType == "WIFI" -> 90
            m.networkType == "CELLULAR" -> 70
            m.networkType == "ETHERNET" -> 95
            else -> 40
        }
        return HeartbeatRequest(
            battery = m.batteryPercent.coerceIn(0, 100),
            network = strength,
            latitude = m.gpsLat,
            longitude = m.gpsLng,
            memory = m.memoryUsedMb,
            storage = m.storageFreeMb,
            temp = m.temperatureCelsius?.toDouble(),
            internet = m.internet,
            networkType = m.networkType,
            imei = m.imei,
            androidVersion = m.androidVersion,
            appVersion = m.appVersion,
            simOperator = m.simOperator,
        )
    }

    private fun buildMetrics(gps: Pair<Double, Double>?): LiveMetrics {
        val battery = readBattery()
        val mem = readMemory()
        val storage = readStorage()
        val net = readNetwork()
        return LiveMetrics(
            batteryPercent = battery.first,
            temperatureCelsius = battery.second,
            networkLabel = net.first,
            networkType = net.second,
            internet = net.third,
            gpsLat = gps?.first,
            gpsLng = gps?.second,
            gpsStatus = gpsStatusLabel(gps),
            memoryUsedMb = mem.first,
            memoryTotalMb = mem.second,
            storageFreeMb = storage.first,
            storageTotalMb = storage.second,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            imei = readDeviceId(),
            simOperator = readSimOperator(),
            signalStrength = null,
        )
    }

    private fun gpsStatusLabel(gps: Pair<Double, Double>?): String {
        if (gps != null) return "OK"
        if (!LocationHelper.hasPermission(context)) return "Permission refusée"
        if (!LocationHelper.isLocationEnabled(context)) return "GPS désactivé"
        return "En attente de fix"
    }

    private fun readBattery(): Pair<Int, Float?> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null
        return pct to temp
    }

    private fun readMemory(): Pair<Long, Long> {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val total = info.totalMem / (1024 * 1024)
        val avail = info.availMem / (1024 * 1024)
        return (total - avail) to total
    }

    private fun readStorage(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes / (1024 * 1024)
            val free = stat.availableBytes / (1024 * 1024)
            free to total
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun readNetwork(): Triple<String, String, Boolean> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps == null -> "NONE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        val label = if (internet) "ONLINE/$type" else "OFFLINE"
        return Triple(label, type, internet)
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun readDeviceId(): String? {
        val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!phoneGranted) {
            return Build.MODEL
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "AID:${android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                )}"
            } else {
                val tm = context.getSystemService(TelephonyManager::class.java)
                @Suppress("DEPRECATION")
                tm?.deviceId ?: Build.MODEL
            }
        } catch (_: Exception) {
            Build.MODEL
        }
    }

    private fun readSimOperator(): String? {
        return try {
            context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
        } catch (_: Exception) {
            null
        }
    }
}

package fr.celya.celyavox

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

/**
 * Manages CPU and Wi-Fi wake locks for VoIP scenarios.
 */
class WakeLockManager(private val context: Context) {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Volatile
    private var cpuWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        acquireCpu()
        acquireWifi()
    }

    fun release() {
        releaseCpu()
        releaseWifi()
    }

    private fun acquireCpu() {
        if (cpuWakeLock?.isHeld == true) return
        try {
            cpuWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "voip:cpu_wake_lock"
            ).apply { setReferenceCounted(false); acquire() }
            Log.d(TAG, "CPU wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire CPU wake lock", e)
        }
    }

    private fun releaseCpu() {
        try {
            cpuWakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing CPU wake lock", e)
        } finally {
            cpuWakeLock = null
            Log.d(TAG, "CPU wake lock released")
        }
    }

    private fun acquireWifi() {
        if (wifiLock?.isHeld == true) return
        try {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "voip:wifi_lock"
            ).apply { setReferenceCounted(false); acquire() }
            Log.d(TAG, "Wi-Fi lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire Wi-Fi lock", e)
        }
    }

    private fun releaseWifi() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing Wi-Fi lock", e)
        } finally {
            wifiLock = null
            Log.d(TAG, "Wi-Fi lock released")
        }
    }

    companion object {
        private const val TAG = "WakeLockManager"
    }
}

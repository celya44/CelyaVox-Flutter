package com.example.voip

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class PjsipEngine private constructor() {

    interface Callback {
        fun onEvent(type: String, message: String)
    }

    companion object {
        private const val TAG = "PjsipEngine"
        val instance: PjsipEngine by lazy { PjsipEngine() }

        init {
            try {
                System.loadLibrary("voip_engine")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load native library", t)
            }
        }

        @Volatile
        private var callback: Callback? = null

        @JvmStatic
        internal fun handleNativeEvent(type: String, message: String) {
            Log.d(TAG, "Native event: $type | $message")
            callback?.onEvent(type, message)
        }
    }

    private val initialized = AtomicBoolean(false)

    fun setCallback(cb: Callback?) {
        callback = cb
    }

    @Synchronized
    fun init(): Boolean {
        if (initialized.get()) return true
        val ok = nativeInit()
        initialized.set(ok)
        return ok
    }

    @Synchronized
    fun register(username: String, password: String, domain: String, proxy: String = ""): Boolean {
        if (!initialized.get()) init()
        return nativeRegister(username, password, domain, proxy)
    }

    @Synchronized
    fun unregister() {
        if (!initialized.get()) return
        nativeUnregister()
    }

    @Synchronized
    fun makeCall(number: String): Boolean {
        if (!initialized.get()) init()
        return nativeMakeCall(number)
    }

    @Synchronized
    fun acceptCall(callId: String): Boolean {
        if (!initialized.get()) return false
        return nativeAcceptCall(callId)
    }

    @Synchronized
    fun hangupCall(callId: String): Boolean {
        if (!initialized.get()) return false
        return nativeHangupCall(callId)
    }

    private external fun nativeInit(): Boolean
    private external fun nativeRegister(username: String, password: String, domain: String, proxy: String): Boolean
    private external fun nativeUnregister()
    private external fun nativeMakeCall(number: String): Boolean
    private external fun nativeAcceptCall(callId: String): Boolean
    private external fun nativeHangupCall(callId: String): Boolean
}

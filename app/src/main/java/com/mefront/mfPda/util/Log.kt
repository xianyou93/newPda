package com.mefront.mfPda.util

import android.util.Log as ALog
import com.mefront.mfPda.BuildConfig

/** Log 开关控制。release 包通过 BuildConfig.LOG_ENABLE 自动关闭。 */
object Log {
    fun d(tag: String, msg: String) {
        if (BuildConfig.LOG_ENABLE) ALog.d(tag, msg)
    }
    fun i(tag: String, msg: String) {
        if (BuildConfig.LOG_ENABLE) ALog.i(tag, msg)
    }
    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.LOG_ENABLE) ALog.w(tag, msg, t)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.LOG_ENABLE) ALog.e(tag, msg, t)
    }
}

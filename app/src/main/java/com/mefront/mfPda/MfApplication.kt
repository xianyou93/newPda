package com.mefront.mfPda

import android.app.Application
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.net.ApiClient
import com.mefront.mfPda.util.Log

class MfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 一次性读取设备 MAC（用于 wxopenid/wxPhone 占位）
        SpCache.init(this)
        ApiClient.init(this)
        Log.i("MfApp", "onCreate, mac=${SpCache.deviceMac()}, openid=${SpCache.openid()}")
    }

    companion object {
        @Volatile
        lateinit var instance: MfApplication
            private set
    }
}

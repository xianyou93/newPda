package com.mefront.mfPda.net

import android.content.Context
import com.mefront.mfPda.BuildConfig
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 业务 HTTP 客户端单例。 */
object ApiClient {

    /** 与原 wx https.js 一致。生产环境前缀。 */
    const val BASE_URL = "https://xxpt.mefront.com/"

    /** 与原 wx https.js 一致。token 校验接口。 */
    const val AUTO_URL = "loginapi/getToken"

    private lateinit var client: OkHttpClient

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        Log.i("ApiClient", "init ok, base=$BASE_URL, logEnable=${BuildConfig.LOG_ENABLE}")
    }

    fun http(): OkHttpClient = client

    fun mac(): String = SpCache.deviceMac()
}

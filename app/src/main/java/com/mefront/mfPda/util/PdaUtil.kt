package com.mefront.mfPda.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.text.TextUtils
import java.net.NetworkInterface
import java.util.Locale

/** 设备 MAC 地址工具。多策略取 MAC，最终 fallback 到 ANDROID_ID。 */
object PdaUtil {

    /** 获取稳定的"pda_" + 唯一标识 串。空时用 "pda_unknown"。 */
    fun openid(context: Context): String {
        val mac = deviceMac(context)
        val id = if (TextUtils.isEmpty(mac) || "02:00:00:00:00:00" == mac) {
            androidId(context)
        } else {
            mac
        }
        return if (TextUtils.isEmpty(id)) "pda_unknown" else "pda_" + id.replace(":", "").lowercase(Locale.ROOT)
    }

    fun phone(context: Context): String = openid(context)

    /** 多策略取 MAC。Android 7+ 拿不到真实 MAC（返回 02:00:00:00:00:00），fallback 到 ANDROID_ID。 */
    fun deviceMac(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.connectionInfo?.macAddress ?: ""
            } else {
                // 7.0+ 通过 NetworkInterface 枚举
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
                for (intf in interfaces) {
                    val mac = intf.hardwareAddress ?: continue
                    if (mac.isEmpty()) continue
                    val sb = StringBuilder()
                    for (b in mac) sb.append(String.format("%02X:", b))
                    if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                    val s = sb.toString()
                    if ("02:00:00:00:00:00" != s) return s
                }
                ""
            }
        } catch (e: Throwable) {
            Log.w("PdaUtil", "getMac fail: ${e.message}")
            ""
        }
    }

    fun androidId(context: Context): String {
        return try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Throwable) {
            ""
        }
    }
}

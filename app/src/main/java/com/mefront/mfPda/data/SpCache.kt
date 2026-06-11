package com.mefront.mfPda.data

import android.content.Context
import android.content.SharedPreferences
import com.mefront.mfPda.MfApplication
import com.mefront.mfPda.util.PdaUtil
import org.json.JSONObject

/**
 * SharedPreferences 缓存层。
 * 完整保留原 wx 所有缓存 key（CuserInfo/orderData/custom/custom2/remark），
 * 额外加了 device_mac / openid / wxPhone 的设备级缓存。
 *
 * 注意：原 wx 是把整个对象 JSON 化存进 storage。PDA 端同一个 key 存一个 JSON 串，
 * 取值时 JSON.parse。
 */
object SpCache {

    private const val PREF_NAME = "mf_pda_sp"

    // 设备级常量缓存 key
    private const val K_DEVICE_MAC = "device_mac"
    private const val K_OPENID = "openid"
    private const val K_WXPHONE = "wxPhone"

    // 业务缓存 key（与原 wx 保持一致）
    private const val K_CUSER_INFO = "CuserInfo"
    private const val K_ORDER_DATA = "orderData"
    private const val K_CUSTOM = "custom"
    private const val K_CUSTOM2 = "custom2"
    private const val K_REMARK = "remark"

    private lateinit var sp: SharedPreferences
    private var inited = false

    fun init(app: MfApplication) {
        if (inited) return
        sp = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 一次性预热设备级 openid / wxPhone（不变）
        if (!sp.contains(K_OPENID)) {
            sp.edit().putString(K_OPENID, PdaUtil.openid(app)).apply()
        }
        if (!sp.contains(K_WXPHONE)) {
            sp.edit().putString(K_WXPHONE, PdaUtil.phone(app)).apply()
        }
        if (!sp.contains(K_DEVICE_MAC)) {
            sp.edit().putString(K_DEVICE_MAC, PdaUtil.deviceMac(app)).apply()
        }
        inited = true
    }

    fun deviceMac(): String = if (inited) sp.getString(K_DEVICE_MAC, "") ?: "" else ""
    fun openid(): String = if (inited) sp.getString(K_OPENID, "") ?: "" else ""
    fun wxPhone(): String = if (inited) sp.getString(K_WXPHONE, "") ?: "" else ""

    // ====== 业务缓存 ======

    fun getCuserInfo(): CuserInfo? =
        CuserInfo.fromJson(if (inited) sp.getString(K_CUSER_INFO, null) else null)

    fun setCuserInfo(info: CuserInfo?) {
        if (!inited) return
        if (info == null) sp.edit().remove(K_CUSER_INFO).apply()
        else sp.edit().putString(K_CUSER_INFO, info.toJson()).apply()
    }

    fun clearCuserInfo() = setCuserInfo(null)

    /** orderData 是条码数组（String[]） */
    fun getOrderData(): List<String> {
        if (!inited) return emptyList()
        val json = sp.getString(K_ORDER_DATA, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Throwable) { emptyList() }
    }

    fun setOrderData(list: List<String>?) {
        if (!inited) return
        if (list == null) sp.edit().remove(K_ORDER_DATA).apply()
        else sp.edit().putString(K_ORDER_DATA, org.json.JSONArray(list).toString()).apply()
    }

    fun clearOrderData() = setOrderData(null)

    /** 客户对象。custom 是个 Record 风格的 Map，存为 JSON object。 */
    fun getCustom(): Map<String, Any?>? = readJsonObject(K_CUSTOM)
    fun setCustom(map: Map<String, Any?>?) = writeJsonObject(K_CUSTOM, map)

    fun getCustom2(): Map<String, Any?>? = readJsonObject(K_CUSTOM2)
    fun setCustom2(map: Map<String, Any?>?) = writeJsonObject(K_CUSTOM2, map)

    fun getRemark(): String = if (inited) sp.getString(K_REMARK, "") ?: "" else ""
    fun setRemark(v: String?) {
        if (!inited) return
        if (v.isNullOrEmpty()) sp.edit().remove(K_REMARK).apply()
        else sp.edit().putString(K_REMARK, v).apply()
    }

    /** 清空"单据"级别的临时缓存，与原 wx "setStorageSync('orderData', [])" + "setStorageSync('custom', '')" 等价。 */
    fun clearOrderDraft() {
        setOrderData(null)
        setCustom(null)
        setRemark(null)
    }

    private fun readJsonObject(key: String): Map<String, Any?>? {
        if (!inited) return null
        val json = sp.getString(key, null) ?: return null
        return try {
            val o = org.json.JSONObject(json)
            val out = mutableMapOf<String, Any?>()
            o.keys().forEach { k -> out[k] = o.opt(k) }
            out
        } catch (e: Throwable) { null }
    }

    private fun writeJsonObject(key: String, map: Map<String, Any?>?) {
        if (!inited) return
        if (map == null) {
            sp.edit().remove(key).apply()
            return
        }
        val o = org.json.JSONObject()
        for ((k, v) in map) o.put(k, v ?: JSONObject.NULL)
        sp.edit().putString(key, o.toString()).apply()
    }
}

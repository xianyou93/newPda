package com.mefront.mfPda.data

import org.json.JSONObject

/**
 * 与小程序 CuserInfo 字段一一对应。
 * - wxopenid / wxPhone 在 PDA 端固定为 "pda_" + 设备 MAC（或 ANDROID_ID fallback）。
 * - wxTableId 后端可能没返回（t_wxLoginInfo 未插入），默认空串。
 * - MembershipId 是 Membership.MembershipId 业务字段，必不为 null。
 */
data class CuserInfo(
    var wxopenid: String = "",
    var loginname: String = "",
    var logincode: String = "",
    var userCode: String = "",
    var userName: String = "",
    var parentCode: String = "",
    var membershipId: String = "",
    var wxTableId: String = "",
    var wxPhone: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("wxopenid", wxopenid)
        put("loginname", loginname)
        put("logincode", logincode)
        put("UserCode", userCode)
        put("UserName", userName)
        put("ParentCode", parentCode)
        put("MembershipId", membershipId)
        put("wxTableId", wxTableId)
        put("wxPhone", wxPhone)
    }.toString()

    companion object {
        fun fromJson(json: String?): CuserInfo? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                CuserInfo(
                    wxopenid = o.optString("wxopenid", ""),
                    loginname = o.optString("loginname", ""),
                    logincode = o.optString("logincode", ""),
                    userCode = o.optString("UserCode", ""),
                    userName = o.optString("UserName", ""),
                    parentCode = o.optString("ParentCode", ""),
                    membershipId = o.optString("MembershipId", ""),
                    wxTableId = o.optString("wxTableId", ""),
                    wxPhone = o.optString("wxPhone", "")
                )
            } catch (e: Throwable) {
                null
            }
        }
    }
}

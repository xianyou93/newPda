package com.mefront.mfPda.net

import com.mefront.mfPda.data.CuserInfo
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * 对应原 wx utils/https.js。
 * 保留 req / req2 / reqLogincode / reqPost 4 个方法，分别对应：
 * - req：带 CuserInfo 全部头部的 GET
 * - req2：裸 GET
 * - reqLogincode：仅带 logincode+usercode 头部的 GET
 * - reqPost：application/x-www-form-urlencoded POST
 *
 * 回调风格 (err, res) 保持与原 wx 一致。
 */
object Net {

    /** GET，带 CuserInfo 全部头。 */
    fun req(
        urlPath: String,
        params: Map<String, Any?> = emptyMap(),
        onResult: (err: Throwable?, res: ApiResponse?) -> Unit
    ) = doRequest(urlPath, params, method = "GET", headers = cuserHeaders(SpCache.getCuserInfo()), onResult = onResult)

    /** GET，不带 token。 */
    fun req2(
        urlPath: String,
        params: Map<String, Any?> = emptyMap(),
        onResult: (err: Throwable?, res: ApiResponse?) -> Unit
    ) = doRequest(urlPath, params, method = "GET", headers = mapOf("Content-Type" to "application/json"), onResult = onResult)

    /** GET，仅带 logincode+usercode 头。 */
    fun reqLogincode(
        urlPath: String,
        params: Map<String, Any?> = emptyMap(),
        onResult: (err: Throwable?, res: ApiResponse?) -> Unit
    ) {
        val u = SpCache.getCuserInfo()
        val headers = if (u != null && u.logincode.isNotBlank()) {
            mapOf(
                "Content-Type" to "application/json",
                "logincode" to u.logincode,
                "usercode" to u.userCode
            )
        } else {
            mapOf("Content-Type" to "application/json")
        }
        doRequest(urlPath, params, method = "GET", headers = headers, onResult = onResult)
    }

    /** POST，application/x-www-form-urlencoded（不带头部）。 */
    fun reqPost(
        urlPath: String,
        params: Map<String, Any?> = emptyMap(),
        onResult: (err: Throwable?, res: ApiResponse?) -> Unit
    ) {
        val url = (ApiClient.BASE_URL + urlPath).toHttpUrlOrNull()
            ?: return onResult(IllegalArgumentException("bad url $urlPath"), null)
        val form = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v?.toString() ?: "", "UTF-8")}"
        }
        val body = okhttp3.RequestBody.create("application/x-www-form-urlencoded".toMediaTypeOrNull(), form)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        exec(req, onResult)
    }

    private fun doRequest(
        urlPath: String,
        params: Map<String, Any?>,
        method: String,
        headers: Map<String, String>,
        onResult: ((err: Throwable?, res: ApiResponse?) -> Unit)? = null
    ) {
        val base = (ApiClient.BASE_URL + urlPath).toHttpUrlOrNull()
            ?: return
        val builder = base.newBuilder()
        for ((k, v) in params) {
            if (v == null) continue
            builder.addQueryParameter(k, v.toString())
        }
        val rb = Request.Builder().url(builder.build())
        for ((k, v) in headers) rb.header(k, v)
        rb.method(method, null)
        exec(rb.build(), onResult ?: { _, _ -> })
    }

    private fun exec(req: Request, onResult: (err: Throwable?, res: ApiResponse?) -> Unit) {
        Thread {
            try {
                val resp = ApiClient.http().newCall(req).execute()
                resp.use { r ->
                    val raw = r.body?.string() ?: ""
                    Log.d("Net", "${req.method} ${req.url} -> ${r.code} $raw")
                    val json = try { JSONObject(raw) } catch (e: Throwable) { JSONObject() }
                    onResult(null, ApiResponse(
                        httpCode = r.code,
                        result = json.opt("result"),
                        msg = json.optString("msg", ""),
                        dataJson = json.optJSONArray("data"),
                        dataObject = json.optJSONObject("data"),
                        raw = json
                    ))
                }
            } catch (e: IOException) {
                Log.e("Net", "io fail ${req.url}: ${e.message}", e)
                onResult(e, null)
            } catch (e: Throwable) {
                Log.e("Net", "fail ${req.url}: ${e.message}", e)
                onResult(e, null)
            }
        }.start()
    }

    private fun cuserHeaders(u: CuserInfo?): Map<String, String> {
        val base = mutableMapOf<String, String>("Content-Type" to "application/json")
        if (u == null) return base
        if (u.logincode.isNotBlank()) base["logincode"] = u.logincode
        if (u.userCode.isNotBlank()) base["usercode"] = u.userCode
        if (u.membershipId.isNotBlank()) base["MembershipId"] = u.membershipId
        if (u.wxTableId.isNotBlank()) base["wxTableId"] = u.wxTableId
        if (u.wxPhone.isNotBlank()) base["wxPhone"] = u.wxPhone
        if (u.wxopenid.isNotBlank()) base["wxopenid"] = u.wxopenid
        return base
    }
}

/** 统一响应封装。result 字段含义与原 wx 一致：1 成功 / 0 失败 / 2 部分成功 / 3 其他。 */
data class ApiResponse(
    val httpCode: Int,
    val result: Any?,
    val msg: String,
    val dataJson: org.json.JSONArray?,
    val dataObject: org.json.JSONObject?,
    val raw: JSONObject
) {
    /** "1" 或 1 都视为 success。 */
    val ok: Boolean get() = result?.toString() == "1"
    val partial: Boolean get() = result?.toString() == "2"
    val isResultZero: Boolean get() = result?.toString() == "0"
}

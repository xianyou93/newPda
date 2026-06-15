package com.mefront.mfPda.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mefront.mfPda.R
import com.mefront.mfPda.data.CuserInfo
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityLoginBinding
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.mime.MimeActivity
import com.mefront.mfPda.util.Log
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

/**
 * 登录页。
 *
 * 关键适配：
 * - 去除 wx.getPhoneNumber 弹窗代码（hasWxPhone 永远为 true，直接走普通登录）。
 * - wxopenid = wxPhone = "pda_" + 设备 MAC（应用启动时一次取，缓存在 SpCache）。
 * - 按钮 enable 条件 = "三输入框都填完"（不需要等 openid ready，MAC 是同步的）。
 * - 登录成功后无脑保存 CuserInfo 缓存（不再判断 wxPhone 是不是 null）。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding

    private var corp: String = ""       // 客户代码（cuscorp）
    private var username: String = ""   // 用户名
    private var psword: String = ""     // 密码

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 用本地缓存预填 cuscorp / username
        SpCache.getCuserInfo()?.let { u ->
            if (u.logincode.isNotBlank()) {
                b.etCorp.setText(u.logincode)
                corp = u.logincode
            }
            if (u.userCode.isNotBlank()) {
                b.etUser.setText(u.userCode)
                username = u.userCode
            }
        }
        // 用户名默认 "admin"
        if (username.isEmpty()) {
            b.etUser.setText("admin")
            username = "admin"
        }
        b.etPwd.setText(psword)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshBtnState() }
        }
        b.etCorp.addTextChangedListener(watcher)
        b.etUser.addTextChangedListener(watcher)
        b.etPwd.addTextChangedListener(watcher)
        refreshBtnState()

        b.btnLogin.setOnClickListener { doLogin() }
    }

    private fun refreshBtnState() {
        val c = b.etCorp.text.toString().trim()
        val u = b.etUser.text.toString().trim()
        val p = b.etPwd.text.toString()
        val ready = c.isNotBlank() && u.isNotBlank() && p.isNotBlank()
        b.btnLogin.isEnabled = ready
        // 按钮文字颜色：enabled=白色, disabled=半透明白色
        b.btnLogin.setTextColor(if (ready) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())
    }

    private fun doLogin() {
        corp = b.etCorp.text.toString().trim()
        username = b.etUser.text.toString().trim()
        psword = b.etPwd.text.toString()
        if (corp.isBlank() || username.isBlank() || psword.isBlank()) {
            MfUi.toast(this, R.string.login_input_uncheck)
            return
        }
        b.btnLogin.isEnabled = false
        b.progress.visibility = View.VISIBLE

        val openid = SpCache.openid()
        val phone = SpCache.wxPhone()
        // 与原 wx login.js 一致的入参。
        // wxopenid / wxPhone 用 "pda_" + MAC 占位，code 不传 → 后端不会调微信接口。
        // 用户未登录，不带 CuserInfo 认证头（用 req2）
        Net.req2(
            "loginapi/login",
            mapOf(
                "parentcorp" to "mf",          // 原 wx 传 "mf"，后端用此字段做非空校验
                "cuscorp" to corp,
                "username" to username,
                "password" to psword,
                "wxopenid" to openid,
                "wxPhone" to phone,
                "code" to ""
            )
        ) { err, res ->
            runOnUiThread {
                b.btnLogin.isEnabled = true
                b.progress.visibility = View.GONE
                if (err != null) {
                    showError("网络异常，请重试")
                    return@runOnUiThread
                }
                if (res == null) { showError("网络异常，请重试"); return@runOnUiThread }
                if (res.ok) {
                    val arr = res.dataJson
                    if (arr == null || arr.length() == 0) {
                        showError("登录返回数据为空")
                        return@runOnUiThread
                    }
                    val o = arr.getJSONObject(0)
                    val info = CuserInfo(
                        wxopenid = o.optString("wxopenid", openid),
                        loginname = o.optString("memberName", ""),
                        logincode = o.optString("corpcode", corp),
                        userCode = o.optString("UserCode", username),
                        userName = o.optString("UserName", ""),
                        parentCode = o.optString("ParentCode", ""),
                        membershipId = o.optString("MembershipId", ""),
                        wxTableId = o.optString("wxTableId", ""),
                        wxPhone = o.optString("wxPhone", phone)
                    )
                    SpCache.setCuserInfo(info)
                    Log.i("Login", "login success: $info")
                    startActivity(Intent(this, MimeActivity::class.java))
                    finish()
                } else {
                    showError(res.msg.ifBlank { "登录失败" })
                }
            }
        }
    }

    private fun showError(msg: String) {
        // 毛玻璃 HUD 风格 Toast
        val view = LayoutInflater.from(this).inflate(R.layout.toast_hud, null)
        view.findViewById<TextView>(R.id.tv_hud_msg).text = msg
        val toast = Toast(this)
        toast.view = view
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }
}

package com.mefront.mfPda.ui.modifyPW

import android.os.Bundle
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityModifyPwBinding
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.widget.MfUi

class ModifyPwActivity : BaseActivity() {

    private lateinit var b: ActivityModifyPwBinding

    override fun title(): CharSequence = "修改密码"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityModifyPwBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnModify.setOnClickListener { doModify() }
    }

    private fun doModify() {
        val p1 = b.etPwd.text.toString()
        val p2 = b.etPwd2.text.toString()
        if (p1.isEmpty() || p2.isEmpty() || p1 != p2) {
            MfUi.toast(this, R.string.login_input_inconsistent)
            return
        }
        val u = SpCache.getCuserInfo()
        if (u == null || u.logincode.isBlank() || u.userCode.isBlank()) {
            MfUi.toast(this, R.string.login_missing_field)
            return
        }
        MfUi.showLoading(this)
        // modifyPassword 后端需要 CorpCode/LoginCode (URL 参数) + logincode/MembershipId/wxTableId/wxopenid (header)
        Net.req(
            "loginapi/modifyPassword",
            mapOf(
                "psword" to p1,
                "pswordTwo" to p2,
                "CorpCode" to u.logincode,
                "LoginCode" to u.userCode
            )
        ) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) {
                    MfUi.toast(this, R.string.network_error)
                    return@runOnUiThread
                }
                if (res.ok) {
                    MfUi.toast(this, "修改成功")
                    finish()
                } else {
                    MfUi.toast(this, R.string.network_error)
                }
            }
        }
    }
}

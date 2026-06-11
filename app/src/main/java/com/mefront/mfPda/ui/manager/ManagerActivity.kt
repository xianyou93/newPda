package com.mefront.mfPda.ui.manager

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.CuserInfo
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityManagerBinding
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.login.LoginActivity
import com.mefront.mfPda.ui.modifyPW.ModifyPwActivity
import com.mefront.mfPda.widget.MfUi

class ManagerActivity : BaseActivity() {

    private lateinit var b: ActivityManagerBinding

    private val items = listOf(
        Row("头像", "avatarUrl", isAvatar = true),
        Row("上级公司", "parentCode"),
        Row("公司名称", "loginname"),
        Row("公司代码", "logincode"),
        Row("登录账号", "userCode"),
        Row("用户名称", "userName")
    )

    override fun title(): CharSequence = "个人信息"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        val u = SpCache.getCuserInfo() ?: return
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(u, items)

        b.btnChangePwd.setOnClickListener {
            startActivity(Intent(this, ModifyPwActivity::class.java))
        }
        b.btnUnbind.setOnClickListener { unbind(u) }
    }

    private fun unbind(u: CuserInfo) {
        MfUi.confirm(this, getString(R.string.manager_unbind_confirm), onConfirm = {
            MfUi.showLoading(this, getString(R.string.manager_unbind_loading))
            Net.reqLogincode("loginapi/cleartie") { err, res ->
                runOnUiThread {
                    MfUi.hideLoading()
                    if (err != null || res == null) {
                        MfUi.toast(this, R.string.manager_unbind_fail)
                        return@runOnUiThread
                    }
                    if (res.ok) {
                        SpCache.clearCuserInfo()
                        startActivity(Intent(this, LoginActivity::class.java)
                            .putExtra("wxopenid", u.wxopenid))
                        finish()
                    } else {
                        MfUi.toast(this, R.string.manager_unbind_fail)
                    }
                }
            }
        })
    }

    data class Row(val text: String, val key: String, val isAvatar: Boolean = false)

    private class Adapter(
        val u: CuserInfo,
        val rows: List<Row>
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.tv_text)
            val tvValue: TextView = v.findViewById(R.id.tv_value)
            val ivAvatar: ImageView = v.findViewById(R.id.iv_avatar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_manager, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = rows[position]
            h.tvText.text = r.text
            if (r.isAvatar) {
                h.ivAvatar.visibility = View.VISIBLE
                h.tvValue.visibility = View.GONE
            } else {
                h.ivAvatar.visibility = View.GONE
                h.tvValue.visibility = View.VISIBLE
                val v = when (r.key) {
                    "parentCode" -> u.parentCode
                    "loginname" -> u.loginname
                    "logincode" -> u.logincode
                    "userCode" -> u.userCode
                    "userName" -> u.userName
                    else -> ""
                }
                h.tvValue.text = v
            }
        }

        override fun getItemCount() = rows.size
    }
}

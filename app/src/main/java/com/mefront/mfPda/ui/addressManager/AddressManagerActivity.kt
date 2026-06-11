package com.mefront.mfPda.ui.addressManager

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityAddressManagerBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.addressAdd.AddressAddActivity
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class AddressManagerActivity : BaseActivity() {

    private lateinit var b: ActivityAddressManagerBinding
    private val data = mutableListOf<JSONObject>()
    private var currentTab = 0     // 0 启用 / 1 禁用
    private var pageNo = 1
    private var loading = false
    private var custName: String = ""

    override fun title(): CharSequence = "客户管理"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddressManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tab.addTab(b.tab.newTab().setText(R.string.am_tab_enable).setTag(0))
        b.tab.addTab(b.tab.newTab().setText(R.string.am_tab_disable).setTag(1))
        b.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.tag as? Int ?: 0
                data.clear(); pageNo = 1; load()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        b.etKeyword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { custName = s?.toString()?.trim() ?: "" }
        })
        b.btnQuery.setOnClickListener { data.clear(); pageNo = 1; load() }
        b.btnNew.setOnClickListener {
            startActivity(Intent(this, AddressAddActivity::class.java))
        }

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(data)
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!loading && lm.findLastVisibleItemPosition() >= (b.list.adapter?.itemCount ?: 0) - 3) {
                    pageNo++; load()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        data.clear(); pageNo = 1; load()
    }

    private fun load() {
        loading = true
        Net.req("customapi/customList", mapOf("pageNo" to pageNo, "custName" to custName, "status" to currentTab)) { err, res ->
            runOnUiThread { handle(err, res) }
        }
    }

    private fun handle(err: Throwable?, res: ApiResponse?) {
        loading = false
        if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return }
        if (!res.ok) { MfUi.toast(this, R.string.network_error); return }
        val arr = res.dataJson
        if (arr != null) {
            for (i in 0 until arr.length()) {
                data.add(arr.getJSONObject(i))
            }
            b.list.adapter?.notifyDataSetChanged()
        }
        if (data.isEmpty()) {
            b.tvEmpty.root.visibility = View.VISIBLE
            b.tvEmpty.root.text = getString(R.string.empty_data)
        } else {
            b.tvEmpty.root.visibility = View.GONE
        }
    }

    private fun onItemClick(o: JSONObject) {
        // 选择客户 → 把 Map 写 custom / custom2，navigateBack（与原 wx customClick 一致）
        val map = mutableMapOf<String, Any?>()
        o.keys().forEach { k -> map[k] = o.opt(k) }
        SpCache.setCustom(map)
        SpCache.setCustom2(map)
        finish()
    }

    private fun onEdit(o: JSONObject) {
        startActivity(Intent(this, AddressAddActivity::class.java)
            .putExtra("Code", o.optString("code", "")))
    }

    private fun onDeleteOrDisable(o: JSONObject, isForbidden: Boolean) {
        MfUi.confirm(this, getString(R.string.am_delete_confirm), onConfirm = {
            Net.req("customapi/delcustom", mapOf(
                "Code" to o.optString("code", ""),
                "type" to if (isForbidden) "forbidden" else ""
            )) { err, res ->
                runOnUiThread {
                    if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                    when (res.result?.toString()) {
                        "1" -> {
                            // 移除列表中这一项
                            val code = o.optString("code", "")
                            val it = data.indexOfFirst { it.optString("code", "") == code }
                            if (it >= 0) {
                                data.removeAt(it)
                                b.list.adapter?.notifyItemRemoved(it)
                            }
                            // 清理 custom 缓存（如果是当前选中的）
                            val custom = SpCache.getCustom()
                            if (custom != null && custom["code"]?.toString() == code) {
                                SpCache.setCustom(null)
                            }
                        }
                        "0" -> MfUi.toast(this, R.string.am_tip_data_err)
                        "2" -> MfUi.toast(this, R.string.am_tip_biz_done)
                        else -> MfUi.toast(this, R.string.am_tip_delete_fail)
                    }
                }
            }
        })
    }

    private fun onStart(o: JSONObject) {
        MfUi.confirm(this, getString(R.string.am_enable_confirm), onConfirm = {
            Net.req("customapi/starteCustom", mapOf("Code" to o.optString("code", ""))) { err, res ->
                runOnUiThread {
                    if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                    when (res.result?.toString()) {
                        "1" -> { data.clear(); pageNo = 1; load() }
                        "0" -> MfUi.toast(this, R.string.am_tip_data_err)
                        "2" -> MfUi.toast(this, R.string.am_tip_no_data)
                        else -> MfUi.toast(this, R.string.am_tip_delete_fail)
                    }
                }
            }
        })
    }

    private inner class Adapter(val data: List<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v.findViewById(R.id.row_root)
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val btnDisable: Button = v.findViewById(R.id.btn_disable)
            val btnDelete: Button = v.findViewById(R.id.btn_delete)
            val btnEdit: Button = v.findViewById(R.id.btn_edit)
            val btnEnable: Button = v.findViewById(R.id.btn_enable)
        }
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_address, p, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            val idx = position + 1
            h.tvName.text = "$idx  ${o.optString("code", "")} ${o.optString("name", "")} ${o.optString("LegalPerson", "")}"
            h.root.setOnClickListener { onItemClick(o) }
            val isEnable = currentTab == 0
            h.btnDisable.visibility = if (isEnable) View.VISIBLE else View.GONE
            h.btnDelete.visibility = if (isEnable) View.VISIBLE else View.GONE
            h.btnEdit.visibility = if (isEnable) View.VISIBLE else View.GONE
            h.btnEnable.visibility = if (isEnable) View.GONE else View.VISIBLE
            h.btnDisable.setOnClickListener { onDeleteOrDisable(o, true) }
            h.btnDelete.setOnClickListener { onDeleteOrDisable(o, false) }
            h.btnEdit.setOnClickListener { onEdit(o) }
            h.btnEnable.setOnClickListener { onStart(o) }
        }
        override fun getItemCount(): Int = data.size
    }
}

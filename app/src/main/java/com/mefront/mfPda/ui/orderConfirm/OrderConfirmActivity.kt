package com.mefront.mfPda.ui.orderConfirm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityOrderConfirmBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.addressManager.AddressManagerActivity
import com.mefront.mfPda.ui.receiveList.ReceiveListActivity
import com.mefront.mfPda.ui.ordertotal.OrdertotalActivity
import com.mefront.mfPda.util.DateUtil
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

/**
 * 新增出库页。
 *
 * 关键适配：
 * - 扫码按钮 click 弹 Toast "扫码功能待对接"，不调 addclick、不连续扫码（第二步再接商米 SDK）。
 * - 手动输入框 + "确认"按钮：原 addclick 流程完全保留，包括 url 前缀去除、自动去重、错误码 0/2/3 处理。
 * - 客户选择：跳 AddressManagerActivity 选完返回（custom 缓存）。
 * - 粘贴并导入：读剪贴板 → 按空白/换行/逗号/分号分隔 → 调 orderapi/getALLCode 批量。
 * - 保存：orderapi/saveorder → 弹 "是否进行出库" 确认 → confirmorder → 跳 OrdertotalActivity。
 * - "查询收货单出库"按钮：仅在 type 不为空时显示（即从菜单主页进入时显示）。
 * - 备注字段做缓存（key="remark"），与 orderData/custom 一起在清空/从收货单进入时被清。
 * - 收货单过来的数据自动加载（billid 入参）。
 */
class OrderConfirmActivity : BaseActivity() {

    private lateinit var b: ActivityOrderConfirmBinding
    private val orderData = mutableListOf<JSONObject>()      // 详情列表（包含 Name/Spec/Spec2/code）
    private val codeList = mutableListOf<String>()            // 已加入的条码（用于去重）

    private var billtype: String = "ckd"     // ckd 出库单 / tkd 退库单
    private var date: String = DateUtil.currentTime()
    private var remark: String = ""
    private var hasType: Boolean = false    // 是否从菜单主页进入（决定是否显示"查询收货单出库"）

    override fun title(): CharSequence = "新增出库单"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(b.root)

        date = DateUtil.currentTime()
        b.tvDate.text = date
        hasType = !intent.getStringExtra("type").isNullOrBlank()
        b.btnFaststock.visibility = if (hasType) View.VISIBLE else View.GONE

        // 备注从缓存恢复
        remark = SpCache.getRemark()
        b.etRemark.setText(remark)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = ListAdapter(orderData)

        b.rgBilltype.setOnCheckedChangeListener { _, id ->
            billtype = if (id == R.id.rb_ckd) "ckd" else "tkd"
        }
        b.tvDate.setOnClickListener { pickDate() }
        b.tvCustRow.setOnClickListener { goPickCustomer() }

        b.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                b.btnConfirmOrCancel.text = if (!s.isNullOrEmpty()) "确认" else "取消"
            }
        })
        b.btnConfirmOrCancel.setOnClickListener { onConfirmOrCancel() }
        b.btnScan.setOnClickListener { onScanClick() }
        b.btnPaste.setOnClickListener { pasteAndImport() }
        b.btnSave.setOnClickListener { saveOrder() }
        b.btnFaststock.setOnClickListener { fastStock() }

        b.etRemark.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                remark = s?.toString() ?: ""
                SpCache.setRemark(remark)
            }
        })

        // 收货单过来的 billid 自动加载
        val billid = intent.getStringExtra("billid")
        if (!billid.isNullOrBlank()) {
            loadReceiveDetail(billid)
        } else {
            // 进入时按原 wx 行为清缓存
            SpCache.clearOrderDraft()
        }
    }

    override fun onResume() {
        super.onResume()
        val custom = SpCache.getCustom()
        if (custom != null) {
            b.tvCust.text = "${custom["code"] ?: ""}  ${custom["name"] ?: ""} ${custom["LegalPerson"] ?: ""}"
        } else {
            b.tvCust.text = getString(R.string.oc_cust_default)
        }
    }

    private fun pickDate() {
        val cal = java.util.Calendar.getInstance()
        val dlg = android.app.DatePickerDialog(this, { _, y, m, d ->
            date = String.format("%04d-%02d-%02d", y, m + 1, d)
            b.tvDate.text = date
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        dlg.datePicker.calendarViewShown = false
        dlg.datePicker.spinnersShown = true
        dlg.show()
    }

    private fun goPickCustomer() {
        startActivity(Intent(this, AddressManagerActivity::class.java))
    }

    private fun loadReceiveDetail(billid: String) {
        MfUi.showLoading(this)
        Net.req("receive/getReceiveDetail", mapOf("billid" to billid)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                if (res.ok) {
                    val arr = res.dataJson
                    if (arr != null && arr.length() > 0) {
                        codeList.clear()
                        orderData.clear()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            codeList.add(o.optString("code", ""))
                            orderData.add(o)
                        }
                        SpCache.setOrderData(codeList.toList())
                        b.etInput.setText("")
                        b.list.adapter?.notifyDataSetChanged()
                    } else {
                        MfUi.toast(this, R.string.oc_tip_recv_empty)
                    }
                } else {
                    MfUi.toast(this, R.string.oc_tip_recv_empty2)
                }
            }
        }
    }

    private fun onScanClick() {
        // 第一步：占位。点击弹 Toast 提示，不调 addclick。
        // TODO: 第二步对接商米 Scanner SDK，扫到码后把 res.result 喂给 addByCode(code)
        MfUi.toast(this, R.string.oc_scan_placeholder)
    }

    /** 公共：手动输入框/扫码成功调用此方法把一个码塞进列表。 */
    private fun addByCode(rawCode: String) {
        val code = rawCode.replace("http://yzj.mefront.com/q/", "")
        if (code.isBlank()) return
        if (codeList.contains(code)) {
            MfUi.alert(this, getString(R.string.oc_tip_code_exists), "")
            return
        }
        Net.req("orderapi/getcode", mapOf("goodno" to code, "billtype" to billtype)) { err, res ->
            runOnUiThread {
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        val arr = res.dataJson
                        if (arr != null && arr.length() > 0) {
                            val o = arr.getJSONObject(0)
                            codeList.add(code)
                            orderData.add(o)
                            SpCache.setOrderData(codeList.toList())
                            b.list.adapter?.notifyDataSetChanged()
                            b.etInput.setText("")
                        }
                    }
                    "0" -> MfUi.alert(this, getString(R.string.oc_tip_code_empty), "")
                    "2" -> MfUi.alert(this, getString(R.string.oc_tip_code_unavail), "")
                    "3" -> MfUi.alert(this, getString(R.string.oc_tip_not_received), "")
                }
            }
        }
    }

    private fun onConfirmOrCancel() {
        val txt = b.etInput.text.toString()
        if (txt.isNotEmpty()) {
            addByCode(txt)
        }
        // 空时按钮 = "取消"，无操作
    }

    private fun pasteAndImport() {
        MfUi.showLoading(this, "导入中...")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            MfUi.hideLoading()
            MfUi.toast(this, R.string.oc_tip_clipboard_empty)
            return
        }
        val text = clip.getItemAt(0).text?.toString() ?: ""
        if (text.isBlank()) {
            MfUi.hideLoading()
            MfUi.toast(this, R.string.oc_tip_clipboard_empty)
            return
        }
        val codes = text.split(Regex("[\\s,;]+"))
            .map { it.replace("http://yzj.mefront.com/q/", "") }
            .filter { it.isNotBlank() }
        val toQuery = codes.filterNot { codeList.contains(it) }
        if (toQuery.isEmpty()) {
            MfUi.hideLoading()
            MfUi.toast(this, R.string.oc_tip_all_imported)
            return
        }
        Net.req("orderapi/getALLCode", mapOf("goodnos" to toQuery, "billtype" to billtype)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.oc_tip_import_fail); return@runOnUiThread }
                val r = res.result?.toString()
                if (r == "1" || r == "2") {
                    val arr = res.dataJson
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val c = o.optString("code", "")
                            if (c.isNotBlank() && !codeList.contains(c)) {
                                codeList.add(c); orderData.add(o)
                            }
                        }
                        SpCache.setOrderData(codeList.toList())
                        b.list.adapter?.notifyDataSetChanged()
                    }
                    if (r == "2") MfUi.toast(this, R.string.oc_tip_partial)
                } else if (r == "3") {
                    MfUi.toast(this, R.string.oc_tip_no_avail)
                } else {
                    MfUi.toast(this, R.string.oc_tip_all_imported)
                }
            }
        }
    }

    private fun saveOrder() {
        val cust = SpCache.getCustom()
        if (cust == null) { MfUi.toast(this, R.string.oc_cust_default); return }
        if (codeList.isEmpty()) { MfUi.toast(this, R.string.oc_tip_no_code); return }
        MfUi.showLoading(this)
        Net.req("orderapi/saveorder", mapOf(
            "billtype" to billtype,
            "cusCode" to (cust["code"]?.toString() ?: ""),
            "remark" to remark,
            "makedate" to date,
            "orderData" to codeList.toList()
        )) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        val newBillid = res.raw.optString("NewBillID", "")
                        MfUi.confirm(this, getString(R.string.oc_save_confirm), onConfirm = {
                            confirmOrder(newBillid)
                        }, confirmText = "出库", cancelText = "仅保存")
                    }
                    "2" -> MfUi.toast(this, R.string.oc_tip_cust_mismatch)
                    "3" -> MfUi.toast(this, R.string.oc_tip_no_code)
                    else -> MfUi.toast(this, res.msg.ifBlank { "提交失败" })
                }
            }
        }
    }

    private fun confirmOrder(newBillid: String) {
        MfUi.showLoading(this)
        Net.req("orderapi/confirmorder", mapOf("billid" to newBillid)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> MfUi.toast(this, R.string.ot_ship_ok)
                    "0" -> MfUi.toast(this, R.string.ot_ship_data_err)
                    "2" -> MfUi.toast(this, R.string.ot_ship_partial_fail)
                }
                goList()
            }
        }
    }

    private fun fastStock() {
        MfUi.confirm(this, getString(R.string.oc_faststock_confirm), onConfirm = {
            startActivity(Intent(this, ReceiveListActivity::class.java))
        })
    }

    private fun goList() {
        startActivity(Intent(this, OrdertotalActivity::class.java).putExtra("id", 0))
        finish()
    }

    private inner class ListAdapter(val data: List<JSONObject>) :
        RecyclerView.Adapter<ListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvRow: TextView = v.findViewById(R.id.tv_row)
            val tvCode: TextView = v.findViewById(R.id.tv_code)
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvSpec: TextView = v.findViewById(R.id.tv_spec)
            val tvSpec2: TextView = v.findViewById(R.id.tv_spec2)
            val btnDel: Button = v.findViewById(R.id.btn_del)
        }
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_order_confirm, p, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            h.tvRow.text = (position + 1).toString()
            h.tvCode.text = o.optString("code", "")
            h.tvName.text = o.optString("Name", "")
            h.tvSpec.text = o.optString("Spec", "")
            h.tvSpec2.text = o.optString("Spec2", "")
            h.btnDel.setOnClickListener {
                codeList.removeAt(position)
                orderData.removeAt(position)
                SpCache.setOrderData(codeList.toList())
                notifyDataSetChanged()
            }
        }
        override fun getItemCount(): Int = data.size
    }
}

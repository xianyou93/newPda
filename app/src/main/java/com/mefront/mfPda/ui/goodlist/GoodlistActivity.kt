package com.mefront.mfPda.ui.goodlist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.databinding.ActivityGoodlistBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.ordertotal.OrdertotalActivity
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class GoodlistActivity : BaseActivity() {

    private lateinit var b: ActivityGoodlistBinding
    private val list = mutableListOf<JSONObject>()
    private var billid: String = ""
    private var orderhead: JSONObject? = null

    override fun title(): CharSequence = "出库单明细"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGoodlistBinding.inflate(layoutInflater)
        setContentView(b.root)

        billid = intent.getStringExtra("billid") ?: ""
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(list)

        loadDetail()
    }

    private fun loadDetail() {
        MfUi.showLoading(this, getString(R.string.gl_loading))
        Net.req("orderapi/orderdetail", mapOf("billid" to billid)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                handleDetail(err, res)
            }
        }
    }

    private fun handleDetail(err: Throwable?, res: ApiResponse?) {
        if (err != null || res == null) {
            MfUi.toast(this, R.string.network_error); return
        }
        if (!res.ok) { MfUi.toast(this, R.string.network_error); return }
        val head = res.raw.optJSONObject("orderhead")
        orderhead = head
        renderHead(head)
        val arr = res.dataJson
        list.clear()
        if (arr != null) {
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        }
        b.list.adapter?.notifyDataSetChanged()
    }

    private fun renderHead(head: JSONObject?) {
        if (head == null) return
        val status = head.optString("Status", "")
        b.tvCustCode.text = "客户代码:${head.optString("CusCode", "")}"
        b.tvCustName.text = "客户名称:${head.optString("name", "")}"
        b.tvDate.text = "日期:${head.optString("MakeDate", "")}"
        b.tvStatus.text = if (status == "已收货") "状态:已出库" else "状态:$status"
        b.tvType.text = "类型:${head.optString("BillType", "")}"
        val isEditable = status == "制单"
        b.btnOutstock.visibility = if (isEditable) View.VISIBLE else View.GONE
        b.btnDelete.visibility = if (isEditable) View.VISIBLE else View.GONE
        b.btnOutstock.setOnClickListener { confirmOrder() }
        b.btnDelete.setOnClickListener { deleteOrder() }
    }

    private fun confirmOrder() {
        MfUi.showLoading(this, getString(R.string.gl_confirming))
        Net.req("orderapi/confirmorder", mapOf("billid" to billid)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> { MfUi.toast(this, R.string.ot_ship_ok); loadDetail() }
                    "0" -> MfUi.toast(this, R.string.ot_ship_data_err)
                    "2" -> MfUi.toast(this, R.string.ot_ship_partial_fail)
                }
            }
        }
    }

    private fun deleteOrder() {
        MfUi.confirm(this, getString(R.string.gl_delete_confirm), onConfirm = {
            MfUi.showLoading(this, getString(R.string.gl_deleting_order))
            Net.req("orderapi/orderdelete", mapOf("billid" to billid)) { err, res ->
                runOnUiThread {
                    MfUi.hideLoading()
                    if (err != null || res == null) {
                        MfUi.toast(this, R.string.network_error); return@runOnUiThread
                    }
                    if (res.ok) {
                        startActivity(Intent(this, OrdertotalActivity::class.java).putExtra("id", 0))
                        finish()
                    } else {
                        MfUi.toast(this, res.msg.ifBlank { "删除失败" })
                    }
                }
            }
        })
    }

    private fun rowDelete(code: String) {
        MfUi.showLoading(this, getString(R.string.gl_deleting_item))
        Net.req("orderapi/delcode", mapOf("billid" to billid, "scancode" to code)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        val arr = res.dataJson
                        list.clear()
                        if (arr != null) {
                            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                        }
                        b.list.adapter?.notifyDataSetChanged()
                    }
                    "0" -> MfUi.toast(this, R.string.gl_tip_billid_empty)
                    "2" -> MfUi.toast(this, R.string.gl_tip_status_block)
                }
            }
        }
    }

    private inner class Adapter(val data: List<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvRow: TextView = v.findViewById(R.id.tv_row)
            val tvCode: TextView = v.findViewById(R.id.tv_code)
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvSpec: TextView = v.findViewById(R.id.tv_spec)
            val tvSpec2: TextView = v.findViewById(R.id.tv_spec2)
            val btnDel: Button = v.findViewById(R.id.btn_del)
        }
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_goodlist, p, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            h.tvRow.text = o.optString("RowNumber", "")
            h.tvCode.text = o.optString("ScanCode", "")
            h.tvName.text = o.optString("Name", "")
            h.tvSpec.text = o.optString("Spec", "")
            h.tvSpec2.text = o.optString("Spec2", "")
            val editable = orderhead?.optString("Status", "") == "制单"
            h.btnDel.visibility = if (editable) View.VISIBLE else View.GONE
            h.btnDel.setOnClickListener { rowDelete(o.optString("ScanCode", "")) }
        }
        override fun getItemCount(): Int = data.size
    }
}

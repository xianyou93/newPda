package com.mefront.mfPda.ui.refund

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.databinding.ActivityRefundDetailBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class RefundDetailActivity : BaseActivity() {

    private lateinit var b: ActivityRefundDetailBinding
    private val list = mutableListOf<JSONObject>()
    private var billId = ""
    private var refundhead: JSONObject? = null

    override fun title(): CharSequence = getString(R.string.rd_title)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRefundDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        billId = intent.getStringExtra("billId") ?: ""
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(list)

        b.btnCopy.setOnClickListener { copyCodes() }
        loadDetail()
    }

    private fun loadDetail() {
        MfUi.showLoading(this, getString(R.string.rd_loading))
        Net.req("refundOrder/getRefundDetail", mapOf("billId" to billId)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                handleData(err, res)
            }
        }
    }

    private fun handleData(err: Throwable?, res: ApiResponse?) {
        if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return }
        if (!res.ok) { MfUi.toast(this, R.string.rd_sys_err); return }
        val head = res.raw.optJSONObject("refundhead")
        refundhead = head
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
        b.tvBillNo.text = "单据号: ${head.optString("billNo", "")}"
        b.tvDate.text = "日期: ${head.optString("refundDate", "")}"
        val state = head.optString("state", "")
        b.tvState.text = "状态: ${if (state == "0") "待审核" else if (state == "1") "已审核" else state}"
        b.tvReason.text = "说明: ${head.optString("refundReason", "")}"
    }

    private fun copyCodes() {
        if (list.isEmpty()) return
        val sb = StringBuilder()
        for ((i, o) in list.withIndex()) {
            val code = o.optString("code", "")
            if (i > 0) sb.append("\n")
            sb.append(code)
        }
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("refund_codes", sb.toString()))
        MfUi.toast(this, R.string.rd_copy_ok)
    }

    private inner class Adapter(val data: List<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvRow: TextView = v.findViewById(R.id.tv_row)
            val tvCode: TextView = v.findViewById(R.id.tv_code)
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvSpec: TextView = v.findViewById(R.id.tv_spec)
            val tvSpec2: TextView = v.findViewById(R.id.tv_spec2)
        }
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_refund_detail, p, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            h.tvRow.text = o.optString("RowNumber", "")
            h.tvCode.text = o.optString("code", "")
            h.tvName.text = o.optString("NAME", o.optString("Name", ""))
            h.tvSpec.text = o.optString("Spec", "")
            h.tvSpec2.text = o.optString("Spec2", "")
        }
        override fun getItemCount(): Int = data.size
    }
}

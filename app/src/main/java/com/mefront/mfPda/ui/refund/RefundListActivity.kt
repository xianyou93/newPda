package com.mefront.mfPda.ui.refund

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.mefront.mfPda.databinding.ActivityRefundListBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class RefundListActivity : BaseActivity() {

    private lateinit var b: ActivityRefundListBinding
    private val list = mutableListOf<JSONObject>()
    private var pageNo = 1
    private var currentTab = 0
    private var loadVersion = 0

    override fun title(): CharSequence = getString(R.string.rl_title)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRefundListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(list)

        b.btnNewRefund.setOnClickListener {
            startActivity(Intent(this, RefundConfirmActivity::class.java))
        }

        b.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { switchTab(tab?.position ?: 0) }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        })
        b.tab.addTab(b.tab.newTab().setText(R.string.rl_tab_all))
        b.tab.addTab(b.tab.newTab().setText(R.string.rl_tab_pending))

        // 上拉加载
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (lm.findLastCompletelyVisibleItemPosition() >= list.size - 2 && list.isNotEmpty()) {
                    loadMore()
                }
            }
        })

        loadData()
    }

    private fun switchTab(pos: Int) {
        if (pos == currentTab) return
        currentTab = pos
        pageNo = 1
        loadVersion++
        list.clear()
        b.list.adapter?.notifyDataSetChanged()
        loadData()
    }

    private fun loadMore() {
        pageNo++
        loadData()
    }

    private fun loadData() {
        b.loadingBox.root.visibility = View.VISIBLE
        b.tvEmpty.root.visibility = View.GONE
        val ver = loadVersion
        val status = if (currentTab == 0) "" else "0"
        Net.req("refundOrder/refundOrderList", mapOf("status" to status, "pageNo" to pageNo.toString())) { err, res ->
            runOnUiThread {
                if (ver != loadVersion) return@runOnUiThread
                b.loadingBox.root.visibility = View.GONE
                handleData(err, res)
            }
        }
    }

    private fun handleData(err: Throwable?, res: ApiResponse?) {
        if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return }
        if (!res.ok) { MfUi.toast(this, R.string.network_error); return }
        val arr = res.dataJson
        if (arr == null || arr.length() == 0) {
            if (pageNo == 1) {
                list.clear()
                b.list.adapter?.notifyDataSetChanged()
                b.tvEmpty.root.visibility = View.VISIBLE
            }
            return
        }
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        b.list.adapter?.notifyDataSetChanged()
    }

    private fun deleteRefund(billId: String, position: Int) {
        Net.req("refundOrder/delRefundOrderList", mapOf("billId" to billId)) { err, res ->
            runOnUiThread {
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                if (res.ok) {
                    list.clear()
                    pageNo = 1
                    loadVersion++
                    loadData()
                } else {
                    MfUi.toast(this, "删除失败请联系管理员！")
                }
            }
        }
    }

    private fun copyCodes(billId: String) {
        Net.req("refundOrder/getRefundDetail", mapOf("billId" to billId)) { err, res ->
            runOnUiThread {
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                if (!res.ok) { MfUi.toast(this, R.string.rl_sys_err); return@runOnUiThread }
                val arr = res.dataJson
                if (arr == null || arr.length() == 0) { MfUi.toast(this, R.string.rl_sys_err); return@runOnUiThread }
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val code = arr.getJSONObject(i).optString("code", "")
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(code)
                }
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("refund_codes", sb.toString()))
                MfUi.toast(this, R.string.rl_copy_ok)
            }
        }
    }

    private inner class Adapter(val data: MutableList<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvState: TextView = v.findViewById(R.id.tv_state)
            val tvBillNo: TextView = v.findViewById(R.id.tv_bill_no)
            val tvReason: TextView = v.findViewById(R.id.tv_reason)
            val tvQty: TextView = v.findViewById(R.id.tv_qty)
            val tvDate: TextView = v.findViewById(R.id.tv_date)
            val btnCopy: Button = v.findViewById(R.id.btn_copy)
            val btnView: Button = v.findViewById(R.id.btn_view)
            val btnDel: Button = v.findViewById(R.id.btn_del)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_refund_list, p, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            val state = o.optString("state", "")
            h.tvState.text = when (state) {
                "0" -> "状态:待审核"
                "1" -> "状态:已审核"
                else -> "状态:$state"
            }
            h.tvBillNo.text = "退单号:${o.optString("billNo", "")}"
            h.tvReason.text = "原因:${o.optString("refundReason", "")}"
            h.tvQty.text = "退回数量：${o.optString("qty", "0")}件"
            h.tvDate.text = o.optString("refundDate", "")
            val billId = o.optString("billId", "")
            // 小程序：state=0显示[查看/删除/复制]，state=1显示[查看/复制]
            h.btnDel.visibility = if (state == "0") View.VISIBLE else View.GONE
            h.btnView.setOnClickListener {
                startActivity(Intent(this@RefundListActivity, RefundDetailActivity::class.java).putExtra("billId", billId))
            }
            h.btnDel.setOnClickListener { deleteRefund(billId, position) }
            h.btnCopy.setOnClickListener { copyCodes(billId) }
        }

        override fun getItemCount(): Int = data.size
    }
}

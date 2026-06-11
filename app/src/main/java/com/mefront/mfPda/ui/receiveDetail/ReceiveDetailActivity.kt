package com.mefront.mfPda.ui.receiveDetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.databinding.ActivityReceiveDetailBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class ReceiveDetailActivity : BaseActivity() {

    private lateinit var b: ActivityReceiveDetailBinding
    private var billid: String = ""
    private val data = mutableListOf<JSONObject>()
    private var orderhead: JSONObject? = null

    override fun title(): CharSequence = "收货单详情"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReceiveDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        billid = intent.getStringExtra("billid") ?: ""
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(data)

        MfUi.showLoading(this, getString(R.string.gl_loading))
        Net.req("receive/getReceiveDetail", mapOf("billid" to billid)) { err, res -> handle(err, res) }
    }

    private fun handle(err: Throwable?, res: ApiResponse?) {
        runOnUiThread {
            MfUi.hideLoading()
            if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
            if (!res.ok) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
            orderhead = res.raw.optJSONObject("orderhead")
            renderHead()
            val arr = res.dataJson
            data.clear()
            if (arr != null) {
                for (i in 0 until arr.length()) data.add(arr.getJSONObject(i))
            }
            b.list.adapter?.notifyDataSetChanged()
        }
    }

    private fun renderHead() {
        val h = orderhead ?: return
        b.tvCustCode.text = "客户代码:${h.optString("CusCode", "")}"
        b.tvCustName.text = "客户名称:${h.optString("NAME", "")}"
        b.tvOrderNo.text = "订单号:${h.optString("Code", "")}"
        b.tvDate.text = "日期:${h.optString("MakeDate", "")}"
        b.tvType.text = "类型:${h.optString("BillType", "")}"
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
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_goodlist, p, false)
            v.findViewById<View>(R.id.btn_del).visibility = View.GONE
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            h.tvRow.text = o.optString("RowNumber", "")
            h.tvCode.text = o.optString("code", "")
            h.tvName.text = o.optString("Name", "")
            h.tvSpec.text = o.optString("Spec", "")
            h.tvSpec2.text = o.optString("Spec2", "")
        }
        override fun getItemCount(): Int = data.size
    }
}

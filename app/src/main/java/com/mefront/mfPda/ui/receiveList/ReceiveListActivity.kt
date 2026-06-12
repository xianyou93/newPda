package com.mefront.mfPda.ui.receiveList

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityReceiveListBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.orderConfirm.OrderConfirmActivity
import com.mefront.mfPda.ui.receiveDetail.ReceiveDetailActivity
import com.mefront.mfPda.widget.MfUi
import android.graphics.Color
import org.json.JSONObject

class ReceiveListActivity : BaseActivity() {

    private lateinit var b: ActivityReceiveListBinding
    private val data = mutableListOf<JSONObject>()
    private var pageNo = 1
    private var loading = false
    private var keyword: String = ""
    private var loadVersion = 0   // 请求版本号，丢弃旧请求的响应

    override fun title(): CharSequence = "收货单列表"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReceiveListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(data)

        b.etKeyword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { keyword = s?.toString()?.trim() ?: "" }
        })
        b.btnQuery.setOnClickListener {
            data.clear(); pageNo = 1; loadVersion++; load()
        }
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!loading && lm.findLastVisibleItemPosition() >= (b.list.adapter?.itemCount ?: 0) - 3) {
                    pageNo++; load()
                }
            }
        })
    }

    private var firstLoad = true

    override fun onResume() {
        super.onResume()
        if (firstLoad) {
            firstLoad = false
            load()
        }
    }

    private fun load() {
        loading = true
        val version = loadVersion
        b.loadingBox.root.visibility = View.VISIBLE
        b.tvEmpty.root.visibility = View.GONE
        Net.req("receive/receivelist", mapOf("type" to 2, "pageNo" to pageNo, "keyword" to keyword)) { err, res ->
            runOnUiThread { handle(err, res, version) }
        }
    }

    private fun handle(err: Throwable?, res: ApiResponse?, version: Int) {
        // 丢弃旧请求的响应
        if (version != loadVersion) return
        loading = false
        b.loadingBox.root.visibility = View.GONE
        if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return }
        if (!res.ok) { MfUi.toast(this, R.string.network_error); return }
        val arr = res.dataJson
        if (arr == null || arr.length() == 0) {
            if (pageNo == 1) {
                b.tvEmpty.root.visibility = View.VISIBLE
                b.tvEmpty.root.text = getString(R.string.empty_data)
            }
            return
        }
        b.tvEmpty.root.visibility = View.GONE
        // 按 billid 分组：同 billid 的多条合并，第一条记 billid/类型/单号/状态/日期/地址 等，下挂产品列表
        val grouped = LinkedHashMap<String, MutableList<JSONObject>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val bid = o.optString("billid", "")
            grouped.getOrPut(bid) { mutableListOf() }.add(o)
        }
        for ((bid, items) in grouped) {
            val first = items[0]
            val total = items.sumOf { it.optInt("qty", 0) }
            val sold = items.sumOf { it.optInt("saleQty", 0) }
            val saleType = when {
                total == sold -> "完全出库"
                sold == 0 -> "未出库"
                else -> "部分出库"
            }
            val merged = JSONObject()
            merged.put("billid", bid)
            merged.put("BillType", first.optString("BillType", ""))
            merged.put("Code", first.optString("Code", ""))
            merged.put("NAME", first.optString("NAME", ""))
            merged.put("qty", total.toString())
            merged.put("MakeDate", first.optString("MakeDate", ""))
            merged.put("saleType", saleType)
            merged.put("consigneeAddress", first.optString("consigneeAddress", ""))
            merged.put("consigneeContact", first.optString("consigneeContact", ""))
            merged.put("consigneeTel1", first.optString("consigneeTel1", ""))
            merged.put("prdList", org.json.JSONArray(items.map {
                JSONObject().apply {
                    put("NAME", it.optString("prdName", ""))
                    put("sku", it.optString("sku", ""))
                    put("qty", it.optInt("qty", 0))
                    put("qtyType", it.optString("qtyType", ""))
                }
            }))
            data.add(merged)
        }
        b.list.adapter?.notifyDataSetChanged()
    }

    private inner class Adapter(val data: List<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: LinearLayout = v.findViewById(R.id.root)
            val tvType: TextView = v.findViewById(R.id.tv_type)
            val tvCode: TextView = v.findViewById(R.id.tv_code)
            val tvSaleType: TextView = v.findViewById(R.id.tv_sale_type)
            val btnView: Button = v.findViewById(R.id.btn_view)
            val btnGen: Button = v.findViewById(R.id.btn_gen)
            val address: LinearLayout = v.findViewById(R.id.box_address)
            val tvAddress: TextView = v.findViewById(R.id.tv_address)
            val contact: LinearLayout = v.findViewById(R.id.box_contact)
            val tvContact: TextView = v.findViewById(R.id.tv_contact)
            val tel: LinearLayout = v.findViewById(R.id.box_tel)
            val tvTel: TextView = v.findViewById(R.id.tv_tel)
            val tvQty: TextView = v.findViewById(R.id.tv_qty)
            val tvDate: TextView = v.findViewById(R.id.tv_date)
            val prdBox: LinearLayout = v.findViewById(R.id.prd_box)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_receive_list, p, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val o = data[position]
            val billType = o.optString("BillType", "")
            val saleType = o.optString("saleType", "")
            h.tvType.text = "类型:${billType}"
            h.tvCode.text = "单号:${o.optString("Code", "")}"
            h.tvSaleType.text = "出库状态:$saleType"
            val saleColor = when (saleType) {
                "未出库" -> Color.parseColor("#C91414")
                "完全出库" -> Color.parseColor("#29AC24")
                "部分出库" -> Color.parseColor("#1417C9")
                else -> Color.parseColor("#666666")
            }
            h.tvSaleType.setTextColor(saleColor)

            val billid = o.optString("billid", "")
            h.btnView.setOnClickListener {
                startActivity(Intent(this@ReceiveListActivity, ReceiveDetailActivity::class.java)
                    .putExtra("billid", billid))
            }
            // 仅 billType="出库单" 且非"完全出库"才显示"出库"按钮
            h.btnGen.visibility = if (billType == "出库单" && saleType != "完全出库") View.VISIBLE else View.GONE
            h.btnGen.setOnClickListener { genOutstock(billid) }

            val addr = o.optString("consigneeAddress", "")
            h.address.visibility = if (addr.isNotEmpty()) View.VISIBLE else View.GONE
            h.tvAddress.text = addr
            val contact = o.optString("consigneeContact", "")
            h.contact.visibility = if (contact.isNotEmpty()) View.VISIBLE else View.GONE
            h.tvContact.text = contact
            val tel = o.optString("consigneeTel1", "")
            h.tel.visibility = if (tel.isNotEmpty()) View.VISIBLE else View.GONE
            h.tvTel.text = tel

            h.tvQty.text = "总${o.optString("qty", "0")}件"
            h.tvDate.text = o.optString("MakeDate", "")

            val prdArr = o.optJSONArray("prdList")
            h.prdBox.removeAllViews()
            if (prdArr != null) {
                for (i in 0 until prdArr.length()) {
                    val p = prdArr.getJSONObject(i)
                    val tv = TextView(this@ReceiveListActivity)
                    tv.text = "${p.optString("NAME", "")}  ${p.optString("sku", "")}  ${p.optInt("qty", 0)}${p.optString("qtyType", "")}"
                    tv.setTextColor(0xFF666666.toInt())
                    tv.textSize = 13f
                    tv.setPadding(0, 6, 0, 0)
                    h.prdBox.addView(tv)
                }
            }
        }

        override fun getItemCount(): Int = data.size
    }

    private fun genOutstock(billid: String) {
        MfUi.confirm(this, getString(R.string.rl_gen_confirm), onConfirm = {
            SpCache.clearOrderDraft()
            startActivity(Intent(this, OrderConfirmActivity::class.java).putExtra("billid", billid))
        })
    }
}

package com.mefront.mfPda.ui.ordertotal

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
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityOrdertotalBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.ui.addressManager.AddressManagerActivity
import com.mefront.mfPda.ui.goodlist.GoodlistActivity
import com.mefront.mfPda.ui.orderConfirm.OrderConfirmActivity
import com.mefront.mfPda.util.DateUtil
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

class OrdertotalActivity : BaseActivity() {

    private lateinit var b: ActivityOrdertotalBinding
    private lateinit var adapter: OrderAdapter
    private val data = mutableListOf<Row>()

    private var currentTab: Int = 0     // 0 全部订单 / 1 待出库
    private var pageNo: Int = 1
    private var loading: Boolean = false
    private var needRefresh: Boolean = true
    private var loadVersion: Int = 0   // 请求版本号，丢弃旧请求的响应
    private var currentCustCode: String = ""  // 当前查询的客户代码，与小程序 customInfo.code 对应

    override fun title(): CharSequence = "出库单列表"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOrdertotalBinding.inflate(layoutInflater)
        setContentView(b.root)

        currentTab = intent.getIntExtra("id", 0)

        b.tab.addTab(b.tab.newTab().setText(R.string.ot_tab_all).setTag(0))
        b.tab.addTab(b.tab.newTab().setText(R.string.ot_tab_pending).setTag(1))
        b.tab.getTabAt(if (currentTab == 1) 1 else 0)?.select()
        b.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val idx = tab?.tag as? Int ?: 0
                if (idx != currentTab) {
                    currentTab = idx
                    data.clear()
                    pageNo = 1
                    loadVersion++  // 作废旧请求
                    adapter.notifyDataSetChanged()
                    load()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        b.etStart.setText(DateUtil.beforeMonthTime())
        b.etEnd.setText(DateUtil.currentTime())

        b.etStart.setOnClickListener { pickDate(b.etStart) }
        b.etEnd.setOnClickListener { pickDate(b.etEnd) }

        b.btnNewOutstock.setOnClickListener {
            needRefresh = true
            startActivity(Intent(this, OrderConfirmActivity::class.java))
        }
        b.btnClean.setOnClickListener { cleanCustomer() }
        b.custRow.setOnClickListener {
            needRefresh = true
            startActivity(Intent(this, AddressManagerActivity::class.java))
        }
        b.list.layoutManager = LinearLayoutManager(this)
        adapter = OrderAdapter(data)
        b.list.adapter = adapter
        b.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (!loading && lm.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
                    pageNo++
                    load()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val custom = SpCache.getCustom2()
        if (custom != null) {
            currentCustCode = custom["code"]?.toString() ?: ""
            b.tvCust.text = "${custom["code"] ?: ""} ${custom["name"] ?: ""} ${custom["LegalPerson"] ?: ""}"
            SpCache.setCustom2(null)
        }
        // getCustom2() 为 null 时保持原有筛选条件不变（从新增出库页返回不应丢失客户筛选）
        if (needRefresh) {
            data.clear()
            pageNo = 1
            loadVersion++  // 作废旧请求
            adapter.notifyDataSetChanged()
            load()
        }
        needRefresh = false
    }

    private fun cleanCustomer() {
        SpCache.setCustom2(null)
        currentCustCode = ""
        b.tvCust.text = getString(R.string.ot_cust_default)
        needRefresh = true
        data.clear()
        pageNo = 1
        loadVersion++  // 作废旧请求
        adapter.notifyDataSetChanged()
        load()
    }

    private fun load() {
        loading = true
        val version = loadVersion
        val status = if (currentTab == 0) "" else "制单"
        Net.req("orderapi/orderlist",
            mapOf(
                "ordersn" to "1",
                "status" to status,
                "pageNo" to pageNo,
                "cusCode" to currentCustCode,
                "startDate" to b.etStart.text.toString(),
                "endDate" to b.etEnd.text.toString()
            )
        ) { err, res -> handle(err, res, version) }
    }

    private fun handle(err: Throwable?, res: ApiResponse?, version: Int) {
        runOnUiThread {
            // 丢弃旧请求的响应
            if (version != loadVersion) return@runOnUiThread
            loading = false
            b.loadingBox.root.visibility = View.GONE
            if (err != null || res == null) {
                MfUi.toast(this, R.string.network_error); return@runOnUiThread
            }
            if (!res.ok) {
                if (res.isResultZero) MfUi.toast(this, R.string.network_error)
                return@runOnUiThread
            }
            val arr = res.dataJson ?: return@runOnUiThread
            if (arr.length() == 0) {
                // 只有第一页空数据才显示"没有数据"，后续页空表示没有更多
                if (pageNo == 1) {
                    b.tvEmpty.root.visibility = View.VISIBLE
                    b.tvEmpty.root.text = getString(R.string.empty_data)
                }
                return@runOnUiThread
            }
            b.tvEmpty.root.visibility = View.GONE
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val st = o.optString("Status", "")
                val billType = o.optString("BillType", "")
                val isTkd = billType == "退库单"
                val isShipped = st == "已收货" || st == "已退库"
                data.add(Row(
                    state = if (isShipped) "已出库" else st,
                    billType = billType,
                    billid = o.optString("billid", ""),
                    name = o.optString("name", ""),
                    qty = o.optString("qty", ""),
                    makedate = o.optString("MakeDate", ""),
                    listclass = if (isTkd) "tkd" else "",
                    showOutstock = !isShipped
                ))
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun pickDate(tv: TextView) {
        // 从 TextView 当前文本解析日期，解析失败则用今天
        val text = tv.text.toString()
        val parts = text.split("-")
        val cal = java.util.Calendar.getInstance()
        var year = cal.get(java.util.Calendar.YEAR)
        var month = cal.get(java.util.Calendar.MONTH)
        var day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        if (parts.size == 3) {
            try {
                year = parts[0].toInt()
                month = parts[1].toInt() - 1
                day = parts[2].toInt()
            } catch (_: NumberFormatException) {}
        }
        val dlg = android.app.DatePickerDialog(this, { _, y, m, d ->
            tv.text = String.format("%04d-%02d-%02d", y, m + 1, d)
            data.clear()
            pageNo = 1
            loadVersion++  // 作废旧请求
            adapter.notifyDataSetChanged()
            load()
        }, year, month, day)
        dlg.datePicker.calendarViewShown = false
        dlg.datePicker.spinnersShown = true
        dlg.show()
    }

    /** 单行 model。 */
    data class Row(
        val state: String,
        val billType: String,
        val billid: String,
        val name: String,
        val qty: String,
        val makedate: String,
        val listclass: String,
        val showOutstock: Boolean
    )

    private inner class OrderAdapter(val data: List<Row>) :
        RecyclerView.Adapter<OrderAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvState: TextView = v.findViewById(R.id.tv_state)
            val tvType: TextView = v.findViewById(R.id.tv_type)
            val btnView: Button = v.findViewById(R.id.btn_view)
            val btnOutstock: Button = v.findViewById(R.id.btn_outstock)
            val tvName: TextView = v.findViewById(R.id.tv_name)
            val tvQty: TextView = v.findViewById(R.id.tv_qty)
            val tvDate: TextView = v.findViewById(R.id.tv_date)
            val root: View = v.findViewById(R.id.card_root)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_ordertotal, p, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = data[position]
            h.tvState.text = "状态:${r.state}"
            h.tvType.text = "类型:${r.billType}"
            h.tvName.text = r.name
            h.tvQty.text = "${r.qty}件"
            h.tvDate.text = r.makedate
            h.btnView.setOnClickListener { goView(r.billid) }
            h.btnOutstock.visibility = if (r.showOutstock) View.VISIBLE else View.GONE
            h.btnOutstock.setOnClickListener { goOutstock(r.billid) }
            // 状态颜色用卡片背景区分（文字颜色统一黑色）
            val bg = when {
                r.listclass == "tkd" -> R.drawable.bg_tkd
                r.state == "制单" -> R.drawable.bg_card_pending
                r.state == "已出库" || r.state == "已收货" || r.state == "已退库" -> R.drawable.bg_card_done
                r.state == "部分出库" -> R.drawable.bg_card_partial
                else -> R.drawable.bg_card
            }
            h.root.setBackgroundResource(bg)
        }

        override fun getItemCount(): Int = data.size
    }

    private fun goView(billid: String) {
        needRefresh = true
        startActivity(Intent(this, GoodlistActivity::class.java).putExtra("billid", billid))
    }

    private fun goOutstock(billid: String) {
        Net.req("orderapi/confirmorder", mapOf("billid" to billid)) { err, res ->
            runOnUiThread {
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        MfUi.toast(this, R.string.ot_ship_ok)
                        data.clear(); pageNo = 1; loadVersion++; load()
                    }
                    "0" -> MfUi.toast(this, R.string.ot_ship_data_err)
                    "2" -> MfUi.toast(this, R.string.ot_ship_partial_fail)
                }
            }
        }
    }
}

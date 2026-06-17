package com.mefront.mfPda.ui.goodlist

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
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
import com.mefront.mfPda.util.Log
import com.mefront.mfPda.widget.MfUi
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.enums.Align
import com.sunmi.printerx.enums.DividingLine
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.TextStyle
import org.json.JSONObject

class GoodlistActivity : BaseActivity() {

    private lateinit var b: ActivityGoodlistBinding
    private val list = mutableListOf<JSONObject>()
    private var billid: String = ""
    private var orderhead: JSONObject? = null

    // 商米打印机
    private var mPrinter: PrinterSdk.Printer? = null
    private var printerReady = false

    override fun title(): CharSequence = "出库单明细"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGoodlistBinding.inflate(layoutInflater)
        setContentView(b.root)

        billid = intent.getStringExtra("billid") ?: ""
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(list)

        loadDetail()

        // 异步初始化商米打印机
        initPrinter()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放打印 SDK
        try { PrinterSdk.getInstance().destroy() } catch (_: Exception) {}
    }

    // ── 打印机初始化（异步） ──

    private fun initPrinter() {
        try {
            PrinterSdk.getInstance().getPrinter(this, object : PrinterSdk.PrinterListen {
                override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                    if (printer != null) {
                        mPrinter = printer
                        printerReady = true
                        Log.d("Print", "printer ready: ${printer.queryApi()?.status}")
                    }
                }

                override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
                    if (printers != null && printers.isNotEmpty() && mPrinter == null) {
                        mPrinter = printers[0]
                        printerReady = true
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("Print", "init printer fail: ${e.message}")
        }
    }

    // ── 加载数据 ──

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
        val isShipped = status == "已收货" || status == "已退库"
        b.btnOutstock.visibility = if (isEditable) View.VISIBLE else View.GONE
        b.btnDelete.visibility = if (isEditable) View.VISIBLE else View.GONE
        b.btnPrint.visibility = if (isShipped) View.VISIBLE else View.GONE
        b.btnOutstock.setOnClickListener { confirmOrder() }
        b.btnDelete.setOnClickListener { deleteOrder() }
        b.btnPrint.setOnClickListener { printOrder() }
        b.btnPrint.isEnabled = true
    }

    // ── 出库/删除 ──

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

    // ── 打印（官方 PrinterX SDK） ──

    private fun printOrder() {
        val head = orderhead ?: run { MfUi.toast(this, "数据未加载"); return }
        if (list.isEmpty()) { MfUi.toast(this, "没有可打印的数据"); return }

        if (!printerReady || mPrinter == null) {
            MfUi.toast(this, "打印机初始化中，请稍后重试")
            return
        }

        val api = mPrinter?.lineApi() ?: run { MfUi.toast(this, "打印服务不可用"); return }

        MfUi.toast(this, getString(R.string.gl_printing))

        try {
            // ── 弹出选择纸仓大小 ──
            AlertDialog.Builder(this)
                .setTitle("请选择纸仓大小")
                .setPositiveButton("58mm") { _: DialogInterface, _: Int ->
                    doPrint(api, 384, head)
                }
                .setNegativeButton("80mm") { _: DialogInterface, _: Int ->
                    doPrint(api, 576, head)
                }
                .show()
        } catch (e: Exception) {
            Log.e("Print", "print fail: ${e.message}")
            MfUi.toast(this, R.string.gl_print_fail)
        }
    }

    private fun doPrint(api: com.sunmi.printerx.api.LineApi, paperWidthPx: Int, head: JSONObject) {
        try {
            api.initLine(BaseStyle.getStyle().setWidth(paperWidthPx))

            // ── 列宽比例：7列（序号/箱码/产品名称/spacer/规格/spacer/规格2） ──
            // 序号缩小给箱码，spacer在产品名称↔规格、规格↔规格2之间留缝隙
            val colsArr = intArrayOf(4, 24, 16, 1, 24, 1, 8)
            val is80mm = paperWidthPx >= 500
            val detailTextSize = if (is80mm) 20 else 16
            val headerTextSize = if (is80mm) 24 else 20

            val fontHeader = TextStyle.getStyle().setTextSize(headerTextSize)
            // 内容列样式
            val colLeft = TextStyle.getStyle().setAlign(Align.LEFT).setTextSize(detailTextSize).setTextSpace(1)
            val colCenter = TextStyle.getStyle().setAlign(Align.CENTER).setTextSize(detailTextSize).setTextSpace(1)
            val colCenterBold = TextStyle.getStyle().setAlign(Align.CENTER).enableBold(true).setTextSize(detailTextSize + 2).setTextSpace(1)
            val colLeftBold = TextStyle.getStyle().setAlign(Align.LEFT).enableBold(true).setTextSize(detailTextSize + 2).setTextSpace(1)
            val spacerStyle = TextStyle.getStyle().setTextSize(1)

            // ── 表头（左对齐） ──
            api.initLine(BaseStyle.getStyle().setWidth(paperWidthPx).setAlign(Align.LEFT))
            api.printText("单据编号: ${head.optString("Code", "")}", fontHeader)
            api.printText("客户代码: ${head.optString("CusCode", "")}", fontHeader)
            api.printText("客户名称: ${head.optString("name", "")}", fontHeader)
            api.printText("日期: ${head.optString("MakeDate", "")}", fontHeader)
            api.printText("类型: ${head.optString("BillType", "")}", fontHeader)

            // ── 分隔线 ──
            api.printDividingLine(DividingLine.EMPTY, 6)
            api.printDividingLine(DividingLine.DOTTED, 2)
            api.printDividingLine(DividingLine.EMPTY, 6)

            // ── 列标题：序号竖排两行（"序"左对齐与表头"类"对齐） ──
            api.printTexts(
                arrayOf("序", "箱码", "产品名称", "", "规格", "", "规格2"),
                colsArr, arrayOf(colLeftBold, colCenterBold, colCenterBold, spacerStyle, colCenterBold, spacerStyle, colCenterBold)
            )
            api.printTexts(
                arrayOf("号", "", "", "", "", "", ""),
                colsArr, arrayOf(colLeftBold, spacerStyle, spacerStyle, spacerStyle, spacerStyle, spacerStyle, spacerStyle)
            )

            // 列标题和明细内容之间留间距
            api.printDividingLine(DividingLine.EMPTY, 4)

            for (i in list.indices) {
                val o = list[i]
                api.printTexts(
                    arrayOf(
                        (i + 1).toString(),
                        o.optString("ScanCode", ""),
                        o.optString("Name", ""),
                        "",
                        o.optString("Spec", ""),
                        "",
                        o.optString("Spec2", "")
                    ),
                    colsArr,
                    arrayOf(colLeft, colCenter, colCenter, spacerStyle, colCenter, spacerStyle, colCenter)
                )
                if (i < list.size - 1) api.printDividingLine(DividingLine.EMPTY, 4)
            }

            api.printDividingLine(DividingLine.EMPTY, 6)
            api.printDividingLine(DividingLine.DOTTED, 2)
            api.printDividingLine(DividingLine.EMPTY, 80)
            api.autoOut()

            MfUi.toast(this@GoodlistActivity, R.string.gl_print_ok)
        } catch (e: Exception) {
            Log.e("Print", "print fail: ${e.message}")
            MfUi.toast(this, R.string.gl_print_fail)
        }
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

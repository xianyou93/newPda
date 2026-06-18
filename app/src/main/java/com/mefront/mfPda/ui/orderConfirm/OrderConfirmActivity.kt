package com.mefront.mfPda.ui.orderConfirm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
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
import com.mefront.mfPda.util.LedUtil
import com.mefront.mfPda.widget.MfUi
import com.sunmi.scanner.IScanInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * 新增出库页。
 *
 * 关键适配：
 * - 扫码按钮：接入商米扫码头引擎 AIDL，连续模式扫描。
 *   点击"扫码"→ 红色激光持续扫描 → 按钮变"停止扫码"(红色) → 禁用输入框+确认+粘贴按钮。
 *   扫到码 → addByCode() → 重复码/无效码弹选择框(继续扫描/停止扫描)。
 *   点击"停止扫码"→ 恢复所有按钮初始状态。
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

    // ── 扫码相关 ──
    private var scanInterface: IScanInterface? = null
    private var scanReceiver: ScanReceiver? = null
    private var isScanning = false
    private var isLightOn = false
    private val processingCodes = mutableSetOf<String>()  // 防止键盘+广播双重处理
    private var lastScanBroadcastTime = 0L  // 最近收到广播的时间戳，用于拦截键盘 Enter
    private val scanTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable { stopScan() }

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
        b.list.setHasFixedSize(true)

        b.rgBilltype.setOnCheckedChangeListener { _, id ->
            billtype = if (id == R.id.rb_ckd) "ckd" else "tkd"
        }
        b.tvDate.setOnClickListener { pickDate() }
        b.tvCustRow.setOnClickListener { goPickCustomer() }

        b.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 输入框有内容 → 确认；空输入+未开灯+未扫码 → 开灯；其余状态保持按钮原有文字
                if (isScanning) {
                    // 扫码中，按钮文字由扫码状态控制
                } else if (isLightOn) {
                    b.btnConfirmOrCancel.text = getString(R.string.oc_btn_light_off)
                } else if (!s.isNullOrEmpty()) {
                    b.btnConfirmOrCancel.text = getString(R.string.oc_btn_confirm)
                } else {
                    b.btnConfirmOrCancel.text = getString(R.string.oc_btn_light_on)
                }
                // 输入框空→键盘显示↓收起；有内容→显示✓完成
                b.etInput.imeOptions = if (s.isNullOrEmpty())
                    android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                else
                    android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                // 扫码期间：商米键盘输出模式可能向输入框键入条码文本，需要清空以防重复处理
                if (isScanning && !s.isNullOrEmpty()) {
                    com.mefront.mfPda.util.Log.d("Scanner", "cleared keyboard scan input: $s")
                    b.etInput.setText("")
                }
            }
        })
        // 扫码/开灯期间拦截 Enter 键（防止商米扫码键盘输出模式误触发确认动作）
        b.etInput.setOnEditorActionListener { _, actionId, event ->
            if (isScanning || isLightOn) {
                com.mefront.mfPda.util.Log.d("Scanner", "blocked editor action while scanning/light")
                true
            } else if (System.currentTimeMillis() - lastScanBroadcastTime < 500) {
                // 广播刚收到扫码结果，键盘输出的 Enter 可能还在队列中，拦截
                com.mefront.mfPda.util.Log.d("Scanner", "blocked editor action post-scan (${System.currentTimeMillis() - lastScanBroadcastTime}ms)")
                b.etInput.setText("")  // 清空键盘输入的码防止残留
                true
            } else if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                // 输入框为空时，不执行确认/开灯，只隐藏键盘
                if (b.etInput.text.isNullOrBlank()) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(b.etInput.windowToken, 0)
                    return@setOnEditorActionListener true
                }
                onConfirmOrCancel()
                true
            } else {
                false
            }
        }

        // 扫码/开灯期间点击输入框时提示
        b.etInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && (isScanning || isLightOn)) {
                b.etInput.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(b.etInput.windowToken, 0)
                MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            }
        }
        b.btnConfirmOrCancel.setOnClickListener {
            if (isScanning) return@setOnClickListener
            if (isLightOn) {
                stopLight()
            } else if (b.etInput.text.isNullOrBlank()) {
                startLight()
            } else {
                onConfirmOrCancel()
            }
        }
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

        initLight()
        // 绑定商米扫码服务 + 注册广播（try-catch 防闪退：服务不存在时 bindService 可能抛 SecurityException）
        try { bindScannerService() } catch (e: Exception) { com.mefront.mfPda.util.Log.d("Scanner", "bindService failed: ${e.message}") }
        try { registerScanReceiver() } catch (e: Exception) { com.mefront.mfPda.util.Log.d("Scanner", "registerReceiver failed: ${e.message}") }
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

    override fun onDestroy() {
        stopScan()
        stopLight()
        scanChoiceDialog?.dismiss()
        scanChoiceDialog = null
        unbindScannerService()
        unregisterScanReceiver()
        super.onDestroy()
    }

    // ── 扫码服务绑定 ──

    // ── 权限回调 ──
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLight()
        } else if (requestCode == 1001) {
            MfUi.toast(this, "需要相机权限才能开灯")
        }
    }

    // ── 闪光灯控制（sysfs 优先 → CameraManager 兜底）──

    private fun initLight() {
        LedUtil.findLedPath()
    }

    private fun startLight() {
        // 检查相机权限
        if (!LedUtil.hasCameraPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001)
            return
        }
        if (LedUtil.turnOn(this)) {
            isLightOn = true
            b.btnConfirmOrCancel.text = getString(R.string.oc_btn_light_off)
            b.btnConfirmOrCancel.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.btn_red, null))
        } else {
            com.mefront.mfPda.util.Log.e("Light", "startLight failed")
            MfUi.toast(this, "开灯失败")
        }
    }

    private fun stopLight() {
        LedUtil.turnOff()
        isLightOn = false
        b.btnConfirmOrCancel.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.btn_green, null))
        refreshConfirmOrCancelBtn()
    }

    private fun refreshConfirmOrCancelBtn() {
        // 根据当前输入框内容恢复按钮文字
        if (!b.etInput.text.isNullOrBlank()) {
            b.btnConfirmOrCancel.text = getString(R.string.oc_btn_confirm)
        } else {
            b.btnConfirmOrCancel.text = getString(R.string.oc_btn_light_on)
        }
    }

    // ── 扫码服务绑定 ──

    private val scanConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            scanInterface = IScanInterface.Stub.asInterface(service)
            com.mefront.mfPda.util.Log.d("Scanner", "onServiceConnected: ${name?.flattenToString()}")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            scanInterface = null
            com.mefront.mfPda.util.Log.d("Scanner", "onServiceDisconnected")
        }
    }

    private fun bindScannerService() {
        val intent = Intent()
        intent.setPackage("com.sunmi.scanner")
        intent.setAction("com.sunmi.scanner.IScanInterface")
        // 按官方 demo: 先 startService 确保服务运行，再 bindService
        startService(intent)
        bindService(intent, scanConn, Context.BIND_AUTO_CREATE)
    }

    private fun unbindScannerService() {
        try { unbindService(scanConn) } catch (_: Exception) {}
        scanInterface = null
    }

    // ── 广播注册 ──

    private fun registerScanReceiver() {
        scanReceiver = ScanReceiver()
        val filter = IntentFilter()
        filter.addAction("com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
    }

    private fun unregisterScanReceiver() {
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
        scanReceiver = null
    }

    // ── 扫码按钮点击 ──

    private fun onScanClick() {
        if (isScanning) stopScan() else startScan()
    }

    private fun startScan() {
        if (scanInterface == null) {
            MfUi.toast(this, "扫码服务连接中，请稍后重试")
            return
        }
        try {
            scanInterface?.scan()
            com.mefront.mfPda.util.Log.d("Scanner", "scan() called, laser should be ON")
        } catch (e: RemoteException) {
            com.mefront.mfPda.util.Log.d("Scanner", "scan() failed: ${e.message}")
            MfUi.toast(this, "扫码启动失败: ${e.message}")
            return
        }
        // 扫码成功启动后才设置状态和修改 UI
        isScanning = true
        b.btnScan.text = getString(R.string.oc_btn_stop_scan)
        b.btnScan.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.btn_red, null))
        // 锁住开灯：扫码时关掉灯光
        if (isLightOn) stopLight()
        // 5秒无扫码自动熄光保护
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 5000)
    }

    private fun stopScan() {
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
        isScanning = false
        // 恢复按钮初始状态（用 backgroundTintList 保持 Material 圆角样式）
        b.btnScan.text = getString(R.string.oc_btn_scan)
        b.btnScan.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.btn_green, null))
        // 恢复 btnConfirmOrCancel 文字
        refreshConfirmOrCancelBtn()
        try { scanInterface?.stop() } catch (e: RemoteException) { e.printStackTrace() }
    }

    // ── 扫码结果广播接收器 ──

    inner class ScanReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 文档字段：data(字符串) / source_byte(byte[]原始数据)
            var code = intent.getStringExtra("data") ?: ""
            if (code.isEmpty()) {
                val arr = intent.getByteArrayExtra("source_byte")
                if (arr != null && arr.isNotEmpty()) {
                    code = String(arr, Charsets.UTF_8)
                }
            }
            if (code.isBlank()) return

            // 记录时间戳，用于拦截后续键盘输出 Enter
            lastScanBroadcastTime = System.currentTimeMillis()

            com.mefront.mfPda.util.Log.d("Scanner", "scan result: $code")
            // 收到广播说明激光正常，重置5秒超时
            scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
            scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 5000)
            addByCode(code, fromScan = true)
            // 连续扫码：扫到一个码后延迟 300ms 再次触发，激光保持亮着继续扫
            if (isScanning && scanInterface != null) {
                b.root.postDelayed({
                    if (isScanning && scanInterface != null) {
                        try { scanInterface?.scan() } catch (_: RemoteException) {}
                    }
                }, 300)
            }
        }
    }

    private fun pickDate() {
        // 从 TextView 当前文本解析日期，解析失败则用今天
        val text = b.tvDate.text.toString()
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
            date = String.format("%04d-%02d-%02d", y, m + 1, d)
            b.tvDate.text = date
        }, year, month, day)
        dlg.datePicker.calendarViewShown = false
        dlg.datePicker.spinnersShown = true
        dlg.show()
    }

    private fun goPickCustomer() {
        if (isScanning || isLightOn) {
            MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            return
        }
        startActivity(Intent(this, AddressManagerActivity::class.java))
    }

    private fun loadReceiveDetail(billid: String) {
        MfUi.showLoading(this)
        Net.req("receive/getReceiveDetail", mapOf("billid" to billid)) { err, res ->
            runOnUiThread {
                if (err != null || res == null) {
                    MfUi.hideLoading()
                    MfUi.toast(this, R.string.network_error); return@runOnUiThread
                }
                if (!res.ok) {
                    MfUi.hideLoading()
                    MfUi.toast(this, R.string.oc_tip_recv_empty2); return@runOnUiThread
                }
                val arr = res.dataJson
                if (arr == null || arr.length() == 0) {
                    MfUi.hideLoading()
                    MfUi.toast(this, R.string.oc_tip_recv_empty); return@runOnUiThread
                }
                // 提取所有条码
                val allCodes = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val code = arr.getJSONObject(i).optString("code", "")
                    if (code.isNotBlank()) allCodes.add(code)
                }
                if (allCodes.isEmpty()) {
                    MfUi.hideLoading()
                    MfUi.toast(this, R.string.oc_tip_recv_empty); return@runOnUiThread
                }
                // 用 getALLCode 校验可用性，只保留未出库的条码
                Net.req("orderapi/getALLCode", mapOf("goodnos" to JSONArray(allCodes).toString(), "billtype" to billtype)) { err2, res2 ->
                    runOnUiThread {
                        MfUi.hideLoading()
                        if (err2 != null || res2 == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                        val r = res2.result?.toString()
                        if (r == "1" || r == "2") {
                            val validated = res2.dataJson
                            if (validated != null && validated.length() > 0) {
                                codeList.clear()
                                orderData.clear()
                                for (i in 0 until validated.length()) {
                                    val o = validated.getJSONObject(i)
                                    val c = o.optString("code", "")
                                    if (c.isNotBlank()) {
                                        codeList.add(c)
                                        orderData.add(o)
                                    }
                                }
                                SpCache.setOrderData(codeList.toList())
                                b.etInput.setText("")
                                b.list.adapter?.notifyDataSetChanged()
                            }
                            if (r == "2") MfUi.toast(this, R.string.oc_tip_partial)
                        } else if (r == "3") {
                            MfUi.toast(this, R.string.oc_tip_no_avail)
                        } else {
                            MfUi.toast(this, R.string.oc_tip_recv_empty)
                        }
                    }
                }
            }
        }
    }

    /** 公共：手动输入框/扫码成功调用此方法把一个码塞进列表。
     *  fromScan=true 时，重复码/无效码弹选择框(继续扫描/停止扫描)。
     *  fromScan=false 时，走原有 alert 逻辑。 */
    private fun addByCode(rawCode: String, fromScan: Boolean = false) {
        val code = rawCode.replace("http://yzj.mefront.com/q/", "")
        if (code.isBlank()) return

        // 标记正在处理，用于拦截键盘输出 Enter
        lastScanBroadcastTime = System.currentTimeMillis()

        // 防止键盘输出+广播双重处理同一个码
        if (!processingCodes.add(code)) {
            com.mefront.mfPda.util.Log.d("Scanner", "addByCode dup skip: $code")
            b.etInput.setText("")
            return
        }

        if (codeList.contains(code)) {
            processingCodes.remove(code)
            if (fromScan) {
                showScanChoiceDialog(getString(R.string.oc_tip_code_exists))
            } else {
                MfUi.alert(this, getString(R.string.oc_tip_code_exists), "")
            }
            return
        }
        Net.req("orderapi/getcode", mapOf("goodno" to code, "billtype" to billtype)) { err, res ->
            runOnUiThread {
                processingCodes.remove(code)
                if (err != null || res == null) {
                    MfUi.toast(this, R.string.network_error)
                    if (fromScan && isScanning) {
                        // API 失败但继续扫描
                        try { scanInterface?.scan() } catch (_: RemoteException) {}
                    }
                    return@runOnUiThread
                }
                when (res.result?.toString()) {
                    "1" -> {
                        val o = res.dataObject
                        if (o != null) {
                            codeList.add(code)
                            orderData.add(o)
                            SpCache.setOrderData(codeList.toList())
                            b.list.adapter?.notifyDataSetChanged()
                            b.list.smoothScrollToPosition(orderData.size - 1)
                            b.etInput.setText("")
                        }
                    }
                    "0" -> {
                        if (fromScan) showScanChoiceDialog(getString(R.string.oc_tip_code_empty))
                        else MfUi.alert(this, getString(R.string.oc_tip_code_empty), "")
                    }
                    "2" -> {
                        if (fromScan) showScanChoiceDialog(getString(R.string.oc_tip_code_unavail))
                        else MfUi.alert(this, getString(R.string.oc_tip_code_unavail), "")
                    }
                    "3" -> {
                        if (fromScan) showScanChoiceDialog(getString(R.string.oc_tip_not_received))
                        else MfUi.alert(this, getString(R.string.oc_tip_not_received), "")
                    }
                }
            }
        }
    }

    /** 扫码期间弹出选择框：继续扫描 / 停止扫描。
     *  注意：商米扫码头在"键盘输出模式"下，扫到码后会发送 Enter 键事件，
     *  可能误触对话框按钮或触发 IME action，因此对话框必须拦截按键事件。 */
    private var scanChoiceDialog: android.app.Dialog? = null

    private fun showScanChoiceDialog(message: String) {
        if (isFinishing || isDestroyed) return
        scanChoiceDialog?.dismiss()
        
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null, false)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_content)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        tvTitle.text = message
        btnConfirm.text = getString(R.string.oc_btn_continue_scan)
        btnCancel.text = getString(R.string.oc_btn_stop_scan_choice)
        tvContent.visibility = View.GONE

        // ★ 禁止按钮 + 任何子 View 获取焦点
        btnConfirm.isFocusable = false
        btnConfirm.isFocusableInTouchMode = false
        btnCancel.isFocusable = false
        btnCancel.isFocusableInTouchMode = false
        // root view 也聚焦拦截（兜底）
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                true
            } else false
        }

        val dialog = android.app.Dialog(this, R.style.MfDialog)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                true
            } else false
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            scanChoiceDialog = null
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
            scanChoiceDialog = null
            stopScan()
        }

        scanChoiceDialog = dialog
        dialog.show()
    }

    private fun onConfirmOrCancel() {
        // 扫码期间禁用
        if (isScanning) {
            MfUi.toast(this, R.string.oc_tip_scan_input_blocked)
            return
        }
        val txt = b.etInput.text.toString()
        if (txt.isNotEmpty()) {
            addByCode(txt)
        }
        // 空时按钮 = "取消"，无操作
    }

    private fun pasteAndImport() {
        // 扫码/开灯期间禁用
        if (isScanning || isLightOn) {
            MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            return
        }
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
        Net.req("orderapi/getALLCode", mapOf("goodnos" to JSONArray(toQuery).toString(), "billtype" to billtype)) { err, res ->
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
        // 扫码/开灯期间禁用
        if (isScanning || isLightOn) {
            MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            return
        }
        val cust = SpCache.getCustom()
        if (cust == null) { MfUi.toast(this, R.string.oc_cust_default); return }
        if (codeList.isEmpty()) { MfUi.toast(this, R.string.oc_tip_no_code); return }
        MfUi.showLoading(this)
        Net.req("orderapi/saveorder", mapOf(
            "billtype" to billtype,
            "cusCode" to (cust["code"]?.toString() ?: ""),
            "remark" to remark,
            "makedate" to date,
            "orderData" to JSONArray(codeList).toString()
        )) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        val newBillid = res.raw.optString("NewBillID", "")
                        MfUi.confirm(this, getString(R.string.oc_save_confirm), onConfirm = {
                            confirmOrder(newBillid)
                        }, onCancel = {
                            goList()
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
        if (isScanning || isLightOn) {
            MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            return
        }
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

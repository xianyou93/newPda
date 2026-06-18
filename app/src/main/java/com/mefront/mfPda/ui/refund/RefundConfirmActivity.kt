package com.mefront.mfPda.ui.refund

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.databinding.ActivityRefundConfirmBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.util.LedUtil
import com.mefront.mfPda.util.Log
import com.mefront.mfPda.widget.MfUi
import com.sunmi.scanner.IScanInterface
import org.json.JSONObject

class RefundConfirmActivity : BaseActivity() {

    companion object {
        val savedCodes = mutableListOf<String>()
    }

    private lateinit var b: ActivityRefundConfirmBinding
    private val orderData = mutableListOf<JSONObject>()
    private val codesSet = mutableSetOf<String>()
    private var firstShow = true

    // 扫码相关
    private var scanInterface: IScanInterface? = null
    private var scanReceiver: ScanReceiver? = null
    private var isScanning = false
    private var isLightOn = false
    private val processingCodes = mutableSetOf<String>()
    private var lastScanBroadcastTime = 0L
    private val scanTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable { stopScan() }

    override fun title(): CharSequence = getString(R.string.rc_title)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRefundConfirmBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter(orderData)

        b.tvDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 输入框监听
        b.etCode.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isScanning) {
                } else if (isLightOn) {
                    b.btnConfirm.text = getString(R.string.oc_btn_light_off)
                } else if (!s.isNullOrEmpty()) {
                    b.btnConfirm.text = getString(R.string.oc_btn_confirm)
                } else {
                    b.btnConfirm.text = getString(R.string.oc_btn_light_on)
                }
                // 输入框空→键盘显示↓收起；有内容→显示✓完成
                b.etCode.imeOptions = if (s.isNullOrEmpty())
                    android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                else
                    android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                if (isScanning && !s.isNullOrEmpty()) {
                    Log.d("Scanner", "cleared keyboard scan input: $s")
                    b.etCode.setText("")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        b.btnScan.setOnClickListener { onScanClick() }
        b.btnConfirm.setOnClickListener { onConfirmOrCancel() }
        b.btnSave.setOnClickListener { saveOrder() }

        // 开灯/扫码期间点击输入框时提示
        b.etCode.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && (isScanning || isLightOn)) {
                b.etCode.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(b.etCode.windowToken, 0)
                MfUi.toast(this, R.string.oc_tip_scan_light_blocked)
            }
        }

        initLight()

        // 扫码服务（try-catch 防闪退，与新增出库一致）
        try { bindScannerService() } catch (e: Exception) { com.mefront.mfPda.util.Log.d("Scanner", "bind failed: ${e.message}") }
        try { registerScanReceiver() } catch (e: Exception) { com.mefront.mfPda.util.Log.d("Scanner", "register failed: ${e.message}") }
    }

    override fun onResume() {
        super.onResume()
        // onShow 逻辑：首次显示时从 storage 恢复条码列表
        if (firstShow && savedCodes.isNotEmpty()) {
            firstShow = false
            Net.req("refundOrder/getAllCode", mapOf("orderData" to savedCodes.toString())) { err, res ->
                runOnUiThread {
                    if (err == null && res != null && res.ok) {
                        val arr = res.dataJson
                        if (arr != null) {
                            orderData.clear()
                            codesSet.clear()
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                orderData.add(item)
                                codesSet.add(item.optString("code", ""))
                            }
                            b.list.adapter?.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
        firstShow = false
    }

    override fun onDestroy() {
        stopScan()
        stopLight()
        scanChoiceDialog?.dismiss()
        scanChoiceDialog = null
        try { unbindScannerService() } catch (_: Exception) {}
        try { unregisterScanReceiver() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ── 扫码服务 ──

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
            b.btnConfirm.text = getString(R.string.oc_btn_light_off)
            b.btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.btn_red, null))
        } else {
            Log.e("Light", "startLight failed")
            MfUi.toast(this, "开灯失败")
        }
    }

    private fun stopLight() {
        LedUtil.turnOff()
        isLightOn = false
        b.btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.btn_green, null))
        refreshConfirmOrCancelBtn()
    }

    private fun refreshConfirmOrCancelBtn() {
        if (!b.etCode.text.isNullOrBlank()) {
            b.btnConfirm.text = getString(R.string.oc_btn_confirm)
        } else {
            b.btnConfirm.text = getString(R.string.oc_btn_light_on)
        }
    }

    // ── 扫码服务 ──

    private val scanConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            scanInterface = IScanInterface.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) { scanInterface = null }
    }

    private fun bindScannerService() {
        val intent = Intent().apply {
            setPackage("com.sunmi.scanner")
            action = "com.sunmi.scanner.IScanInterface"
        }
        startService(intent)
        bindService(intent, scanConn, Context.BIND_AUTO_CREATE)
    }

    private fun unbindScannerService() {
        try { unbindService(scanConn) } catch (_: Exception) {}
        scanInterface = null
    }

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

    private fun onScanClick() {
        if (isScanning) { stopScan(); return }
        if (isLightOn) { MfUi.toast(this, R.string.oc_tip_scan_light_blocked); return }
        startScan()
    }

    private fun startScan() {
        if (scanInterface == null) {
            MfUi.toast(this, "扫码服务连接中，请稍后重试")
            return
        }
        try {
            scanInterface?.scan()
        } catch (e: RemoteException) {
            MfUi.toast(this, "扫码启动失败: ${e.message}")
            return
        }
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
        b.btnScan.text = getString(R.string.oc_btn_scan)
        b.btnScan.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(R.color.btn_green, null))
        refreshConfirmOrCancelBtn()
        try { scanInterface?.stop() } catch (_: RemoteException) {}
    }

    inner class ScanReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED" != intent.action) return
            var code = intent.getStringExtra("data") ?: ""
            // 有些设备通过 source_byte 返回数据
            if (code.isEmpty()) {
                val arr = intent.getByteArrayExtra("source_byte")
                if (arr != null && arr.isNotEmpty()) {
                    code = String(arr, Charsets.UTF_8)
                }
            }
            code = code.replace("http://yzj.mefront.com/q/", "")
            if (code.isEmpty()) return
            lastScanBroadcastTime = System.currentTimeMillis()
            // 收到广播说明激光正常，重置5秒超时
            scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
            scanTimeoutHandler.postDelayed(scanTimeoutRunnable, 5000)
            if (!processingCodes.add(code)) return
            addByCode(code)
            // 连续扫码：无论API结果如何，延迟300ms重新scan保持激光常亮
            if (isScanning && scanInterface != null) {
                b.root.postDelayed({
                    if (isScanning && scanInterface != null) {
                        try { scanInterface?.scan() } catch (_: RemoteException) {}
                    }
                }, 300)
            }
        }
    }

    // ── 业务逻辑 ──

    private fun onConfirmOrCancel() {
        if (isScanning) return
        if (isLightOn) {
            stopLight()
            return
        }
        if (b.etCode.text.isNullOrBlank()) {
            startLight()
            return
        }
        val code = b.etCode.text.toString().trim().replace("http://yzj.mefront.com/q/", "")
        if (code.isEmpty()) return
        addByCode(code)
    }

    private fun addByCode(code: String) {
        if (codesSet.contains(code)) {
            confirmDialog(getString(R.string.rc_code_repeat))
            processingCodes.remove(code)
            return
        }
        Net.req("refundOrder/getcode", mapOf("goodno" to code)) { err, res ->
            runOnUiThread {
                processingCodes.remove(code)
                if (err != null || res == null) {
                    MfUi.toast(this, R.string.network_error)
                    return@runOnUiThread
                }
                handleCodeResult(res, code)
            }
        }
    }

    private fun handleCodeResult(res: ApiResponse, code: String) {
        when (res.result?.toString()) {
            "1" -> {
                val data = res.dataObject
                if (data != null) {
                    orderData.add(data)
                    codesSet.add(code)
                    savedCodes.add(code)
                    b.etCode.setText("")
                    b.list.adapter?.notifyDataSetChanged()
                    b.list.smoothScrollToPosition(orderData.size - 1)
                }
                // 连续扫码在ScanReceiver里统一处理，这里只处理数据
            }
            "2" -> confirmDialog(getString(R.string.rc_code_unavail))
            "3" -> confirmDialog(getString(R.string.rc_code_not_received))
            else -> confirmDialog(getString(R.string.oc_tip_code_empty))
        }
    }

    // ★ 扫码提示对话框（防物理按键击穿，与新增出库一致）
    private var scanChoiceDialog: android.app.Dialog? = null

    private fun confirmDialog(msg: String) {
        if (isFinishing || isDestroyed) return
        scanChoiceDialog?.dismiss()

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null, false)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_content)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        tvTitle.text = msg
        btnConfirm.text = getString(R.string.oc_btn_continue_scan)
        btnCancel.text = getString(R.string.oc_btn_stop_scan_choice)
        tvContent.visibility = View.GONE

        // 防物理扫码键击穿
        btnConfirm.isFocusable = false
        btnConfirm.isFocusableInTouchMode = false
        btnCancel.isFocusable = false
        btnCancel.isFocusableInTouchMode = false
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) true else false
        }

        val dialog = android.app.Dialog(this, R.style.MfDialog)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) true else false
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            scanChoiceDialog = null
            // 继续扫描：重新触发激光
            if (isScanning && scanInterface != null) {
                try { scanInterface?.scan() } catch (_: RemoteException) {}
            }
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
            scanChoiceDialog = null
            stopScan()
        }

        scanChoiceDialog = dialog
        dialog.show()
    }

    private fun saveOrder() {
        if (isScanning || isLightOn) { MfUi.toast(this, R.string.oc_tip_scan_light_blocked); return }
        if (orderData.isEmpty()) { MfUi.toast(this, R.string.rc_code_empty); return }
        val codes = orderData.map { it.optString("code", "") }
        val remark = b.etRemark.text.toString().ifEmpty { "无说明" }
        MfUi.showLoading(this, getString(R.string.rl_loading))
        Net.req("refundOrder/saveorder", mapOf("orderData" to org.json.JSONArray(codes).toString(), "remark" to remark)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.network_error); return@runOnUiThread }
                when (res.result?.toString()) {
                    "1" -> {
                        savedCodes.clear()
                        val msg = res.raw.optString("Msg", "")
                        if (msg.isNotEmpty()) {
                            MfUi.confirm(this, msg, onConfirm = {
                                startActivity(Intent(this, RefundListActivity::class.java))
                                finish()
                            })
                        } else {
                            MfUi.toast(this, R.string.rc_save_ok)
                            startActivity(Intent(this, RefundListActivity::class.java))
                            finish()
                        }
                    }
                    "2" -> MfUi.toast(this, "请输入防串码！")
                    else -> MfUi.toast(this, res.raw.optString("Msg", getString(R.string.rc_save_fail)))
                }
            }
        }
    }

    // ── 列表适配器 ──

    private inner class Adapter(val data: MutableList<JSONObject>) : RecyclerView.Adapter<Adapter.VH>() {
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
            h.tvName.text = o.optString("NAME", o.optString("Name", ""))
            h.tvSpec.text = o.optString("Spec", "")
            h.tvSpec2.text = o.optString("Spec2", "")
            h.btnDel.setOnClickListener {
                data.removeAt(position)
                codesSet.remove(o.optString("code", ""))
                notifyDataSetChanged()
            }
        }
        override fun getItemCount(): Int = data.size
    }
}

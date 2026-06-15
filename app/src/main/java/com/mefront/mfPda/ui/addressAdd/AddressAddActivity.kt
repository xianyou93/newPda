package com.mefront.mfPda.ui.addressAdd

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.databinding.ActivityAddressAddBinding
import com.mefront.mfPda.net.ApiResponse
import com.mefront.mfPda.net.Net
import com.mefront.mfPda.util.DateUtil
import com.mefront.mfPda.widget.MfUi
import org.json.JSONObject

/**
 * 新增/编辑客户。
 * - 新增：Code 显示"系统自动生成"
 * - 编辑：携带 Code 进入，onLoad 调 customapi/getcustom 拉数据预填
 * - 校验：4 个必填项 + 手机号格式
 * - 提交：customapi/savecustom，成功后退回
 */
class AddressAddActivity : BaseActivity() {

    private lateinit var b: ActivityAddressAddBinding
    private var saveType: String = "Add"     // Add / Edit
    private var code: String = "系统自动生成" // 新增模式默认值，与小程序一致；编辑模式为后端返回

    private var name: String = ""
    private var address: String = ""
    private var legalPerson: String = ""
    private var phone: String = ""

    // 隐藏字段（PDA无UI输入，与小程序保持一致，编辑时从后端加载回传）
    private var comment: String = ""
    private var bizManager: String = ""
    private var legalPersonIDNumber: String = ""
    private var zipCode: String = ""
    private var accountName: String = ""
    private var depositBank: String = ""
    private var accountNumber: String = ""
    private var corpCategoryID: String = "00000000-0000-0000-0000-000000000000"

    override fun title(): CharSequence = "客户维护"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddressAddBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 编辑模式：onLoad 拉数据
        val editCode = intent.getStringExtra("Code")
        if (!editCode.isNullOrBlank()) {
            saveType = "Edit"
            code = editCode
            loadCustom(code)
        } else {
            b.tvCode.text = getString(R.string.aa_corp_code_auto)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { collect() }
        }
        b.etName.addTextChangedListener(watcher)
        b.etAddress.addTextChangedListener(watcher)
        b.etLegal.addTextChangedListener(watcher)
        b.etPhone.addTextChangedListener(watcher)
        b.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && phone.isNotEmpty() && !DateUtil.checkPhone(phone)) {
                MfUi.toast(this, R.string.aa_phone_err)
            }
        }

        b.btnSave.setOnClickListener { save() }
    }

    private fun collect() {
        name = b.etName.text.toString().trim()
        address = b.etAddress.text.toString().trim()
        legalPerson = b.etLegal.text.toString().trim()
        phone = b.etPhone.text.toString().trim()
    }

    private fun loadCustom(code: String) {
        MfUi.showLoading(this)
        Net.req("customapi/getcustom", mapOf("Code" to code)) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.aa_data_load_fail); return@runOnUiThread }
                if (!res.ok) { MfUi.toast(this, R.string.aa_data_load_fail); return@runOnUiThread }
                // 后端 getcustom 返回 data 为对象（Custom.get(0)），优先用 dataObject，兼容 dataJson 数组
                val o = res.dataObject ?: res.dataJson?.optJSONObject(0) ?: JSONObject()
                this.code = o.optString("Code", code)
                b.tvCode.text = this.code
                b.etName.setText(o.optString("Name", ""))
                b.etAddress.setText(o.optString("Address", ""))
                b.etLegal.setText(o.optString("LegalPerson", ""))
                b.etPhone.setText(o.optString("BizManagerTelePhone", ""))
                // 隐藏字段：编辑时从后端加载，保存时回传
                comment = o.optString("Comment", "")
                bizManager = o.optString("BizManager", "")
                legalPersonIDNumber = o.optString("LegalPersonIDNumber", "")
                zipCode = o.optString("ZipCode", "")
                accountName = o.optString("AccountName", "")
                depositBank = o.optString("DepositBank", "")
                accountNumber = o.optString("AccountNumber", "")
                corpCategoryID = o.optString("CorpCategoryID", "00000000-0000-0000-0000-000000000000")
            }
        }
    }

    private fun save() {
        collect()
        if (name.isBlank() || address.isBlank() || legalPerson.isBlank() || phone.isBlank()) {
            MfUi.toast(this, R.string.aa_required)
            return
        }
        if (!DateUtil.checkPhone(phone)) {
            MfUi.toast(this, R.string.aa_phone_err)
            return
        }
        MfUi.showLoading(this)
        Net.req("customapi/savecustom", mapOf(
            "SaveType" to saveType,
            "Code" to code,
            "Name" to name,
            "Address" to address,
            "Comment" to comment,
            "BizManager" to bizManager,
            "BizManagerTelePhone" to phone,
            "LegalPerson" to legalPerson,
            "LegalPersonIDNumber" to legalPersonIDNumber,
            "ZipCode" to zipCode,
            "AccountName" to accountName,
            "DepositBank" to depositBank,
            "AccountNumber" to accountNumber,
            "CorpCategoryID" to corpCategoryID
        )) { err, res ->
            runOnUiThread {
                MfUi.hideLoading()
                if (err != null || res == null) { MfUi.toast(this, R.string.aa_save_fail); return@runOnUiThread }
                if (res.ok) {
                    finish()
                } else {
                    MfUi.toast(this, R.string.aa_save_fail)
                }
            }
        }
    }
}

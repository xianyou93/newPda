package com.mefront.mfPda.widget

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.mefront.mfPda.R
import com.mefront.mfPda.util.Log

/** 通用 UI 工具。Toast / Dialog / Loading 一站式。 */
object MfUi {

    fun toast(ctx: Context, msg: CharSequence?) {
        if (msg.isNullOrBlank()) return
        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    fun toast(ctx: Context, resId: Int) {
        toast(ctx, ctx.getString(resId))
    }

    /**
     * 自定义确认对话框（圆角、主题色按钮、居中排版）。
     */
    fun confirm(
        act: Activity,
        title: String,
        content: String? = null,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null,
        confirmText: String = "确定",
        cancelText: String = "取消"
    ) {
        if (act.isFinishing || act.isDestroyed) return
        val view = LayoutInflater.from(act).inflate(R.layout.dialog_confirm, null, false)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_content)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        tvTitle.text = title
        btnConfirm.text = confirmText
        btnCancel.text = cancelText

        if (!content.isNullOrBlank()) {
            tvContent.text = content
            tvContent.visibility = View.VISIBLE
        } else {
            tvContent.visibility = View.GONE
        }

        val dialog = Dialog(act, R.style.MfDialog)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        dialog.show()
    }

    fun confirmRes(
        act: Activity,
        titleRes: Int,
        contentRes: Int? = null,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) = confirm(act, act.getString(titleRes),
        contentRes?.let { act.getString(it) }, onConfirm, onCancel)

    /**
     * 自定义提示对话框（单按钮）。
     */
    fun alert(act: Activity, title: String, content: String, onDismiss: (() -> Unit)? = null) {
        if (act.isFinishing || act.isDestroyed) return
        val view = LayoutInflater.from(act).inflate(R.layout.dialog_confirm, null, false)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_content)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        tvTitle.text = title
        tvContent.text = content
        tvContent.visibility = View.VISIBLE
        btnConfirm.text = "我知道了"
        btnCancel.visibility = View.GONE
        // 隐藏分割线（cancel 隐藏后，confirm 占满宽度）
        view.findViewById<View>(R.id.btn_cancel)?.let {
            it.visibility = View.GONE
        }

        val dialog = Dialog(act, R.style.MfDialog)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        dialog.show()
    }

    private var loadingDialog: AlertDialog? = null
    /** 对应 wx.showLoading/hideLoading 简化为"带文本的加载对话框"。 */
    fun showLoading(act: Activity, msg: String? = null) {
        try {
            if (act.isFinishing || act.isDestroyed) return
            if (loadingDialog?.isShowing == true) return
            val view = LayoutInflater.from(act).inflate(R.layout.dialog_loading, null, false)
            view.findViewById<TextView>(R.id.tv_msg).text = msg ?: "加载中…"
            loadingDialog = AlertDialog.Builder(act, R.style.MfDialog)
                .setView(view)
                .setCancelable(false)
                .create()
            loadingDialog?.show()
        } catch (e: Throwable) {
            Log.e("MfUi", "showLoading fail: ${e.message}")
        }
    }

    fun hideLoading() {
        try {
            loadingDialog?.dismiss()
            loadingDialog = null
        } catch (e: Throwable) { }
    }
}

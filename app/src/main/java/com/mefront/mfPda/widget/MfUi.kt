package com.mefront.mfPda.widget

import android.app.Activity
import android.app.AlertDialog
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
     * 对应 wx.showModal(title, content, success)。
     * 取消时 cancel 回调可为 null。
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
        AlertDialog.Builder(act, R.style.MfDialog)
            .setTitle(title)
            .setMessage(content ?: "")
            .setPositiveButton(confirmText) { d, _ ->
                d.dismiss()
                onConfirm()
            }
            .setNegativeButton(cancelText) { d, _ ->
                d.dismiss()
                onCancel?.invoke()
            }
            .setCancelable(true)
            .show()
    }

    fun confirmRes(
        act: Activity,
        titleRes: Int,
        contentRes: Int? = null,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) = confirm(act, act.getString(titleRes),
        contentRes?.let { act.getString(it) }, onConfirm, onCancel)

    fun alert(act: Activity, title: String, content: String, onDismiss: (() -> Unit)? = null) {
        if (act.isFinishing || act.isDestroyed) return
        AlertDialog.Builder(act, R.style.MfDialog)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("确定") { d, _ -> d.dismiss(); onDismiss?.invoke() }
            .setCancelable(true)
            .show()
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

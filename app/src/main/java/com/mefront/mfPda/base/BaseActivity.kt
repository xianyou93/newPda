package com.mefront.mfPda.base

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.mefront.mfPda.R
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.ui.login.LoginActivity
import com.mefront.mfPda.util.Log

/**
 * 公共 Activity 基类：默认 toolbar、返回键、未登录拦截。
 *
 * 全 app 拦截所有物理按键（只放行返回键和音量键），防止商米物理扫码键
 * 发送的按键事件传到 UI 层导致导航跳转。物理扫码走硬件通路（激光→广播），
 * 不需要按键事件配合。
 */
abstract class BaseActivity : AppCompatActivity() {

    protected open val requireLogin: Boolean = true

    protected open fun title(): CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        trySetupToolbar()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        trySetupToolbar()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        trySetupToolbar()
    }

    private fun trySetupToolbar() {
        if (title() != null) {
            findViewById<Toolbar?>(R.id.toolbar)?.let { tb ->
                setSupportActionBar(tb)
                supportActionBar?.title = title()
                tb.setNavigationIcon(R.drawable.ic_back)
                tb.setNavigationOnClickListener { finish() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (requireLogin && SpCache.getCuserInfo() == null) {
            Log.w("Base", "${this.javaClass.simpleName} 未登录，跳转登录页")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ── 全局物理按键拦截 ──
    // 商米 V3PLUS 两侧物理扫码键会发送按键事件，不拦截就会传到 UI 层触发跳转。
    // 物理扫码自走硬件路径（激光→广播），不需按键事件配合，全部消费掉。
    // 放行返回键（BACK）和音量键（VOLUME_UP/DOWN）。
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val kc = event.keyCode
        if (kc == KeyEvent.KEYCODE_BACK ||
            kc == KeyEvent.KEYCODE_VOLUME_UP ||
            kc == KeyEvent.KEYCODE_VOLUME_DOWN ||
            kc == KeyEvent.KEYCODE_DEL) {
            return super.dispatchKeyEvent(event)
        }
        return true
    }
}

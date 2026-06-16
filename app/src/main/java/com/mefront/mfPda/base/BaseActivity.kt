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
 * — 全局 DPAD 按键拦截 —
 * 商米 V3PLUS 两侧物理扫码键会发送 DPAD 系列按键事件，本类统一拦截所有 DPAD 按键
 * 使其不传到 UI 层，防止误触导航。子类可重写 [onDpadCenterEvent] 处理扫码逻辑。
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

    // ── 全局 DPAD 按键拦截 ──
    // 商米物理扫码键发送 DPAD_CENTER/DOWN/UP/LEFT/RIGHT，一律拦截不传到 UI 层

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val kc = event.keyCode
        if (kc == KeyEvent.KEYCODE_DPAD_CENTER ||
            kc == KeyEvent.KEYCODE_DPAD_DOWN ||
            kc == KeyEvent.KEYCODE_DPAD_UP ||
            kc == KeyEvent.KEYCODE_DPAD_LEFT ||
            kc == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // DPAD_CENTER 通知子类处理扫码（如 OrderConfirmActivity 调 sendKeyEvent）
            if (kc == KeyEvent.KEYCODE_DPAD_CENTER) {
                onDpadCenterEvent(event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** 子类可重写此方法处理物理扫码键（DPAD_CENTER），默认空实现。 */
    protected open fun onDpadCenterEvent(event: KeyEvent) {}
}

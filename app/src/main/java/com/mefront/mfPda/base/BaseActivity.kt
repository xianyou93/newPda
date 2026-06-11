package com.mefront.mfPda.base

import android.content.Intent
import android.os.Bundle
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
 */
abstract class BaseActivity : AppCompatActivity() {

    protected open val requireLogin: Boolean = true

    protected open fun title(): CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // toolbar 设置移到了 setContentView 重载中，
        // 确保在布局加载完成后找 toolbar
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
}

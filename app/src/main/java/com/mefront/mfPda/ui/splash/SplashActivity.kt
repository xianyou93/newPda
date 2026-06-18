package com.mefront.mfPda.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.ui.login.LoginActivity
import com.mefront.mfPda.ui.mime.MimeActivity

/**
 * 启动展示页。
 *
 * 逻辑：
 * 1. 检查本地缓存 CuserInfo（SharedPreferences 持久化）
 * 2. 有有效缓存 → 直接进首页（自动登录）
 * 3. 无缓存 → 跳登录页
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不设 setContentView，windowBackground 已是展示图

        Handler(Looper.getMainLooper()).postDelayed({
            val cuser = SpCache.getCuserInfo()
            val target = if (cuser != null && cuser.wxopenid.isNotBlank() && cuser.logincode.isNotBlank()) {
                Intent(this, MimeActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }

    override fun onBackPressed() {
        // 展示页期间禁用返回键
    }
}

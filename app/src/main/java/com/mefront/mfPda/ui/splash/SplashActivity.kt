package com.mefront.mfPda.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.mefront.mfPda.ui.login.LoginActivity

/**
 * 启动展示页：Theme.Splash 的 windowBackground 直接显示展示图，
 * 无需 setContentView，冷启动即展示，无白屏闪烁。
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不设 setContentView，windowBackground 已是展示图

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }

    override fun onBackPressed() {
        // 展示页期间禁用返回键
    }
}

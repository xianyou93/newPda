package com.mefront.mfPda.ui.mime

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.mefront.mfPda.R
import com.mefront.mfPda.base.BaseActivity
import com.mefront.mfPda.data.SpCache
import com.mefront.mfPda.databinding.ActivityMimeBinding
import com.mefront.mfPda.ui.addressManager.AddressManagerActivity
import com.mefront.mfPda.ui.manager.ManagerActivity
import com.mefront.mfPda.ui.orderConfirm.OrderConfirmActivity
import com.mefront.mfPda.ui.ordertotal.OrdertotalActivity
import com.mefront.mfPda.util.Log

/**
 * 菜单主页。
 *
 * 重构点：
 * - 3 个接口（收货单/合同/付款确认计数）全部删掉——需求文档明确不移植。
 * - showFlag 0→1 的"加载"判断：原 wx 是等 3 个接口成功；PDA 端只校验本地 CuserInfo 缓存是否有效（无效 → 跳登录页），无网络请求。
 * - 顶部用户信息区 + 4 个菜单 + 4 个菜单项的 catchtap 点击跳转。
 * - 头像用系统默认占位 icon_avatar，昵称 = loginname + "/" + logincode。
 * - 整个顶部区域可点击 → 跳 ManagerActivity。
 */
class MimeActivity : BaseActivity() {

    private lateinit var b: ActivityMimeBinding

    override fun title(): CharSequence = getString(R.string.app_name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMimeBinding.inflate(layoutInflater)
        setContentView(b.root)
        // BaseActivity 已经 setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)   // 主页无返回

        // 仅 4 个菜单 + 顶部用户信息区
        b.topUser.setOnClickListener {
            startActivity(Intent(this, ManagerActivity::class.java))
        }
        b.menuOutstock.setOnClickListener {
            startActivity(Intent(this, OrdertotalActivity::class.java).putExtra("id", 0))
        }
        b.menuNew.setOnClickListener {
            startActivity(Intent(this, OrderConfirmActivity::class.java).putExtra("type", "1"))
        }
        b.menuCustomer.setOnClickListener {
            startActivity(Intent(this, AddressManagerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 校验本地缓存有效性（替代原 wx 3 个接口的 showFlag 0→1 流程）
        val u = SpCache.getCuserInfo()
        if (u == null) {
            Log.w("Mime", "CuserInfo 为空，已跳登录页（BaseActivity 处理）")
            return
        }
        b.tvNick.text = "${u.loginname}/${u.logincode}"
        b.loadingBox.root.visibility = View.GONE
        b.menuList.visibility = View.VISIBLE
    }
}

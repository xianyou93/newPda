package com.mefront.mfPda.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * 闪光灯控制工具类。
 *
 * 方案一（优先）：写 sysfs 文件直接控制硬件 LED（参考 Sunmi 官方 EaiUtil），
 * 适用于 T2mini 等部分设备。
 *
 * 方案二（兜底）：CameraManager.setTorchMode() + 重试，
 * 适用于 V3PLUS 等无 sysfs LED 路径的设备。
 *
 * 两个方案都不依赖相机预览，只控制闪光灯开关。
 */
object LedUtil {

    // ── 方案一：sysfs 路径 ──

    private val LED_PATHS = arrayOf(
        "/sys/class/leds/led_cam/brightness",
        "/sys/class/leds/led_flash/brightness",
        "/sys/class/leds/torch/brightness",
        "/sys/class/leds/led:torch_0/brightness",
        "/sys/class/leds/led:flash_0/brightness",
        "/sys/class/leds/flashlight/brightness",
        "/sys/devices/platform/soc/*/leds/led:flash_0/brightness"
    )

    private var ledPath: String? = null

    /** 查找设备上可用的 sysfs LED 路径 */
    fun findLedPath(): String? {
        ledPath?.let { return it }
        for (path in LED_PATHS) {
            // 通配符路径需要特殊处理
            val resolved = path.replace("*", "")
            if (File(resolved).exists()) {
                ledPath = resolved
                Log.d("LedUtil", "sysfs LED found: $resolved")
                return resolved
            }
            if (File(path).exists()) {
                ledPath = path
                Log.d("LedUtil", "sysfs LED found: $path")
                return path
            }
        }
        Log.e("LedUtil", "No sysfs LED found")
        return null
    }

    /** 方案一：通过 sysfs 开灯 */
    private fun turnOnSysfs(): Boolean {
        val path = findLedPath() ?: return false
        return writeSysfs(path, "255")  // 全亮
    }

    /** 方案一：通过 sysfs 关灯 */
    private fun turnOffSysfs(): Boolean {
        val path = ledPath ?: return false
        return writeSysfs(path, "0")
    }

    private fun writeSysfs(path: String, value: String): Boolean {
        try {
            FileOutputStream(File(path)).use { fos ->
                fos.write(value.toByteArray())
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e("LedUtil", "write sysfs fail: ${e.message}")
            return false
        }
    }

    // ── 方案二：CameraManager ──

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    /** 初始化相机闪光灯（需要在 UI 线程调用） */
    fun initCamera(context: Context) {
        if (cameraId != null) return
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            for (id in cameraManager!!.cameraIdList) {
                val chars = cameraManager!!.getCameraCharacteristics(id)
                val flashAvailable = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flashAvailable == true) {
                    cameraId = id
                    Log.d("LedUtil", "Camera torch found: $id")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("LedUtil", "initCamera fail: ${e.message}")
        }
    }

    /** 检查 CAMERA 权限是否已授予 */
    fun hasCameraPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /** 方案二：通过 CameraManager 开灯 */
    private fun turnOnCamera(): Boolean {
        if (cameraId == null || cameraManager == null) return false
        return try {
            cameraManager?.setTorchMode(cameraId!!, true)
            true
        } catch (e: Exception) {
            Log.e("LedUtil", "Camera torch on fail: ${e.message}")
            false
        }
    }

    /** 方案二：通过 CameraManager 关灯 */
    private fun turnOffCamera(): Boolean {
        if (cameraId == null || cameraManager == null) return false
        return try {
            cameraManager?.setTorchMode(cameraId!!, false)
            true
        } catch (e: Exception) {
            Log.e("LedUtil", "Camera torch off fail: ${e.message}")
            false
        }
    }

    // ── 外部接口 ──

    /** 开灯（sysfs优先 → CameraManager兜底，自动重试1次） */
    fun turnOn(context: Context? = null): Boolean {
        // 方案一：sysfs
        if (turnOnSysfs()) return true

        // 方案二：CameraManager
        if (context != null && cameraId == null) initCamera(context)
        if (turnOnCamera()) return true

        // 重试一次 CameraManager
        android.os.SystemClock.sleep(300)
        if (context != null) initCamera(context)
        return turnOnCamera()
    }

    /** 关灯 */
    fun turnOff(): Boolean {
        if (turnOffSysfs()) return true
        return turnOffCamera()
    }
}

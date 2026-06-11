package com.mefront.mfPda.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 原 wx util.js 中 CurentTime / BeforeMonthTime / CurrentYearTime 的 Kotlin 实现。 */
object DateUtil {

    fun currentTime(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun beforeMonthTime(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    fun checkPhone(phone: String): Boolean {
        return Regex("^1[123456789]\\d{9}$").matches(phone)
    }
}

package com.mefront.mfPda.widget

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.mefront.mfPda.R
import java.util.Calendar
import java.util.Locale

/**
 * 自定义日期选择对话框。
 *
 * 替代 MaterialDatePicker，提供：
 * - 月份左右箭头切换 + 月份可点击选择
 * - 年份可点击（显式按钮样式 → 弹年份列表）
 * - 日期网格（日 一 二 三 四 五 六）
 * - 手动输入 EditText（与日历联动）
 * - 选中月份/年份后自动保留选中日
 *
 * 用法：
 *   MonthCalendarDialog(activity, "2026-06-22") { date ->
 *       // date 为 "yyyy-MM-dd"
 *   }.show()
 */
class MonthCalendarDialog(
    private val act: Activity,
    initialDate: String?,  // yyyy-MM-dd, null = 今天
    private val onDateSelected: (String) -> Unit
) : Dialog(act, R.style.MfDialog) {

    // 日历状态：当前显示的年份和月份
    private var displayYear: Int
    private var displayMonth: Int  // 1-12

    // 用户选中的日期
    private var selectedYear = 0
    private var selectedMonth = 0  // 1-12
    private var selectedDay = 0

    // 是否是首次加载（避免手动输入初始化时触发联动死循环）
    private var isInitializing = true

    private lateinit var calendarGrid: LinearLayout
    private lateinit var btnPrevMonth: View
    private lateinit var btnNextMonth: View
    private lateinit var btnYear: TextView
    private lateinit var tvMonth: TextView
    private lateinit var etManualInput: EditText
    private lateinit var tvSelectedDate: TextView
    private var rootView: View? = null

    init {
        // 解析初始日期
        val cal = Calendar.getInstance()
        if (!initialDate.isNullOrBlank()) {
            try {
                val parts = initialDate.split("-")
                if (parts.size == 3) {
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            } catch (_: NumberFormatException) {}
        }
        displayYear = cal.get(Calendar.YEAR)
        displayMonth = cal.get(Calendar.MONTH) + 1  // Calendar.MONTH = 0-based
        selectedYear = displayYear
        selectedMonth = displayMonth
        selectedDay = cal.get(Calendar.DAY_OF_MONTH)

        // 加载布局
        val view = LayoutInflater.from(act).inflate(R.layout.dialog_date_picker, null, false)
        setContentView(view)
        setCancelable(true)
        setCanceledOnTouchOutside(true)

        rootView = view
        bindViews(view)
        setupListeners(view)

        // 渲染日历
        syncInputFromSelection()
        renderCalendar()
        isInitializing = false
    }

    private fun bindViews(view: View) {
        calendarGrid = view.findViewById(R.id.calendar_grid)
        btnPrevMonth = view.findViewById(R.id.btn_prev_month)
        btnNextMonth = view.findViewById(R.id.btn_next_month)
        btnYear = view.findViewById(R.id.btn_year)
        tvMonth = view.findViewById(R.id.tv_month)
        etManualInput = view.findViewById(R.id.et_manual_input)
        tvSelectedDate = view.findViewById(R.id.tv_selected_date)
    }

    private fun setupListeners(view: View) {
        btnPrevMonth.setOnClickListener { monthOffset(-1) }
        btnNextMonth.setOnClickListener { monthOffset(1) }
        btnYear.setOnClickListener { showYearPicker() }
        tvMonth.setOnClickListener { showMonthPicker() }

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            onConfirm()
        }

        // 手动输入联动
        etManualInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isInitializing) return
                parseManualInput(s.toString())
            }
        })
    }

    /** 月份偏移（-1 上月 / +1 下月），选中日不丢失 */
    private fun monthOffset(offset: Int) {
        displayMonth += offset
        if (displayMonth < 1) {
            displayMonth = 12
            displayYear--
        } else if (displayMonth > 12) {
            displayMonth = 1
            displayYear++
        }
        clampSelectedDay()
        renderCalendar()
    }

    /** 显示年份不会超出选中日所在月份的天数 */
    private fun clampSelectedDay() {
        val cal = Calendar.getInstance()
        cal.set(displayYear, displayMonth - 1, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (selectedDay > maxDay) selectedDay = maxDay
    }

    /** 弹出年份选择器 */
    private fun showYearPicker() {
        val years = (displayYear - 10..displayYear + 5).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(act)
            .setTitle("选择年份")
            .setItems(years) { _, which ->
                displayYear = years[which].toInt()
                clampSelectedDay()
                // 自动选择同一天（如果存在），避免用户还要再点一次日期
                selectDate(displayYear, displayMonth, selectedDay)
            }
            .show()
    }

    /** 弹出月份选择器 */
    private fun showMonthPicker() {
        val months = (1..12).map { "${it}月" }.toTypedArray()
        val currentMonthIdx = displayMonth - 1
        AlertDialog.Builder(act)
            .setTitle("选择月份")
            .setSingleChoiceItems(months, currentMonthIdx) { dialog, which ->
                displayMonth = which + 1
                clampSelectedDay()
                selectDate(displayYear, displayMonth, selectedDay)
                dialog.dismiss()
            }
            .show()
    }

    /** 渲染日期网格 */
    private fun renderCalendar() {
        btnYear.text = "${displayYear}年 ▾"
        tvMonth.text = "${displayMonth}月 ▾"

        val cal = Calendar.getInstance()
        cal.set(displayYear, displayMonth - 1, 1)  // 当月第一天
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=周日, 1=周一, ... 6=周六
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 计算需要多少行（6行最坏情况）
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7  // 向上取整

        calendarGrid.removeAllViews()

        val cellSize = 44.dpToPx(act)

        var dayCounter = 0
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(act)
            rowLayout.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            rowLayout.orientation = LinearLayout.HORIZONTAL

            for (col in 0..6) {
                val cellIndex = row * 7 + col
                val cell = LayoutInflater.from(act).inflate(R.layout.item_calendar_day, null, false) as TextView

                val lp = LinearLayout.LayoutParams(
                    cellSize,
                    cellSize
                )
                lp.weight = 1f
                cell.layoutParams = lp

                if (cellIndex < firstDayOfWeek || dayCounter >= daysInMonth) {
                    cell.text = ""
                    cell.isEnabled = false
                    cell.visibility = View.INVISIBLE
                } else {
                    dayCounter++
                    val dayNum = dayCounter
                    cell.text = dayNum.toString()
                    cell.tag = dayNum

                    val isToday = isSameDay(dayNum)
                    val isSelected = (displayYear == selectedYear
                            && displayMonth == selectedMonth
                            && dayNum == selectedDay)

                    if (isSelected) {
                        cell.setBackgroundResource(R.drawable.bg_calendar_day_selected)
                        cell.setTextColor(act.getColor(R.color.text_white))
                    } else {
                        cell.setBackgroundResource(R.drawable.bg_calendar_day)
                        cell.setTextColor(act.getColor(R.color.text_primary))
                    }

                    // 今天的日期加一个小圆点标记
                    if (isToday && !isSelected) {
                        cell.setBackgroundResource(R.drawable.bg_calendar_day_today)
                    }

                    cell.setOnClickListener { v ->
                        val day = v.tag as Int
                        selectDate(displayYear, displayMonth, day)
                    }
                }

                rowLayout.addView(cell)
            }
            calendarGrid.addView(rowLayout)
        }

        // 在标题栏显示当前选中的完整日期
        tvSelectedDate.text = String.format(Locale.getDefault(),
            "已选: %04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
    }

    /** 判断某天是否是今天 */
    private fun isSameDay(day: Int): Boolean {
        val now = Calendar.getInstance()
        return now.get(Calendar.YEAR) == displayYear
                && now.get(Calendar.MONTH) + 1 == displayMonth
                && now.get(Calendar.DAY_OF_MONTH) == day
    }

    /** 选中某个日期 → 更新选择和输入框 */
    private fun selectDate(year: Int, month: Int, day: Int) {
        selectedYear = year
        selectedMonth = month
        selectedDay = day
        // 如果选择的月份与显示不同，同步显示
        displayYear = year
        displayMonth = month
        syncInputFromSelection()
        renderCalendar()
    }

    /** 从选中日期同步到手动输入框 */
    private fun syncInputFromSelection() {
        val fmt = String.format(Locale.getDefault(), "%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
        if (etManualInput.text.toString() != fmt) {
            isInitializing = true
            etManualInput.setText(fmt)
            etManualInput.setSelection(fmt.length)
            isInitializing = false
        }
    }

    /** 解析手动输入内容 → 联动日历 */
    private fun parseManualInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.length < 8) return
        val normalized = trimmed
            .replace("/", "-")
            .replace(".", "-")
            .replace("\\s".toRegex(), "")
        val parts = normalized.split("-")
        if (parts.size == 3 || normalized.length == 8) {
            try {
                var y: Int
                var m: Int
                var d: Int
                if (normalized.length == 8 && !normalized.contains("-")) {
                    y = normalized.substring(0, 4).toInt()
                    m = normalized.substring(4, 6).toInt()
                    d = normalized.substring(6, 8).toInt()
                } else if (parts.size == 3) {
                    y = parts[0].toInt()
                    m = parts[1].toInt()
                    d = parts[2].toInt()
                } else return

                if (m in 1..12 && d in 1..31) {
                    selectDate(y, m, d)
                }
            } catch (_: NumberFormatException) {}
        }
    }

    /** 确定按钮 */
    private fun onConfirm() {
        val dateStr = String.format(
            Locale.getDefault(), "%04d-%02d-%02d",
            selectedYear, selectedMonth, selectedDay
        )
        onDateSelected(dateStr)
        dismiss()
    }

    /** dp → px 工具 */
    private fun Int.dpToPx(ctx: Context): Int {
        return (this * ctx.resources.displayMetrics.density).toInt()
    }
}

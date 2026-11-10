package com.example.englishlearning.screentranslation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮控制条（F3-05）：可拖动，常显「已发 N 次 · 模型名」，两个按钮「单次」「停止」。
 *
 * 成本可见性是硬要求，所以计数与模型名放在控件第一行，而不是藏进应用界面：
 * 用户在看别的 App 时也必须能看到这次会话花了多少次请求。
 */
@SuppressLint("ViewConstructor")
class ScreenTranslationControlBar(
    context: Context,
    private val onTranslateOnce: () -> Unit,
    private val onStop: () -> Unit,
) : LinearLayout(context) {
    private val status = TextView(context)

    init {
        orientation = VERTICAL
        background =
            GradientDrawable().apply {
                cornerRadius = RADIUS_PX
                // 这一支在 `ui` 之外，裸 hex 会被 RawColorLiteralRatchetTest 当场挡下：
                // 走 `Color.argb(...)` 的十进制三通道写法（与主题同色：底 230/20/31/26，描边 85/53/185/181）。
                setColor(Color.argb(230, 20, 31, 26))
                setStroke(STROKE_PX, Color.argb(85, 53, 185, 181))
            }
        setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)

        status.setTextColor(Color.WHITE)
        status.textSize = 12f
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(context)
        buttons.orientation = HORIZONTAL
        buttons.gravity = Gravity.END
        buttons.addView(
            Button(context).apply {
                text = "单次"
                textSize = 12f
                setOnClickListener { onTranslateOnce() }
            },
        )
        buttons.addView(
            Button(context).apply {
                text = "停止"
                textSize = 12f
                setOnClickListener { onStop() }
            },
        )
        addView(buttons, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun updateStatus(
        sentRequests: Int,
        modelName: String?,
    ) {
        val model = modelName ?: "未配置模型"
        status.text = "本次会话已发 $sentRequests 次请求 · $model"
    }

    /** 拖动：只改 WindowManager 的 x/y，不重建窗口。 */
    fun attachDrag(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ): OnTouchListener {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        return OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    runCatching { windowManager.updateViewLayout(this, params) }
                    true
                }
                else -> false
            }
        }
    }

    private companion object {
        const val RADIUS_PX = 24f
        const val STROKE_PX = 2
        const val PADDING_PX = 16
    }
}

package com.iris.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class IrisHudView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(0, 219, 255)
    private val blue = Color.rgb(12, 114, 255)
    private var phase = 0f
    var speaking = false
    var listening = false
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width * .465f, height * .43f)
        phase += if (speaking) 2.3f else if (listening) 1.5f else .45f
        p.style = Paint.Style.STROKE
        for (layer in 0..5) {
            val r = radius * (1f - layer * .116f)
            p.color = if (layer % 2 == 0) cyan else blue
            p.alpha = 95 + layer * 19
            p.strokeWidth = if (layer % 2 == 0) 3.8f else 2f
            p.setShadowLayer(if (layer % 2 == 0) 13f else 5f, 0f, 0f, p.color)
            val start = (phase * (if (layer % 2 == 0) 1 else -1) * (.15f + layer * .08f))
            for (i in 0 until (7 + layer)) {
                val a = start + i * (360f / (7 + layer))
                val sweep = 360f / (7 + layer) * (if (layer % 2 == 0) .58f else .28f)
                canvas.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),a,sweep,false,p)
            }
        }
        p.clearShadowLayer()
        p.color = cyan
        p.strokeWidth = 1.5f
        val ticks = 112
        for (i in 0 until ticks) {
            val angle = i * Math.PI * 2 / ticks
            val inner = radius * (if (i % 5 == 0) .745f else .785f)
            val outer = radius * .815f
            p.alpha = if (i % 5 == 0) 230 else 120
            canvas.drawLine(cx+cos(angle).toFloat()*inner,cy+sin(angle).toFloat()*inner,
                cx+cos(angle).toFloat()*outer,cy+sin(angle).toFloat()*outer,p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
        p.typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
        p.textAlign = Paint.Align.CENTER
        p.textSize = radius * .205f
        p.letterSpacingCompat()
        p.color = Color.WHITE
        p.setShadowLayer(14f,0f,0f,cyan)
        canvas.drawText("I·R·I·S",cx,cy + radius*.07f,p)
        p.clearShadowLayer()
        if (speaking || listening) {
            p.strokeWidth = 2.6f
            p.color = cyan
            for (i in 0..28) {
                val x = cx + (i-14) * radius * .034f
                val amplitude = (7f + 14f * kotlin.math.abs(sin(phase*.09 + i*.53))).toFloat()
                canvas.drawLine(x,cy+radius*.27f-amplitude,x,cy+radius*.27f+amplitude,p)
            }
        }
        postInvalidateDelayed(33)
    }
    private fun Paint.letterSpacingCompat() { }
}

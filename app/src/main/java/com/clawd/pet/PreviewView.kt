package com.clawd.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class PreviewView(context: Context, private val prefs: Prefs) : View(context) {

    private val density = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var unit = (7f * density).toInt()
    private val t0 = SystemClock.uptimeMillis()

    private var nextBlink = 1.6
    private var blinkUntil = 0.0
    private var nextWave = 3.0
    private var waveUntil = 0.0

    fun refresh() = invalidate()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {

        val uH = ((h - 24 * density) / (PetRenderer.GRID_H + 5)).toInt()
        val uW = ((w - 24 * density) / (PetRenderer.GRID_W + 4)).toInt()
        unit = max((3f * density).toInt(), minOf(uH, uW, (10f * density).toInt()))
    }

    override fun onDraw(canvas: Canvas) {
        val now = (SystemClock.uptimeMillis() - t0) / 1000.0
        val pal = Skins.palettes[prefs.color.coerceIn(0, Skins.palettes.size - 1)]

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        bg.color = PetRenderer.mix(0xFF1E1E22.toInt(), pal.body, 0.14f)
        val r = 22f * density
        canvas.drawRoundRect(rect, r, r, bg)

        if (now >= nextBlink) { blinkUntil = now + 0.13; nextBlink = now + 2.0 + Math.random() * 3.0 }
        if (now >= nextWave) { waveUntil = now + 1.4; nextWave = now + 6.0 + Math.random() * 4.0 }

        var eyes = if (now < blinkUntil) "blink" else "open"
        var arms = "normal"
        if (now < waveUntil) arms = if ((now * 5).toInt() % 2 == 0) "wave1" else "wave2"
        val bob = (sin(now * 2.0) * unit * 0.35).toFloat()
        val pose = Pose(eyes, 0, arms, "stand", 0, false)

        val petW = PetRenderer.GRID_W * unit
        val petH = PetRenderer.GRID_H * unit
        val ox = (width - petW) / 2f
        val oy = (height - petH) / 2f + bob
        PetRenderer.draw(canvas, ox, oy, unit, pal.body, pal.eye, pose, prefs.accessory)

        postInvalidateOnAnimation()
    }
}

package com.clawd.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class FxView(context: Context, private val engine: PetEngine, private val prefs: Prefs) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF01E1E24.toInt() }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val pal = Skins.palettes[prefs.color.coerceIn(0, Skins.palettes.size - 1)]
        drawSnack(canvas)
        drawPet(canvas, pal)
        drawHungerIndicator(canvas)
        drawParticles(canvas)
        drawBubble(canvas)
    }

    private fun drawSnack(canvas: Canvas) {
        if (!engine.snackVisible) return
        val now = engine.now()
        val cell = max(3f, engine.unit * 0.85f)
        val bob = (kotlin.math.sin(now * 3.0) * engine.unit * 0.6).toFloat()
        drawPattern(canvas, engine.snackKind, engine.snackX, engine.snackY + bob, cell, 1f, 0f)
    }

    private fun drawPet(canvas: Canvas, pal: Palette) {
        val rot = engine.rotation()
        val ox = engine.x
        val oy = engine.y + engine.hopOff
        val cx = ox + engine.petW / 2f
        val cy = oy + engine.petH / 2f
        val rotate = rot != 0f
        if (rotate) { canvas.save(); canvas.rotate(rot, cx, cy) }
        PetRenderer.draw(canvas, ox, oy, engine.unit, pal.body, pal.eye, engine.pose(), prefs.accessory)
        if (rotate) canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        if (engine.particles.isEmpty()) return
        val now = engine.now()
        val cell = max(2, (engine.unit * 0.5f).roundToInt())
        for (p in engine.particles) {
            val prog = ((now - p.born) / p.duration).toFloat().coerceIn(0f, 1f)
            val curX = p.x0 + p.driftX * prog
            val curY = p.y0 - p.driftY * prog
            val scale = 0.55f + 0.45f * minOf(1f, prog * 2.5f)
            drawPattern(canvas, p.kind, curX, curY, cell * scale, 1f - prog, p.rot)
        }
    }

    private fun drawPattern(canvas: Canvas, kind: String, cx: Float, cy: Float, cell: Float, alpha: Float, rot: Float) {
        val pat = Skins.patterns[kind] ?: return
        paint.color = when (kind) {
            "heart" -> Skins.HEART; "zzz" -> Skins.ZZZ; "spark" -> Skins.SPARK
            "star" -> Skins.STAR; "drop" -> Skins.DROP; "note" -> Skins.NOTE
            "food" -> Skins.FOOD; "question" -> Skins.QUESTION
            "fish" -> Skins.FISH; "cookie" -> Skins.COOKIE; "bolt" -> Skins.BOLT
            else -> Skins.SPARK
        }
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val rows = pat.size
        val cols = pat[0].length
        canvas.save()
        canvas.translate(cx, cy)
        if (rot != 0f) canvas.rotate(rot)
        val left = -cols * cell / 2f
        val top = -rows * cell / 2f
        for (r in 0 until rows) {
            val line = pat[r]
            for (c in 0 until cols) {
                if (c < line.length && line[c] == 'X') {
                    val x0 = left + c * cell
                    val y0 = top + r * cell
                    canvas.drawRect(x0, y0, x0 + cell, y0 + cell, paint)
                }
            }
        }
        canvas.restore()
        paint.alpha = 255
    }
    private fun drawBubble(canvas: Canvas) {
        val text = engine.bubbleText ?: return
        if (engine.bubbleAlpha <= 0f) return
        val a = engine.bubbleAlpha.coerceIn(0f, 1f)
        val ts = max(11f, engine.unit * 2.4f)
        textPaint.textSize = ts
        val padH = ts * 0.7f
        val padV = ts * 0.45f
        val fm = textPaint.fontMetrics
        val bw = textPaint.measureText(text) + padH * 2
        val bh = (fm.descent - fm.ascent) + padV * 2
        val cx = engine.bubbleAnchorX()
        val bx = (cx - bw / 2f).coerceIn(engine.unit.toFloat(), width - bw - engine.unit)
        var by = engine.bubbleAnchorY() - engine.unit * 1.5f - bh
        if (by < engine.insetTop.toFloat()) by = engine.bubbleAnchorY() + engine.petH + engine.unit
        rect.set(bx, by, bx + bw, by + bh)
        bubbleBg.alpha = (a * 240).toInt()
        val r = bh / 2.4f
        canvas.drawRoundRect(rect, r, r, bubbleBg)
        textPaint.alpha = (a * 255).toInt()
        canvas.drawText(text, rect.centerX(), by + padV - fm.ascent, textPaint)
        textPaint.alpha = 255
    }

    private fun drawHungerIndicator(canvas: Canvas) {
        if (!prefs.hungerEnabled) return
        val hunger = engine.hunger()
        if (hunger < 0.5f) return
        val now = engine.now()
        val pulse = (0.7f + kotlin.math.sin(now * 2.5) * 0.3f).toFloat()
        val alpha = ((hunger - 0.5f) * 2f).coerceIn(0f, 1f) * pulse
        val cell = max(2, (engine.unit * 0.6f).roundToInt()).toFloat()
        val x = engine.x + engine.petW / 2f
        val y = engine.y + engine.hopOff - engine.unit * 3f
        paint.alpha = (alpha * 255).toInt()
        paint.color = if (hunger > 0.8f) Skins.DROP else Skins.QUESTION
        val pattern = if (hunger > 0.8f) "drop" else "question"
        drawPattern(canvas, pattern, x, y, cell, alpha, 0f)
        paint.alpha = 255
    }
}

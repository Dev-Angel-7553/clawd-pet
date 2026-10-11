package com.clawd.pet

import android.graphics.Canvas
import android.graphics.Paint

data class Pose(
    val eyes: String,
    val look: Int,
    val arms: String,
    val legs: String,
    val squash: Int,
    val blush: Boolean,
)

object PetRenderer {

    const val GRID_W = 18
    const val GRID_H = 13

    private val paint = Paint()

    fun mix(a: Int, b: Int, t: Float): Int {
        var out = 0xFF000000.toInt()
        for (shift in intArrayOf(16, 8, 0)) {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            out = out or ((ca + ((cb - ca) * t)).toInt() shl shift)
        }
        return out
    }

    fun draw(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        unit: Int,
        bodyColor: Int,
        eyeColor: Int,
        pose: Pose,
        accessory: Int,
    ) {
        val s = unit
        val blushColor = mix(bodyColor, 0xFFFF4F79.toInt(), 0.5f)

        fun px(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            paint.color = color
            canvas.drawRect(
                ox + x0 * s, oy + y0 * s, ox + x1 * s, oy + y1 * s, paint
            )
        }

        var left = 3
        var right = 15
        var top = 3
        var bottom = 10
        when {
            pose.squash > 0 -> { left = 2; right = 16; top = 5 }
            pose.squash < 0 -> { left = 4; right = 14; top = 2 }
        }
        if (pose.legs == "tuck") { top += 1; bottom += 1 }

        val heights = when (pose.legs) {
            "walk1" -> intArrayOf(1, 2, 1, 2)
            "walk2" -> intArrayOf(2, 1, 2, 1)
            "dangle" -> intArrayOf(3, 3, 3, 3)
            "tuck" -> intArrayOf(1, 1, 1, 1)
            else -> intArrayOf(2, 2, 2, 2)
        }
        val legCols = intArrayOf(left + 1, left + 3, right - 4, right - 2)
        for (i in legCols.indices) {
            val col = legCols[i]
            px(col, bottom, col + 1, bottom + heights[i], bodyColor)
        }

        fun arm(side: Int, style: String) {
            when (style) {
                "up" -> if (side < 0) {
                    px(left - 2, top - 1, left - 1, top + 3, bodyColor)
                    px(left - 1, top + 2, left, top + 3, bodyColor)
                } else {
                    px(right + 1, top - 1, right + 2, top + 3, bodyColor)
                    px(right, top + 2, right + 1, top + 3, bodyColor)
                }
                "tuck" -> if (side < 0) px(left - 1, top + 3, left, top + 5, bodyColor)
                else px(right, top + 3, right + 1, top + 5, bodyColor)
                else -> if (side < 0) px(left - 2, top + 3, left, top + 5, bodyColor)
                else px(right, top + 3, right + 2, top + 5, bodyColor)
            }
        }

        var leftArm = pose.arms
        var rightArm = pose.arms
        if (pose.arms == "wave1") { leftArm = "up"; rightArm = "normal" }
        else if (pose.arms == "wave2") { leftArm = "normal"; rightArm = "up" }
        arm(-1, leftArm)
        arm(1, rightArm)

        px(left, top, right, bottom, bodyColor)

        val ey = top + 2
        val eyeCenters = intArrayOf(left + 3 + pose.look, right - 4 + pose.look)
        for (idx in eyeCenters.indices) {
            val side = if (idx == 0) -1 else 1
            val cx = eyeCenters[idx]
            drawEye(::px, pose.eyes, cx, ey, side, eyeColor)
            if (pose.blush) {
                if (side < 0) px(cx - 2, ey + 2, cx, ey + 3, blushColor)
                else px(cx + 1, ey + 2, cx + 3, ey + 3, blushColor)
            }
        }

        if (accessory > 0) drawAccessory(::px, left, right, top, bottom, pose.look, accessory)
    }

    private inline fun drawEye(
        px: (Int, Int, Int, Int, Int) -> Unit,
        eyes: String, cx: Int, ey: Int, side: Int, eyeColor: Int,
    ) {
        when (eyes) {
            "blink" -> px(cx, ey + 1, cx + 1, ey + 2, eyeColor)
            "happy" -> {
                px(cx - 1, ey + 1, cx, ey + 2, eyeColor)
                px(cx, ey, cx + 1, ey + 1, eyeColor)
                px(cx + 1, ey + 1, cx + 2, ey + 2, eyeColor)
            }
            "surprised" -> px(cx, ey - 1, cx + 1, ey + 2, eyeColor)
            "dizzy" -> {
                val d = arrayOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
                for ((dx, dy) in d) px(cx + dx, ey + dy, cx + dx + 1, ey + dy + 1, eyeColor)
            }
            "sleep" -> px(cx - 1, ey + 1, cx + 2, ey + 2, eyeColor)
            "angry" -> {
                px(cx, ey, cx + 1, ey + 2, eyeColor)
                val brow = if (side < 0) cx - 1 else cx + 1
                px(brow, ey - 1, brow + 1, ey, eyeColor)
            }
            "sad" -> {
                px(cx, ey + 1, cx + 1, ey + 2, eyeColor)
                val brow = if (side < 0) cx - 1 else cx + 1
                px(brow, ey, brow + 1, ey + 1, eyeColor)
            }
            "love" -> {
                val h = Skins.HEART
                px(cx - 1, ey, cx, ey + 1, h)
                px(cx + 1, ey, cx + 2, ey + 1, h)
                px(cx - 1, ey + 1, cx + 2, ey + 2, h)
                px(cx, ey + 2, cx + 1, ey + 3, h)
            }
            "wink" -> {
                if (side < 0) px(cx, ey + 1, cx + 1, ey + 2, eyeColor)
                else px(cx, ey, cx + 1, ey + 2, eyeColor)
            }
            else -> px(cx, ey, cx + 1, ey + 2, eyeColor)
        }
    }

    private fun drawAccessory(
        px: (Int, Int, Int, Int, Int) -> Unit,
        left: Int, right: Int, top: Int, bottom: Int, look: Int, accessory: Int,
    ) {
        val cxMid = (left + right) / 2
        when (accessory) {
            1 -> {
                val main = 0xFF5A86E8.toInt(); val cuff = 0xFFF2F2F2.toInt()
                px(left, top - 1, right, top, cuff)
                px(left + 1, top - 3, right - 1, top - 1, main)
                px(cxMid, top - 4, cxMid + 1, top - 3, cuff)
            }
            2 -> {
                val c = 0xFFE85A6B.toInt()
                px(left + 1, top - 2, right - 1, top, c)
                val visorFrom = if (look >= 0) right - 1 else left - 3
                val visorTo = if (look >= 0) right + 3 else left
                px(visorFrom, top - 1, visorTo, top, c)
            }
            3 -> {
                val f = 0xFF15151A.toInt()
                val e = top + 2
                px(left + 2, e - 1, left + 5, e + 2, f)
                px(right - 5, e - 1, right - 2, e + 2, f)
                px(left + 5, e, right - 5, e + 1, f)
            }
            4 -> {
                val f = 0xFF101014.toInt()
                val e = top + 2
                px(left + 2, e - 1, left + 6, e + 2, f)
                px(right - 6, e - 1, right - 2, e + 2, f)
                px(left + 6, e, right - 6, e + 1, f)
            }
            5 -> {
                val g = 0xFFFFD24D.toInt()
                px(left + 2, top - 3, right - 2, top - 2, g)
            }
            6 -> {
                val b = 0xFFE8455A.toInt()
                px(cxMid - 2, bottom, cxMid, bottom + 2, b)
                px(cxMid + 1, bottom, cxMid + 3, bottom + 2, b)
                px(cxMid, bottom, cxMid + 1, bottom + 1, b)
            }
            7 -> {
                val g = 0xFFFFD24D.toInt()
                px(left + 1, top - 2, right - 1, top, g)
                px(left + 1, top - 4, left + 3, top - 2, g)
                px(cxMid, top - 4, cxMid + 1, top - 2, g)
                px(right - 3, top - 4, right - 1, top - 2, g)
            }
            8 -> {
                val band = 0xFF2C2C34.toInt(); val pad = 0xFFE85A6B.toInt()
                px(left + 1, top - 2, right - 1, top - 1, band)
                px(left - 1, top + 1, left + 1, top + 4, pad)
                px(right - 1, top + 1, right + 1, top + 4, pad)
            }
            9 -> {
                val petal = 0xFFEE85AE.toInt(); val mid = 0xFFFFD24D.toInt()
                px(cxMid - 1, top - 3, cxMid + 2, top - 2, petal)
                px(cxMid, top - 4, cxMid + 1, top - 1, petal)
                px(cxMid, top - 3, cxMid + 1, top - 2, mid)
            }
        }
    }
}

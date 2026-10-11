package com.clawd.pet

import android.os.SystemClock
import android.view.MotionEvent
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Particle(
    val kind: String,
    val x0: Float,
    val y0: Float,
    val driftX: Float,
    val driftY: Float,
    val born: Double,
    val duration: Double,
    val rot: Float,
)

class PetEngine(
    private val prefs: Prefs,
    var density: Float,
    private val haptic: (Int) -> Unit,
) {
    var unit = 1; private set
    var petW = 1; private set
    var petH = 1; private set

    var screenW = 1
    var screenH = 1
    var insetTop = 0
    var insetBottom = 0

    var originX = 0
    var originY = 0

    var x = 0f; private set
    var y = 0f; private set
    var hopOff = 0f; private set

    private var state = "idle"
    private var vx = 0f
    private var vy = 0f
    private var posReady = false

    private var lastTick = 0.0
    private var lastInteraction = 0.0
    private var nextAction = 0.0
    private var nextBlink = 0.0
    private var blinkUntil = 0.0
    private var look = 0
    private var expr: String? = null
    private var exprUntil = 0.0
    private var squashUntil = 0.0
    private var waveUntil = 0.0
    private var hopActive = false
    private var hopVy = 0f
    private var walkTarget = 0f
    private var walkTargetY = 0f
    private var walkVertical = false
    private var wakeAt = 0.0
    private var eatUntil = 0.0
    private var nextBite = 0.0
    private var nextHeart = 0.0
    private var petStart = 0.0
    private var nextPetLove = 0.0
    private var nextZ = 0.0
    private var lastTap = 0.0
    private val recentTaps = ArrayList<Double>()
    private var greetPending = false
    private var nextHungry = 0.0
    private var hungerNow = 0f

    var tiltX = 0f
    private var batteryLow = false
    private var charging = false
    private var nextLowBat = 0.0
    private var nextShake = 0.0
    private var eatKind = "food"

    private var dragResist = false
    private var nextResistSay = 0.0

    var snackX = 0f; private set
    var snackY = 0f; private set
    var snackVisible = false; private set
    var snackKind = "food"; private set
    private var nextSnack = 0.0
    private var walkToSnack = false

    var bubbleText: String? = null; private set
    var bubbleAlpha = 0f; private set
    private var bubbleVisible = false
    private var bubbleUntil = 0.0
    private var bubbleFadeOut = false
    private var lastPhrase: String? = null

    val particles = ArrayList<Particle>()

    private class Touch(val x0: Float, val y0: Float, val t0: Double, val ox: Float, val oy: Float) {
        var anchor = 0f
        var dir = 0
        var vx = 0f
        val hist = ArrayList<Triple<Double, Float, Float>>()
    }
    private var touch: Touch? = null

    fun now(): Double = SystemClock.uptimeMillis() / 1000.0
    private fun dp(v: Number): Float = v.toFloat() * density
    private fun cl(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v
    private fun hyp(a: Float, b: Float) = sqrt(a * a + b * b)

    init {
        configure()
        reset()
    }

    fun greet() { greetPending = true }

    fun setBattery(pct: Int, chg: Boolean) {
        batteryLow = pct in 1..15
        if (chg && !charging) onChargeStart()
        charging = chg
    }

    fun setTilt(gx: Float) { tiltX = gx }

    fun onShake() {
        val n = now()
        if (touch != null) return
        if (state == "fly" || state == "drag" || state == "eat") return
        if (state == "sleep") {
            state = "idle"; look = 0; lastInteraction = n
            setExpr("surprised", 1.0); hop(dp(320)); say("wake")
            nextShake = n + 1.2
            return
        }
        if (n < nextShake) return
        nextShake = n + 1.5
        lastInteraction = n
        setExpr("dizzy", 2.0)
        for (i in 0 until 3) spawnParticle("star")
        say("dizzy")
        haptic(1)
    }

    private fun onChargeStart() {
        val n = now()
        if (state == "fly" || state == "drag") return
        if (state == "sleep") { state = "idle"; look = 0 }
        lastInteraction = n
        setExpr("happy", 2.0)
        for (i in 0 until 3) spawnParticle("bolt")
        hop(dp(360))
        say("charge")
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 6
    }

    fun isInteracting(): Boolean = touch != null

    fun configure() {
        val si = prefs.size.coerceIn(0, Skins.sizeDp.size - 1)
        val newUnit = maxOf(2, dp(Skins.sizeDp[si]).toInt())
        val oldW = petW; val oldH = petH
        unit = newUnit
        petW = PetRenderer.GRID_W * unit
        petH = PetRenderer.GRID_H * unit
        if (posReady) {
            x += (oldW - petW) / 2f
            y += (oldH - petH).toFloat()
            hop(dp(300))
        }
        if (!prefs.snacks) { snackVisible = false; walkToSnack = false }
    }

    private fun reset() {
        val n = now()
        state = "idle"
        x = 0f; y = 0f; vx = 0f; vy = 0f
        posReady = false
        lastTick = n
        lastInteraction = n
        nextAction = n + 4.0
        nextBlink = n + 2.0
        nextHungry = n + 20.0
        nextSnack = n + getSnackInterval()
    }

    private fun getSnackInterval(): Double {
        val speed = prefs.snackSpeed.coerceIn(0, 2)
        return when (speed) {
            0 -> Random.nextDouble(60.0, 120.0)
            1 -> Random.nextDouble(30.0, 70.0)
            2 -> Random.nextDouble(15.0, 40.0)
            else -> Random.nextDouble(30.0, 70.0)
        }
    }

    fun nextDelayMs(): Long {
        val n = now()
        if (state == "press" || state == "drag" || state == "pet" || state == "fly" ||
            state == "walk" || state == "eat" || hopActive || bubbleFadeOut || n < squashUntil ||
            (expr == "dizzy" && n < exprUntil) || particles.isNotEmpty()
        ) return 16
        if (state == "sleep") return 250
        return 80
    }

    fun step() {
        val n = now()
        val dt = (n - lastTick).coerceIn(0.0, 0.05)
        val dtf = dt.toFloat()
        lastTick = n
        if (screenW <= 1 || screenH <= 1) return
        val minX = 0f
        val maxX = maxOf(0f, (screenW - petW).toFloat())
        val minY = insetTop.toFloat()
        val maxY = maxOf(insetTop.toFloat(), (screenH - insetBottom - petH).toFloat())

        if (!posReady) {
            restorePosition(minX, maxX, minY, maxY)
            posReady = true
            if (greetPending) {
                greetPending = false
                hop(dp(420))
                say("hello")
            }
        }

        val gravity = prefs.gravity
        if (state == "press" && touch != null && n - touch!!.t0 >= 0.4) startPet(n)

        when (state) {
            "pet" -> {
                if (n >= nextHeart) { nextHeart = n + 0.45; spawnParticle("heart"); haptic(4) }

                if (n - petStart > 3.0 && n >= nextPetLove) {
                    nextPetLove = n + 2.2
                    setExpr("love", 1.4)
                    for (i in 0 until 3) spawnParticle("heart")
                    if (Random.nextFloat() < 0.6f) say("pet")
                }
            }
            "eat" -> stepEat(n)
            "fly" -> stepFly(dtf, n, gravity, minX, maxX, minY, maxY)
            "idle", "walk", "sleep" -> stepGround(dtf, n, gravity, minX, maxX, minY, maxY)
        }

        if (hopActive) {
            hopOff += hopVy * dtf
            hopVy += dp(2600) * dtf
            if (hopOff >= 0f) { hopOff = 0f; hopVy = 0f; hopActive = false; squashUntil = n + 0.1 }
        }

        updateHunger(n)
        updateParticles(n)
        updateBubble(n)

        if (prefs.snacks && !snackVisible && n >= nextSnack &&
            (state == "idle" || state == "walk")
        ) {
            nextSnack = n + getSnackInterval()
            if (Random.nextFloat() < 0.7f) spawnSnack()
        }
    }
    private fun stepGround(dt: Float, n: Double, gravity: Boolean, minX: Float, maxX: Float, minY: Float, maxY: Float) {
        x = cl(x, minX, maxX)
        if (gravity) {
            if (y < maxY - dp(2) && !hopActive) { state = "fly"; vx = 0f; vy = 0f; return }
            y = minOf(y, maxY)
        } else {
            y = cl(y, minY, maxY)
        }

        val speed = Skins.speedFactor[prefs.speed.coerceIn(0, Skins.speedFactor.size - 1)].toFloat()

        if (gravity && state == "idle" && !hopActive && abs(tiltX) > 0.3f) {
            x = cl(x + tiltX * dp(60) * speed * dt, minX, maxX)
            look = if (tiltX > 0) 1 else -1
        }

        if (state == "walk") {
            if (walkVertical && !gravity) {

                look = 0
                val stepY = dp(42) * speed * dt
                y += cl(walkTargetY - y, -stepY, stepY)
                if (abs(walkTargetY - y) < dp(2) || y <= minY || y >= maxY) {
                    y = cl(y, minY, maxY)
                    state = "idle"
                    nextAction = n + Random.nextDouble(2.5, 6.0)
                    savePosition()
                }
                return
            }

            val dir = if (walkTarget > x) 1 else -1
            look = dir
            val chaseBoost = if (walkToSnack) 1.6f else 1f
            x += dir * dp(42) * speed * chaseBoost * dt
            val reachedX = (dir > 0 && x >= walkTarget) || (dir < 0 && x <= walkTarget)
            if (reachedX || x <= minX || x >= maxX) {
                x = cl(x, minX, maxX)
                if (walkToSnack && snackVisible) { eatSnack(n); return }
                walkToSnack = false
                state = "idle"
                nextAction = n + Random.nextDouble(2.5, 6.0)
                savePosition()
            }
            return
        }

        if (state == "sleep") {
            if (n >= nextZ) { nextZ = n + 1.6; spawnParticle("zzz") }

            if (n >= wakeAt) {
                state = "idle"; look = 0
                lastInteraction = n
                nextAction = n + Random.nextDouble(1.5, 4.0)
                setExpr("surprised", 0.8)
                hop(dp(300))
                say("wake")
            }
            return
        }

        if (n >= nextBlink) {
            blinkUntil = n + 0.13
            nextBlink = n + if (Random.nextFloat() < 0.15f) 0.35 else Random.nextDouble(2.0, 5.5)
        }

        val si = prefs.sleep.coerceIn(0, Skins.sleepSeconds.size - 1)
        var timeout = Skins.sleepSeconds[si]
        if (timeout > 0 && isNight()) timeout = maxOf(20, timeout / 2)
        if (timeout > 0 && n - lastInteraction > timeout && !hopActive) {
            state = "sleep"; look = 0; nextZ = n + 0.6
            wakeAt = n + Random.nextDouble(18.0, 45.0)
            if (isNight()) say("sleepy") else hideBubble()
            return
        }

        if (batteryLow && !charging && n >= nextLowBat) {
            nextLowBat = n + Random.nextDouble(14.0, 22.0)
            setExpr("sad", 2.2); spawnParticle("drop"); say("lowbat"); return
        }

        if (prefs.hungerEnabled && hungerNow > 0.75f && n >= nextHungry) {
            nextHungry = n + Random.nextDouble(8.0, 16.0)
            setExpr("sad", 2.5); spawnParticle("drop"); say("hungry"); return
        }

        if (prefs.snacks && snackVisible && state == "idle" && !hopActive) {
            walkTarget = cl(snackX - petW / 2f, minX, maxX)
            walkVertical = false
            walkToSnack = true
            state = "walk"
            if (Random.nextFloat() < 0.4f) say("tap")
            return
        }

        if (n < nextAction || hopActive) return
        nextAction = n + Random.nextDouble(3.5, 9.0) / speed
        val roll = Random.nextFloat()
        if (!prefs.wander) {
            if (roll < 0.5f) look = intArrayOf(-1, 0, 1).random()
            return
        }
        when {
            roll < 0.30f -> {

                val far = Random.nextDouble(dp(40).toDouble(), dp(170).toDouble()).toFloat() * if (Random.nextBoolean()) 1 else -1
                val target = cl(x + far, minX, maxX)
                if (abs(target - x) > dp(12)) { walkTarget = target; walkVertical = false; walkToSnack = false; state = "walk" }
            }
            !gravity && roll < 0.40f -> {

                val far = Random.nextDouble(dp(60).toDouble(), dp(220).toDouble()).toFloat() * if (Random.nextBoolean()) 1 else -1
                val ty = cl(y + far, minY, maxY)
                if (abs(ty - y) > dp(12)) { walkTargetY = ty; walkVertical = true; walkToSnack = false; state = "walk" }
            }
            roll < 0.58f -> look = intArrayOf(-1, 0, 1).random()
            roll < 0.72f -> say("idle")
            roll < 0.84f -> hop(dp(360))
            else -> { waveUntil = n + 1.6; if (Random.nextFloat() < 0.3f) spawnParticle("note") }
        }
    }
    private fun stepFly(dt: Float, n: Double, gravity: Boolean, minX: Float, maxX: Float, minY: Float, maxY: Float) {
        x += vx * dt
        y += vy * dt
        if (gravity) {
            vy += dp(2600) * dt
            vx *= exp(-0.35f * dt)
        } else {
            val fr = exp(-2.0f * dt)
            vx *= fr; vy *= fr
        }
        var impact = 0f
        if (x < minX) { x = minX; impact = maxOf(impact, abs(vx)); vx = abs(vx) * 0.55f }
        else if (x > maxX) { x = maxX; impact = maxOf(impact, abs(vx)); vx = -abs(vx) * 0.55f }
        if (y < minY) { y = minY; impact = maxOf(impact, abs(vy)); vy = abs(vy) * 0.55f }
        else if (y > maxY) {
            y = maxY; impact = maxOf(impact, abs(vy))
            if (gravity && vy < dp(380)) { land(n); return }
            vy = -abs(vy) * (if (gravity) 0.42f else 0.55f)
            vx *= if (gravity) 0.75f else 1.0f
            squashUntil = n + 0.1
        }
        if (impact > dp(2300)) { setExpr("dizzy", 2.6); say("dizzy"); haptic(1) }
        else if (impact > dp(900)) haptic(3)
        if (state == "fly" && !gravity && hyp(vx, vy) < dp(45)) land(n)
    }

    private fun land(n: Double) {
        state = "idle"; vx = 0f; vy = 0f
        squashUntil = n + 0.14
        nextAction = n + Random.nextDouble(2.0, 5.0)
        savePosition()
    }

    private fun updateHunger(n: Double) {
        if (!prefs.hungerEnabled) { hungerNow = 0f; return }
        val full = Skins.hungerFullSeconds[prefs.hungerRate.coerceIn(0, Skins.hungerFullSeconds.size - 1)]
        val elapsed = (System.currentTimeMillis() - prefs.lastFedAt) / 1000.0
        hungerNow = cl((elapsed / full).toFloat(), 0f, 1f)
    }

    fun hunger(): Float = hungerNow

    fun feed() {
        val n = now()
        val wasFull = prefs.hungerEnabled && hungerNow < 0.3f
        prefs.lastFedAt = System.currentTimeMillis()
        hungerNow = 0f
        hopActive = false; hopOff = 0f; hopVy = 0f; vx = 0f; vy = 0f
        look = 0
        lastInteraction = n
        if (wasFull) {

            state = "idle"
            setExpr("happy", 1.6)
            spawnParticle("note")
            nextAction = n + Random.nextDouble(1.5, 3.5)
            say("full")
            return
        }
        prefs.feedCount = prefs.feedCount + 1
        state = "eat"
        eatKind = arrayOf("food", "fish", "cookie").random()
        eatUntil = n + 1.6
        nextBite = n
        setExpr("happy", 2.2)
        say("feed")
    }

    private fun stepEat(n: Double) {
        if (n >= nextBite) { nextBite = n + 0.32; spawnParticle(eatKind); haptic(3) }
        if (n >= eatUntil) {
            state = "idle"
            setExpr("happy", 1.6)
            nextAction = n + Random.nextDouble(2.0, 5.0)
            savePosition()
        }
    }

    private fun spawnSnack() {
        if (snackVisible || screenW <= 1 || screenH <= 1) return
        val minX = petW * 0.6f
        val maxX = screenW - petW * 0.6f
        if (maxX <= minX) return
        snackX = Random.nextFloat() * (maxX - minX) + minX
        if (abs(snackX - (x + petW / 2f)) < petW) {
            snackX = if (x + petW / 2f < screenW / 2f) maxX else minX
        }
        snackY = if (prefs.gravity) {
            (screenH - insetBottom).toFloat() - petH * 0.35f
        } else {
            y + petH * 0.5f
        }
        snackKind = arrayOf("food", "fish", "cookie").random()
        snackVisible = true
    }

    private fun eatSnack(n: Double) {
        snackVisible = false
        walkToSnack = false
        look = 0
        lastInteraction = n
        hopActive = false; hopOff = 0f; hopVy = 0f
        state = "eat"
        eatKind = snackKind
        eatUntil = n + 1.3
        nextBite = n
        setExpr("happy", 2.2)
        prefs.feedCount = prefs.feedCount + 1
        if (prefs.hungerEnabled) {
            prefs.lastFedAt = System.currentTimeMillis()
            hungerNow = 0f
        }
        nextSnack = n + getSnackInterval()
        if (Random.nextFloat() < 0.7f) say("feed")
    }

    fun pose(): Pose {
        val n = now()
        var eyes = "open"
        var lk = look
        var arms = "normal"
        var legs = "stand"
        var squash = 0
        var blush = false

        when (state) {
            "drag", "fly" -> { eyes = "surprised"; arms = "up"; legs = "dangle" }
            "pet" -> { eyes = "happy"; blush = true; squash = if ((n * 3).toInt() % 2 == 0) 1 else 0; lk = 0 }
            "eat" -> { eyes = "happy"; blush = true; lk = 0 }
            "sleep" -> { eyes = "sleep"; arms = "tuck"; legs = "tuck"; lk = 0 }
            "walk" -> legs = if ((n * 7).toInt() % 2 == 0) "walk1" else "walk2"
            "press" -> squash = 1
        }

        if ((state == "idle" || state == "walk") && n < waveUntil)
            arms = if ((n * 5).toInt() % 2 == 0) "wave1" else "wave2"

        if (hopActive && (state == "idle" || state == "walk")) {
            legs = "dangle"
            if (hopVy < 0) { squash = -1; arms = "up" }
        }

        if (n < exprUntil && expr != null && state != "sleep") {
            eyes = expr!!
            if (expr == "happy" || expr == "love") blush = true
        }

        if (prefs.hungerEnabled && hungerNow > 0.8f && (state == "idle" || state == "walk") &&
            (expr == null || n >= exprUntil)
        ) eyes = "sad"

        if (n < squashUntil && squash == 0) squash = 1
        if (eyes == "open" && (state == "idle" || state == "walk") && n < blinkUntil) eyes = "blink"
        if (legs == "tuck" && squash != 0) squash = 0
        return Pose(eyes, lk, arms, legs, squash, blush)
    }

    fun rotation(): Float {
        val n = now()
        return when {
            state == "fly" -> cl(vx / dp(70), -25f, 25f)
            expr == "dizzy" && n < exprUntil -> (sin(n * 10.0) * 7.0).toFloat()
            state == "drag" && dragResist -> (sin(n * 22.0) * 9.0).toFloat()
            state == "drag" && touch != null && touch!!.hist.isNotEmpty() &&
                n - touch!!.hist.last().first < 0.08 -> cl(touch!!.vx / dp(90), -18f, 18f)
            else -> 0f
        }
    }

    private fun restorePosition(minX: Float, maxX: Float, minY: Float, maxY: Float) {
        val fx = (prefs.posX / 1000f).coerceIn(0f, 1f)
        val fy = (prefs.posY / 1000f).coerceIn(0f, 1f)
        x = minX + (maxX - minX) * fx
        y = minY + (maxY - minY) * fy
    }

    private fun savePosition() {
        val minX = 0f; val maxX = maxOf(1f, (screenW - petW).toFloat())
        val minY = insetTop.toFloat(); val maxY = maxOf(minY + 1f, (screenH - insetBottom - petH).toFloat())
        val fx = ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
        val fy = ((y - minY) / (maxY - minY)).coerceIn(0f, 1f)
        prefs.posX = (fx * 1000).toInt()
        prefs.posY = (fy * 1000).toInt()
    }

    fun recenter() {
        val minX = 0f; val maxX = maxOf(0f, (screenW - petW).toFloat())
        val minY = insetTop.toFloat(); val maxY = maxOf(insetTop.toFloat(), (screenH - insetBottom - petH).toFloat())
        x = (minX + maxX) / 2f
        y = (minY + maxY) / 2f
        posReady = true
        state = "idle"; vx = 0f; vy = 0f
        lastInteraction = now()
        hop(dp(500))
        for (i in 0 until 3) spawnParticle("spark")
        say("hello")
        savePosition()
    }

    private fun setExpr(e: String, dur: Double) { expr = e; exprUntil = now() + dur }

    private fun hop(strength: Float) {
        if (state != "idle" && state != "walk") return
        hopActive = true
        hopVy = -strength
        if (hopOff > 0) hopOff = 0f
    }

    private fun startPet(n: Double) { state = "pet"; nextHeart = n + 0.1; petStart = n; nextPetLove = n + 3.0; haptic(0) }

    private fun endPet(n: Double) {
        state = "idle"; setExpr("happy", 2.2)
        nextAction = n + Random.nextDouble(3.0, 6.0)
        prefs.petsCount = prefs.petsCount + 1
        if (Random.nextFloat() < 0.6f) say("pet")
    }

    private fun stroke() { spawnParticle("heart"); haptic(4) }

    private fun say(category: String, force: Boolean = false) {
        if (!force && !prefs.phrases) return
        if (!force && category == "idle") {
            val chance = Skins.phraseFreqChance[prefs.phraseFreq.coerceIn(0, Skins.phraseFreqChance.size - 1)]
            if (Random.nextDouble() > chance) return
        }
        val raw = Phrases.pick(category, lastPhrase)
        lastPhrase = raw
        val text = raw.replace("{name}", prefs.petName)
        bubbleText = text
        bubbleVisible = true
        bubbleFadeOut = false
        bubbleAlpha = 0f
        bubbleUntil = now() + 2.0 + text.length * 0.05
    }

    private fun hideBubble() { if (bubbleVisible) bubbleFadeOut = true }

    private fun updateBubble(n: Double) {
        if (!bubbleVisible) { bubbleAlpha = 0f; return }
        if (!bubbleFadeOut && n >= bubbleUntil) bubbleFadeOut = true
        if (bubbleFadeOut) {
            bubbleAlpha -= 0.12f
            if (bubbleAlpha <= 0f) { bubbleAlpha = 0f; bubbleVisible = false; bubbleFadeOut = false; bubbleText = null }
        } else if (bubbleAlpha < 1f) {
            bubbleAlpha = minOf(1f, bubbleAlpha + 0.15f)
        }
    }

    fun bubbleAnchorX(): Float = x + petW / 2f
    fun bubbleAnchorY(): Float = y + hopOff

    private fun spawnParticle(kind: String) {
        if (particles.size >= 16) return
        val n = now()
        val headY = y + hopOff + unit * 2f
        val sx: Float; val sy: Float; val driftX: Float; val driftY: Float
        when (kind) {
            "zzz" -> { sx = x + petW * 0.72f; sy = headY - unit * 2f; driftX = dp(18); driftY = dp(Random.nextDouble(40.0, 58.0)) }
            "food", "fish", "cookie" -> {
                sx = x + petW / 2f + Random.nextFloat() * petW * 0.6f - petW * 0.3f
                sy = y - dp(16); driftX = Random.nextFloat() * dp(12) - dp(6)
                driftY = -dp(Random.nextDouble(28.0, 46.0))
            }
            else -> {
                sx = x + petW / 2f + Random.nextFloat() * petW * 0.5f - petW * 0.25f
                sy = headY - unit * 2f; driftX = Random.nextFloat() * dp(32) - dp(16)
                driftY = dp(Random.nextDouble(40.0, 58.0))
            }
        }
        val dur = when (kind) { "zzz" -> 1.6; "food", "fish", "cookie" -> 0.9; else -> 1.1 }
        particles.add(Particle(kind, sx, sy, driftX, driftY, n, dur, Random.nextFloat() * 30f - 15f))
    }

    private fun updateParticles(n: Double) {
        particles.removeAll { n - it.born >= it.duration }
    }

    fun onTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val rx = event.rawX - originX
        val ry = event.rawY - originY
        val n = now()
        if (action == MotionEvent.ACTION_DOWN) {
            val wasSleeping = state == "sleep"
            lastInteraction = n
            val t = Touch(rx, ry, n, rx - x, ry - y)
            t.anchor = rx
            t.hist.add(Triple(n, rx, ry))
            touch = t
            hopActive = false; hopOff = 0f; hopVy = 0f; vx = 0f; vy = 0f
            state = "press"
            if (wasSleeping) { setExpr("surprised", 0.9); say("wake") }
            return true
        }
        val t = touch ?: return false
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                t.hist.add(Triple(n, rx, ry))
                while (t.hist.size > 2 && n - t.hist[0].first > 0.1) t.hist.removeAt(0)
                if (state == "press" && hyp(rx - t.x0, ry - t.y0) > dp(8)) {
                    state = "drag"; haptic(3)
                    dragResist = prefs.cling && Random.nextFloat() < 0.4f
                    if (dragResist) {
                        setExpr("angry", 1.2); nextResistSay = n + 0.6
                        if (Random.nextFloat() < 0.7f) say("drag")
                    } else if (Random.nextFloat() < 0.25f) say("drag")
                }
                if (state == "drag") {

                    val dMinX = -petW * 0.15f
                    val dMaxX = screenW - petW * 0.85f
                    val dTop = insetTop.toFloat()
                    val dBot = maxOf(dTop, (screenH - insetBottom - petH).toFloat())
                    val tx = cl(rx - t.ox, dMinX, dMaxX)
                    val ty = cl(ry - t.oy, dTop, dBot)
                    if (dragResist) {
                        x += (tx - x) * 0.12f
                        y += (ty - y) * 0.12f
                        if (n >= nextResistSay) {
                            nextResistSay = n + 1.3
                            setExpr("angry", 1.1)
                            if (Random.nextFloat() < 0.6f) say("annoyed", true)
                        }
                    } else {
                        x = tx; y = ty
                    }
                    val h0 = t.hist[0]
                    if (n - h0.first > 0.005) t.vx = (rx - h0.second) / (n - h0.first).toFloat()
                } else if (state == "pet") {
                    val dx = rx - t.anchor
                    if (abs(dx) > dp(10)) {
                        val dir = if (dx > 0) 1 else -1
                        if (t.dir != 0 && dir != t.dir) stroke()
                        t.dir = dir; t.anchor = rx
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touch = null
                lastInteraction = n
                when (state) {
                    "drag" -> releaseDrag(n, t, action == MotionEvent.ACTION_CANCEL)
                    "pet" -> endPet(n)
                    "press" -> { state = "idle"; if (action == MotionEvent.ACTION_UP) tap(n) }
                }
                return true
            }
        }
        return true
    }

    private fun releaseDrag(n: Double, t: Touch, cancelled: Boolean) {
        dragResist = false
        var vX = 0f; var vY = 0f
        if (!cancelled && t.hist.size >= 2) {
            val a = t.hist[0]; val b = t.hist[t.hist.size - 1]
            if (b.first - a.first > 0.005 && n - b.first < 0.08) {
                vX = (b.second - a.second) / (b.first - a.first).toFloat()
                vY = (b.third - a.third) / (b.first - a.first).toFloat()
            }
        }
        val limit = dp(6000)
        vX = cl(vX, -limit, limit); vY = cl(vY, -limit, limit)
        val speed = hyp(vX, vY)
        if (!cancelled && prefs.cling && speed > dp(900) && Random.nextFloat() < 0.5f) {
            catchSelf(n); return
        }
        if (speed > dp(700) || prefs.gravity) {
            state = "fly"; vx = vX; vy = vY
            if (speed > dp(2600)) say("throw")
        } else {

            val minX = 0f; val maxX = maxOf(0f, (screenW - petW).toFloat())
            val minY = insetTop.toFloat()
            val maxY = maxOf(minY, (screenH - insetBottom - petH).toFloat())
            x = cl(x, minX, maxX); y = cl(y, minY, maxY)
            state = "idle"; squashUntil = n + 0.12
            nextAction = n + Random.nextDouble(2.0, 5.0); savePosition()
        }
    }

    private fun catchSelf(n: Double) {
        val minX = 0f; val maxX = maxOf(0f, (screenW - petW).toFloat())
        val minY = insetTop.toFloat()
        val maxY = maxOf(minY, (screenH - insetBottom - petH).toFloat())
        x = cl(x, minX, maxX)
        y = if (prefs.gravity) maxY else cl(y, minY, maxY)
        vx = 0f; vy = 0f
        hopActive = false; hopOff = 0f; hopVy = 0f
        state = "idle"
        squashUntil = n + 0.2
        setExpr("surprised", 1.2)
        lastInteraction = n
        haptic(1)
        say("grab")
        nextAction = n + Random.nextDouble(2.0, 5.0)
        savePosition()
    }

    private fun tap(n: Double) {
        if (n - lastTap < 0.32) {
            lastTap = 0.0
            hop(dp(720)); setExpr("happy", 1.3)
            for (i in 0 until 3) spawnParticle("spark")
            haptic(0)
            prefs.playCount = prefs.playCount + 1
            if (Random.nextFloat() < 0.4f) say("jump")
            return
        }
        lastTap = n
        recentTaps.removeAll { n - it >= 2.5 }
        recentTaps.add(n)
        if (recentTaps.size >= 6) {
            recentTaps.clear()
            setExpr("angry", 2.5); say("annoyed", true); haptic(1); return
        }
        squashUntil = n + 0.12
        setExpr("surprised", 0.45)
        hop(dp(330)); haptic(3)
        if (Random.nextFloat() < 0.35f) say("tap")
    }
}

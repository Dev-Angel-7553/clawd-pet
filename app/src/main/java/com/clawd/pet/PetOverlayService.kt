package com.clawd.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class PetOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var engine: PetEngine
    private lateinit var fxView: FxView
    private lateinit var touchView: TouchView
    private lateinit var fxLp: WindowManager.LayoutParams
    private lateinit var touchLp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null
    private val fxLoc = IntArray(2)
    private var sensorMgr: SensorManager? = null
    private var accel: Sensor? = null
    private var sensorsOn = false
    private var batteryOn = false

    private val sensorListener = object : SensorEventListener {
        private var lx = 0f
        private var ly = 0f
        private var lz = 0f
        private var inited = false
        private var lastShake = 0L
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
            engine.setTilt((-x / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f))
            if (inited) {
                val delta = abs(x - lx) + abs(y - ly) + abs(z - lz)
                if (delta > 24f) {
                    val t = SystemClock.uptimeMillis()
                    if (t - lastShake > 1200) { lastShake = t; engine.onShake() }
                }
            }
            lx = x; ly = y; lz = z; inited = true
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val chg = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            engine.setBattery(pct, chg)
        }
    }

    private var running = false
    private var shown = false
    private var added = false

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_STOP = "com.clawd.pet.STOP"
        const val ACTION_TOGGLE = "com.clawd.pet.TOGGLE"
        const val ACTION_RECENTER = "com.clawd.pet.RECENTER"
        const val ACTION_FEED = "com.clawd.pet.FEED"
        const val ACTION_REFRESH = "com.clawd.pet.REFRESH"
        private const val CH = "clawd_pet"
        private const val NID = 42

        fun start(ctx: Context) {
            val i = Intent(ctx, PetOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, PetOverlayService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        prefs.ensureBorn()
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        engine = PetEngine(prefs, resources.displayMetrics.density) { code -> if (prefs.haptics) vibrate(code) }
        buildParams()
        fxView = FxView(this, engine, prefs)
        touchView = TouchView(this, engine) { }
        sensorMgr = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accel = sensorMgr?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun buildParams() {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        fxLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        touchLp = WindowManager.LayoutParams(
            10, 10, 0, 0, overlayType(), base, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        running = true
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_TOGGLE -> { if (shown) hide() else show(); updateNotification() }
            ACTION_RECENTER -> { if (!shown) show(); engine.recenter() }
            ACTION_FEED -> { if (!shown) show(); engine.feed() }
            ACTION_REFRESH -> {
                if (!prefs.enabled) { stopEverything(); return START_NOT_STICKY }
                engine.configure(); if (!shown) show() else syncSensors()
            }
            else -> { engine.greet(); show() }
        }
        ensureLoop()
        return START_STICKY
    }

    private fun startInForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NID, n)
        }
    }

    private fun show() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopEverything(); return }
        addViews()
        shown = true
        syncSensors()
    }

    private fun hide() { removeViews(); shown = false; stopSensors() }

    private fun syncSensors() {
        if (!shown) { stopSensors(); return }
        if (!batteryOn) {
            try { registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)); batteryOn = true }
            catch (_: Exception) {}
        }
        if (prefs.sensors && !sensorsOn && accel != null) {
            try { sensorMgr?.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI); sensorsOn = true }
            catch (_: Exception) {}
        } else if (!prefs.sensors && sensorsOn) {
            try { sensorMgr?.unregisterListener(sensorListener) } catch (_: Exception) {}
            sensorsOn = false
            engine.setTilt(0f)
        }
    }

    private fun stopSensors() {
        if (batteryOn) { try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}; batteryOn = false }
        if (sensorsOn) { try { sensorMgr?.unregisterListener(sensorListener) } catch (_: Exception) {}; sensorsOn = false }
        engine.setTilt(0f)
    }

    private fun addViews() {
        if (added) return
        try {
            wm.addView(fxView, fxLp)
            wm.addView(touchView, touchLp)
            added = true
        } catch (_: Exception) { added = false }
    }

    private fun removeViews() {
        if (!added) return
        try { wm.removeView(touchView) } catch (_: Exception) {}
        try { wm.removeView(fxView) } catch (_: Exception) {}
        added = false
    }

    private fun stopEverything() {
        running = false
        handler.removeCallbacks(tick)
        stopSensors()
        removeViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        stopSensors()
        removeViews()
        super.onDestroy()
    }

    private fun ensureLoop() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            updateMetrics()
            engine.step()
            if (shown && added) {

                if (!engine.isInteracting()) repositionTouch()
                fxView.invalidate()
            }
            handler.postDelayed(this, if (shown) engine.nextDelayMs() else 400L)
        }
    }

    private fun repositionTouch() {

        val minSlop = (22f * resources.displayMetrics.density).toInt()
        val slop = maxOf(engine.unit * 3, minSlop)

        touchLp.x = (engine.x - slop).toInt() + engine.originX
        touchLp.y = (engine.y + engine.hopOff - slop).toInt() + engine.originY
        touchLp.width = engine.petW + slop * 2
        touchLp.height = engine.petH + slop * 2
        try { wm.updateViewLayout(touchView, touchLp) } catch (_: Exception) {}
    }

    private fun updateMetrics() {
        val density = resources.displayMetrics.density
        val comfort = (6f * density).toInt()
        val topMargin = (2f * density).toInt()
        val hardMinBottom = (14f * density).toInt()

        var w = 0
        var h = 0
        if (added) {
            fxView.getLocationOnScreen(fxLoc)
            engine.originX = fxLoc[0]
            engine.originY = fxLoc[1]
            w = fxView.width
            h = fxView.height
        }
        if (w <= 1 || h <= 1) {

            if (Build.VERSION.SDK_INT >= 30) {
                val b = wm.currentWindowMetrics.bounds
                w = b.width(); h = b.height()
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
                w = dm.widthPixels; h = dm.heightPixels
            }
        }
        engine.screenW = w
        engine.screenH = h

        val navBottom = if (Build.VERSION.SDK_INT >= 30) {
            fxView.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            @Suppress("DEPRECATION") fxView.rootWindowInsets?.systemWindowInsetBottom ?: 0
        }
        engine.insetTop = topMargin
        engine.insetBottom = maxOf(navBottom + comfort, hardMinBottom)
    }
    private fun vibrate(code: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = when (code) { 0 -> 22L; 1 -> 40L; 4 -> 8L; else -> 12L }
        if (Build.VERSION.SDK_INT >= 26) {
            val amp = if (code == 1) 180 else 60
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Clawd", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun servicePi(action: String, rc: Int): PendingIntent {
        val i = Intent(this, PetOverlayService::class.java).setAction(action)
        val fl = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, rc, i, fl)
    }

    private fun openPi(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fl = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 100, i, fl)
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val toggle = if (shown) "Спрятать" else "Показать"
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_clawd)
            .setContentTitle("Clawd")
            .setContentText(if (shown) "Питомец гуляет по экрану" else "Питомец спрятан")
            .setContentIntent(openPi())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggle, servicePi(ACTION_TOGGLE, 1))
            .addAction(0, "Покормить", servicePi(ACTION_FEED, 2))
            .addAction(0, "В центр", servicePi(ACTION_RECENTER, 3))
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NID, buildNotification())
    }
}

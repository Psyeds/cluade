package com.syed.reelsguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var panel: FrameLayout? = null
    private var card: LinearLayout? = null
    private var panelStatus: TextView? = null
    private var unlockBtn: Button? = null
    private var renderedState = ""

    private var warned5 = false
    private var warned1 = false
    private var unlockTicks = 0

    private val tick: Runnable = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 1000L)
        }
    }

    private val unlockRunnable: Runnable = object : Runnable {
        override fun run() {
            unlockTicks--
            if (unlockTicks <= 0) {
                Store.clearAll(this@BubbleService)
                toast("Unlocked")
                removePanel()
            } else {
                unlockBtn?.text = "Unlocking in ${unlockTicks}s (close this to cancel)"
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addBubble()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removePanel()
        bubble?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubble = null
        super.onDestroy()
    }

    // ---------- foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("guard", "Reels Guard", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "guard")
            .setContentTitle("Reels Guard is on")
            .setContentText("Tap the floating bubble to set your stop time")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun fmt(t: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))

    private fun fmtRem(ms: Long): String {
        val m = (ms + 59_999L) / 60_000L
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"
    }

    private fun buzz(pattern: LongArray) {
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun label(ctx: Context, t: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(ctx)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        return tv
    }

    private fun btn(ctx: Context, t: String, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.background = rounded(Color.parseColor("#3B3B4A"), 14)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun state(now: Long = System.currentTimeMillis()): String = when {
        Store.isLocked(this, now) -> "locked"
        Store.deadline(this) > 0L -> "active"
        else -> "idle"
    }

    // ---------- bubble ----------

    private fun addBubble() {
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.typeface = Typeface.DEFAULT_BOLD
        val size = dp(56)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = Store.bubbleX(this)
        lp.y = Store.bubbleY(this)
        tv.setOnTouchListener(DragListener(lp))
        wm.addView(tv, lp)
        bubble = tv
        refreshBubble(System.currentTimeMillis())
    }

    private inner class DragListener(private val lp: WindowManager.LayoutParams) :
        View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > dp(8) || abs(dy) > dp(8))) moved = true
                    if (moved) {
                        val dm = resources.displayMetrics
                        lp.x = (startX + dx).toInt().coerceIn(0, dm.widthPixels - lp.width)
                        lp.y = (startY + dy).toInt().coerceIn(0, dm.heightPixels - lp.height)
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (_: Exception) {
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        Store.setBubble(this@BubbleService, lp.x, lp.y)
                    } else {
                        openPanel()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun paintBubble(text: String, color: Int) {
        val b = bubble ?: return
        b.text = text
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        g.setStroke(dp(2), Color.WHITE)
        b.background = g
    }

    private fun refreshBubble(now: Long) {
        val dl = Store.deadline(this)
        when {
            Store.isLocked(this, now) -> paintBubble("LOCK", Color.parseColor("#C62828"))
            dl > 0L -> {
                val rem = dl - now
                val txt = if (rem >= 60_000L) "${(rem + 59_999L) / 60_000L}m" else "${(rem + 999L) / 1000L}s"
                val col = when {
                    rem <= 2 * 60_000L -> "#C62828"
                    rem <= 10 * 60_000L -> "#EF6C00"
                    else -> "#2E7D32"
                }
                paintBubble(txt, Color.parseColor(col))
            }
            else -> paintBubble("SET", Color.parseColor("#37474F"))
        }
    }

    // ---------- tick ----------

    private fun onTick() {
        val now = System.currentTimeMillis()
        val dl = Store.deadline(this)

        if (dl > 0L) {
            val rem = dl - now
            if (rem <= 0L) {
                Store.lockUntil(this, now + Store.LOCK_MINUTES * 60_000L)
                buzz(longArrayOf(0, 600, 200, 600, 200, 900))
                toast("Time's up. Go get ready.")
            } else {
                if (rem <= 5 * 60_000L && !warned5) {
                    warned5 = true
                    buzz(longArrayOf(0, 300))
                    toast("5 min left on Reels")
                }
                if (rem <= 60_000L && !warned1) {
                    warned1 = true
                    buzz(longArrayOf(0, 300, 150, 300))
                    toast("1 min left. Start wrapping up.")
                }
            }
        }

        if (Store.isLocked(this, now)) {
            ReelsGuardService.instance?.enforceNow()
        }

        refreshBubble(now)

        if (panel != null) {
            if (state(now) != renderedState) renderPanel() else updatePanelStatus(now)
        }
    }

    // ---------- panel ----------

    private fun openPanel() {
        if (panel != null) return
        val ctx = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)

        val scrim = FrameLayout(ctx)
        scrim.setBackgroundColor(Color.parseColor("#B3000000"))
        scrim.setOnClickListener { removePanel() }

        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(20), dp(20), dp(20), dp(16))
        c.background = rounded(Color.parseColor("#1E1E26"), 24)
        c.isClickable = true

        val w = minOf(resources.displayMetrics.widthPixels - dp(40), dp(360))
        scrim.addView(
            c,
            FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(scrim, lp)
        panel = scrim
        card = c
        renderPanel()
    }

    private fun removePanel() {
        handler.removeCallbacks(unlockRunnable)
        panel?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        panel = null
        card = null
        panelStatus = null
        unlockBtn = null
    }

    private fun updatePanelStatus(now: Long = System.currentTimeMillis()) {
        val dl = Store.deadline(this)
        panelStatus?.text = when (state(now)) {
            "locked" -> "Time's up. Reels is locked until ${fmt(Store.lockedUntil(this))}. Go get ready."
            "active" -> "Stops at ${fmt(dl)} (${fmtRem(dl - now)} left)"
            else -> "Pick the time you need to be done by"
        }
    }

    private fun renderPanel() {
        val c = card ?: return
        val ctx = c.context
        c.removeAllViews()
        handler.removeCallbacks(unlockRunnable)
        unlockBtn = null
        renderedState = state()

        c.addView(label(ctx, "Reels limit", 20f, Color.WHITE, true))
        val status = label(ctx, "", 15f, Color.parseColor("#C8C8D0"), false)
        status.setPadding(0, dp(6), 0, dp(12))
        c.addView(status)
        panelStatus = status
        updatePanelStatus()

        when (renderedState) {
            "locked" -> {
                val b = btn(ctx, "Unlock (15 sec wait)") { startUnlockCountdown() }
                unlockBtn = b
                c.addView(b)
            }
            "active" -> {
                c.addView(btn(ctx, "Change time") {
                    Store.clearAll(this@BubbleService)
                    renderPanel()
                })
                c.addView(btn(ctx, "Cancel limit") {
                    Store.clearAll(this@BubbleService)
                    removePanel()
                })
            }
            else -> addPicker(c, ctx)
        }

        val cb = CheckBox(ctx)
        cb.text = "Only Reels (uncheck = block all of Instagram)"
        cb.setTextColor(Color.parseColor("#C8C8D0"))
        cb.isChecked = Store.reelsOnly(this)
        cb.setOnCheckedChangeListener { _, checked -> Store.setReelsOnly(this@BubbleService, checked) }
        val cbLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cbLp.topMargin = dp(12)
        cb.layoutParams = cbLp
        c.addView(cb)

        c.addView(btn(ctx, "Close") { removePanel() })
    }

    private fun addPicker(c: LinearLayout, ctx: Context) {
        val hours = Array(12) { (it + 1).toString() }
        val mins = Array(60) { String.format(Locale.US, "%02d", it) }

        val hp = NumberPicker(ctx)
        hp.minValue = 1
        hp.maxValue = 12
        hp.displayedValues = hours
        hp.wrapSelectorWheel = true

        val mp = NumberPicker(ctx)
        mp.minValue = 0
        mp.maxValue = 59
        mp.displayedValues = mins
        mp.wrapSelectorWheel = true

        val ap = NumberPicker(ctx)
        ap.minValue = 0
        ap.maxValue = 1
        ap.displayedValues = arrayOf("AM", "PM")

        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 10)
        val h12 = cal.get(Calendar.HOUR)
        hp.value = if (h12 == 0) 12 else h12
        mp.value = cal.get(Calendar.MINUTE)
        ap.value = cal.get(Calendar.AM_PM)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.addView(hp)
        row.addView(mp)
        row.addView(ap)
        c.addView(row)

        c.addView(btn(ctx, "Start limit") {
            val t = Calendar.getInstance()
            t.set(Calendar.HOUR, if (hp.value == 12) 0 else hp.value)
            t.set(Calendar.AM_PM, ap.value)
            t.set(Calendar.MINUTE, mp.value)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (t.timeInMillis <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1)
            startDeadline(t.timeInMillis)
        })

        val quick = LinearLayout(ctx)
        quick.orientation = LinearLayout.HORIZONTAL
        val options = listOf("+15 min" to 15L, "+30 min" to 30L, "+1 hr" to 60L)
        for ((name, minutes) in options) {
            val b = btn(ctx, name) { startDeadline(System.currentTimeMillis() + minutes * 60_000L) }
            val qlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            qlp.topMargin = dp(8)
            qlp.marginEnd = dp(4)
            b.layoutParams = qlp
            quick.addView(b)
        }
        c.addView(quick)
    }

    private fun startDeadline(t: Long) {
        Store.setDeadline(this, t)
        val rem = t - System.currentTimeMillis()
        warned5 = rem <= 5 * 60_000L
        warned1 = rem <= 60_000L
        toast("Locked in. Reels stops at ${fmt(t)}")
        removePanel()
    }

    private fun startUnlockCountdown() {
        unlockTicks = 15
        unlockBtn?.isEnabled = false
        unlockBtn?.text = "Unlocking in 15s (close this to cancel)"
        handler.postDelayed(unlockRunnable, 1000L)
    }
}

package com.syed.reelsguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(32), dp(20), dp(20))

        root.addView(text("Reels Guard", 26f, Color.WHITE, true))
        root.addView(text("Do these once, then start the bubble.", 15f, Color.parseColor("#C8C8D0"), false))

        overlayStatus = text("", 14f, Color.parseColor("#9FA0AA"), false)
        root.addView(button("1. Allow floating bubble") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(overlayStatus)

        a11yStatus = text("", 14f, Color.parseColor("#9FA0AA"), false)
        root.addView(button("2. Turn on the Reels guard") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(a11yStatus)
        root.addView(
            text(
                "If the switch is greyed out: open App info for this app, tap the three dots " +
                    "top right, tap Allow restricted settings, then try step 2 again.",
                13f, Color.parseColor("#9FA0AA"), false
            )
        )
        root.addView(button("Open App info") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        root.addView(button("3. Allow notifications (optional)") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        })

        root.addView(button("Start bubble") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Do step 1 first", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, BubbleService::class.java))
                Toast.makeText(this, "Bubble is on. Drag it where you want.", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(button("Stop bubble") {
            stopService(Intent(this, BubbleService::class.java))
        })

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#14141A"))
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        overlayStatus.text = if (Settings.canDrawOverlays(this)) "Bubble permission: on" else "Bubble permission: off"
        a11yStatus.text = if (accessibilityOn()) "Reels guard: on" else "Reels guard: off"
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains("$packageName/")
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setPadding(0, dp(6), 0, dp(6))
        return tv
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(12)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }
}

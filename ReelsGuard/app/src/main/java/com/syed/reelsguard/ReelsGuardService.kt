package com.syed.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ReelsGuardService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ReelsGuardService? = null

        private val INSTAGRAM = setOf("com.instagram.android", "com.instagram.lite")
    }

    private var lastKick = 0L
    private var lastCheck = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in INSTAGRAM) enforceNow()
    }

    /** Called by Instagram events and by the bubble's 1 second tick while locked. */
    fun enforceNow() {
        if (!Store.isLocked(this)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCheck < 400L) return
        lastCheck = now

        val root = rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString() ?: return
            if (pkg !in INSTAGRAM) return

            val block = !Store.reelsOnly(this) || looksLikeReels(root)
            if (block && now - lastKick > 1500L) {
                lastKick = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                Toast.makeText(this, "Time's up. Go get ready.", Toast.LENGTH_LONG).show()
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    // Best effort: Instagram can rename these view IDs in any update.
    // If Reels slips through, turn off "Reels only" in the bubble panel to block all of Instagram.
    private fun looksLikeReels(root: AccessibilityNodeInfo): Boolean {
        var count = 0

        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            count++
            if (count > 800 || depth > 30) return false
            val id = n.viewIdResourceName ?: ""
            if (id.contains("clips_viewer") || id.contains("reel_viewer")) return true
            if (id.endsWith("clips_tab") && n.isSelected) return true
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                val found = walk(child, depth + 1)
                @Suppress("DEPRECATION")
                child.recycle()
                if (found) return true
            }
            return false
        }

        return walk(root, 0)
    }
}

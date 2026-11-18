package com.syed.reelsguard

import android.content.Context
import android.content.SharedPreferences

object Store {
    // How long Reels stays locked after the time limit hits
    const val LOCK_MINUTES = 30L

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("reelsguard", Context.MODE_PRIVATE)

    fun deadline(c: Context): Long = sp(c).getLong("deadline", 0L)
    fun lockedUntil(c: Context): Long = sp(c).getLong("lockedUntil", 0L)
    fun reelsOnly(c: Context): Boolean = sp(c).getBoolean("reelsOnly", true)
    fun bubbleX(c: Context): Int = sp(c).getInt("bx", 20)
    fun bubbleY(c: Context): Int = sp(c).getInt("by", 300)

    fun isLocked(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        now < lockedUntil(c)

    fun setDeadline(c: Context, t: Long) {
        sp(c).edit().putLong("deadline", t).putLong("lockedUntil", 0L).apply()
    }

    fun lockUntil(c: Context, t: Long) {
        sp(c).edit().putLong("deadline", 0L).putLong("lockedUntil", t).apply()
    }

    fun clearAll(c: Context) {
        sp(c).edit().putLong("deadline", 0L).putLong("lockedUntil", 0L).apply()
    }

    fun setReelsOnly(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("reelsOnly", v).apply()
    }

    fun setBubble(c: Context, x: Int, y: Int) {
        sp(c).edit().putInt("bx", x).putInt("by", y).apply()
    }
}

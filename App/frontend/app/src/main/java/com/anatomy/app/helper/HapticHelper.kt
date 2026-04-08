package com.anatomy.app.helper

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HapticHelper — Utility for haptic (vibration) feedback.
 *
 * Provides short, double, and long buzz patterns.
 * Handles API-level differences (API 26+ VibrationEffect vs API 31+ VibratorManager).
 */
object HapticHelper {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * A short, light tap — used for swipe and navigation feedback.
     */
    fun shortBuzz() {
        vibrate(80)
    }

    /**
     * A double tap pattern — used for detection/confirmation events.
     */
    fun doubleBuzz() {
        val pattern = longArrayOf(0, 80, 100, 80)
        vibratePattern(pattern)
    }

    /**
     * A longer, sustained vibration — used for important alerts.
     */
    fun longBuzz() {
        vibrate(300)
    }

    private fun vibrate(durationMs: Long) {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, -1)
            }
        }
    }
}

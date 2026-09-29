package com.example.smartsolarmobileapp.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps page content clear of the status bar, camera cutout, and gesture bar.
 */
object ScreenInsets {

    fun apply(view: View, extraHorizontalDp: Int = 20, extraVerticalDp: Int = 16) {
        val density = view.resources.displayMetrics.density
        val extraH = (extraHorizontalDp * density).toInt()
        val extraV = (extraVerticalDp * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                bars.left + extraH,
                bars.top + extraV,
                bars.right + extraH,
                bars.bottom + extraV
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}

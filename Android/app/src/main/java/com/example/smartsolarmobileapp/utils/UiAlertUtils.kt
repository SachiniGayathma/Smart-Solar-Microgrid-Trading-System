package com.example.smartsolarmobileapp.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.smartsolarmobileapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

object UiAlertUtils {

    enum class AlertType {
        SUCCESS,
        WARNING,
        ERROR,
        INFO
    }

    /**
     * Displays a modern rounded card dialog with an expressive icon badge,
     * crisp typography, and styled Material action buttons.
     */
    fun showModernDialog(
        context: Context,
        title: String,
        message: String,
        type: AlertType = AlertType.INFO,
        positiveButtonText: String = "Got It",
        onPositiveClick: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        onNegativeClick: (() -> Unit)? = null
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_alert, null)

        val layoutIconBg = view.findViewById<FrameLayout>(R.id.layout_alert_icon_bg)
        val ivIcon = view.findViewById<ImageView>(R.id.iv_alert_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_alert_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_alert_message)
        val btnPrimary = view.findViewById<MaterialButton>(R.id.btn_alert_primary)
        val btnSecondary = view.findViewById<MaterialButton>(R.id.btn_alert_secondary)

        tvTitle.text = title
        tvMessage.text = message
        btnPrimary.text = positiveButtonText

        // Apply visual theming based on AlertType
        when (type) {
            AlertType.WARNING -> {
                ivIcon.setImageResource(R.drawable.ic_alert_triangle)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.solar_amber_primary))
                val bg = ContextCompat.getDrawable(context, R.drawable.bg_dialog_circle)?.mutate() as? GradientDrawable
                bg?.setColor(ContextCompat.getColor(context, R.color.solar_amber_container))
                layoutIconBg.background = bg
                btnPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.solar_amber_primary))
            }
            AlertType.ERROR -> {
                ivIcon.setImageResource(R.drawable.ic_error_circle)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.solar_status_cancelled))
                val bg = ContextCompat.getDrawable(context, R.drawable.bg_dialog_circle)?.mutate() as? GradientDrawable
                bg?.setColor(ContextCompat.getColor(context, R.color.solar_status_cancelled_bg))
                layoutIconBg.background = bg
                btnPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.solar_status_cancelled))
            }
            AlertType.SUCCESS -> {
                ivIcon.setImageResource(R.drawable.ic_check_circle)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.solar_green_primary))
                val bg = ContextCompat.getDrawable(context, R.drawable.bg_dialog_circle)?.mutate() as? GradientDrawable
                bg?.setColor(ContextCompat.getColor(context, R.color.solar_green_container))
                layoutIconBg.background = bg
                btnPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.solar_green_primary))
            }
            AlertType.INFO -> {
                ivIcon.setImageResource(R.drawable.ic_info_circle)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.solar_green_primary))
                val bg = ContextCompat.getDrawable(context, R.drawable.bg_dialog_circle)?.mutate() as? GradientDrawable
                bg?.setColor(ContextCompat.getColor(context, R.color.solar_green_container))
                layoutIconBg.background = bg
                btnPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.solar_green_primary))
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(negativeButtonText != null)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnPrimary.setOnClickListener {
            dialog.dismiss()
            onPositiveClick?.invoke()
        }

        if (!negativeButtonText.isNullOrBlank()) {
            btnSecondary.visibility = View.VISIBLE
            btnSecondary.text = negativeButtonText
            btnSecondary.setOnClickListener {
                dialog.dismiss()
                onNegativeClick?.invoke()
            }
        } else {
            btnSecondary.visibility = View.GONE
        }

        dialog.show()
        return dialog
    }

    /**
     * Displays an elevated floating Snackbar notification with custom icon,
     * crisp typography, and rounded pill background.
     */
    fun showToast(
        activity: Activity,
        message: String,
        type: AlertType = AlertType.INFO,
        duration: Int = Snackbar.LENGTH_SHORT
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        showSnackbar(rootView, message, type, duration)
    }

    /**
     * Displays an elevated floating Snackbar on any provided view.
     */
    fun showSnackbar(
        view: View,
        message: String,
        type: AlertType = AlertType.INFO,
        duration: Int = Snackbar.LENGTH_SHORT
    ) {
        val prefix = when (type) {
            AlertType.SUCCESS -> "✓  "
            AlertType.WARNING -> "⚠️  "
            AlertType.ERROR -> "✕  "
            AlertType.INFO -> "⚡  "
        }

        val snackbar = Snackbar.make(view, "$prefix$message", duration)
        val snackbarView = snackbar.view

        snackbarView.setBackgroundResource(R.drawable.bg_snackbar_pill)
        snackbarView.elevation = 8f

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.let {
            it.setTextColor(Color.WHITE)
            it.textSize = 13.5f
            it.maxLines = 3
        }

        snackbar.show()
    }
}

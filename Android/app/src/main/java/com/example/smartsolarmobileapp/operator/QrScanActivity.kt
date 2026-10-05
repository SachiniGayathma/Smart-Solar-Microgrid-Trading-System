/**
 * Scans or accepts a prosumer QR token and verifies it on the Web API.
 */
package com.example.smartsolarmobileapp.operator

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.models.Reservation
import com.example.smartsolarmobileapp.utils.DateTimeUtils
import com.example.smartsolarmobileapp.utils.ScreenInsets
import com.example.smartsolarmobileapp.utils.UiAlertUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class QrScanActivity : AppCompatActivity() {

    private lateinit var repository: OperatorRepository

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        } else {
            findViewById<EditText>(R.id.et_qr_token).setText(contents)
            verify(contents)
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openScanner() else {
            UiAlertUtils.showModernDialog(
                this,
                "Camera needed",
                "Allow the camera so the QR code can be scanned in portrait.",
                UiAlertUtils.AlertType.WARNING
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)
        ScreenInsets.apply(findViewById(R.id.layout_qr_scan), extraHorizontalDp = 20, extraVerticalDp = 18)
        repository = OperatorRepository(this)

        findViewById<View>(R.id.btn_open_scanner).setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) openScanner() else cameraPermission.launch(Manifest.permission.CAMERA)
        }
        findViewById<View>(R.id.btn_verify_qr).setOnClickListener {
            verify(findViewById<EditText>(R.id.et_qr_token).text.toString())
        }
    }

    private fun openScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Hold the QR code upright")
            .setBeepEnabled(true)
            .setOrientationLocked(false)
            .setCaptureActivity(PortraitCaptureActivity::class.java)
        try {
            scanLauncher.launch(options)
        } catch (e: Exception) {
            UiAlertUtils.showModernDialog(
                this,
                "Camera could not open",
                e.message ?: "Try again, or paste the QR token.",
                UiAlertUtils.AlertType.ERROR
            )
        }
    }

    private fun verify(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            UiAlertUtils.showModernDialog(
                this,
                "QR token required",
                "Scan a code or paste the token before verifying.",
                UiAlertUtils.AlertType.WARNING
            )
            return
        }
        findViewById<ProgressBar>(R.id.pb_verify).visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = repository.verifyQr(trimmed)
                if (isFinishing) return@launch
                findViewById<ProgressBar>(R.id.pb_verify).visibility = View.GONE
                showResult(result.verified, result.message, result.reservation)
            } catch (e: Exception) {
                if (isFinishing) return@launch
                findViewById<ProgressBar>(R.id.pb_verify).visibility = View.GONE
                UiAlertUtils.showModernDialog(
                    this@QrScanActivity,
                    "Verification failed",
                    e.message ?: "The server could not be reached.",
                    UiAlertUtils.AlertType.ERROR
                )
            }
        }
    }

    private fun showResult(verified: Boolean, message: String, reservation: Reservation?) {
        val card = findViewById<CardView>(R.id.card_verify_result)
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_verify_badge).visibility =
            if (verified) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tv_verify_title).text =
            if (verified) "Energy Transfer Completed" else "Verification failed"
        findViewById<TextView>(R.id.tv_verify_message).text = message
        val whenLabel = reservation?.let {
            DateTimeUtils.parseIsoString(it.scheduledAt)?.let { date ->
                "${DateTimeUtils.formatDisplayDate(date)} at ${DateTimeUtils.formatDisplayTime(date)}"
            } ?: it.scheduledAt
        }
        findViewById<TextView>(R.id.tv_verify_id).text =
            reservation?.id?.let { "Reservation ID: $it" }.orEmpty()
        findViewById<TextView>(R.id.tv_verify_nic).text =
            reservation?.let { "Prosumer NIC: ${it.prosumerNic}" }.orEmpty()
        findViewById<TextView>(R.id.tv_verify_station).text =
            reservation?.let { "Station: ${it.stationName ?: it.stationId}" }.orEmpty()
        findViewById<TextView>(R.id.tv_verify_time).text =
            if (whenLabel.isNullOrBlank()) "" else "Time slot: $whenLabel"
    }
}

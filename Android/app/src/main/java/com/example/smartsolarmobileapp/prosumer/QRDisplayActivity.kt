/**
 * Generates and displays a secure 2D barcode for on-site grid operator scanning.
 *
 * TODO: Member 4 (Grid Operator) — Cross-Reference
 * The QR code displayed here encodes either:
 *   (a) A cryptographically secure qrToken string (when the API has approved the reservation), or
 *   (b) The raw reservation ID as a fallback (when operating offline).
 * Your QrScanActivity should decode this payload and POST it to:
 *   POST /api/Reservations/verify-qr  { "qrToken": "<decoded_string>" }
 * See models/QRVerificationRequest.kt and models/QRVerificationResponse.kt for DTOs.
 */
package com.example.smartsolarmobileapp.prosumer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.database.DatabaseHelper
import com.example.smartsolarmobileapp.database.ReservationDao
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class QRDisplayActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var tvReservationId: TextView

    private lateinit var reservationDao: ReservationDao

    private var qrToken: String = ""
    private var reservationId: String = ""
    private var stationName: String = ""
    private var slotTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_display)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Transaction QR Code"

        reservationDao = ReservationDao(DatabaseHelper(this))

        // Maximize screen brightness for effortless on-site optical scanner reading
        boostScreenBrightness()

        extractIntentExtras()
        initializeViews()
        renderQrCode()
    }

    private fun boostScreenBrightness() {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams
    }

    private fun extractIntentExtras() {
        qrToken = intent.getStringExtra("EXTRA_QR_TOKEN") ?: ""
        reservationId = intent.getStringExtra("EXTRA_RESERVATION_ID") ?: ""
        stationName = intent.getStringExtra("EXTRA_STATION_NAME") ?: ""
        slotTime = intent.getStringExtra("EXTRA_SLOT_TIME") ?: ""

        // If qrToken is empty, look up in local SQLite cache
        if (qrToken.isBlank() && reservationId.isNotBlank()) {
            val localRes = reservationDao.getReservationById(reservationId)
            localRes?.let {
                qrToken = it.qrToken ?: it.id ?: ""
                if (stationName.isBlank()) stationName = it.stationName ?: ""
            }
        }

        // Final fallback: use reservation ID as token payload
        if (qrToken.isBlank() && reservationId.isNotBlank()) {
            qrToken = reservationId
        }
    }

    private fun initializeViews() {
        tvTitle = findViewById(R.id.tv_qr_title)
        tvInstructions = findViewById(R.id.tv_qr_instructions)
        ivQrCode = findViewById(R.id.iv_qr_code)
        tvReservationId = findViewById(R.id.tv_qr_reservation_id)

        val displayId = if (reservationId.length > 8) reservationId.take(8).uppercase() else reservationId
        tvReservationId.text = "Reservation #$displayId"

        if (stationName.isNotBlank()) {
            tvInstructions.text = "Present this QR code to the grid operator at $stationName upon arrival"
        }
    }

    /**
     * Encodes the reservation token into a 512x512 bitmap QR code using ZXing.
     */
    private fun renderQrCode() {
        if (qrToken.isBlank()) {
            Toast.makeText(this, "QR payload is missing", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrToken, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to render QR Code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
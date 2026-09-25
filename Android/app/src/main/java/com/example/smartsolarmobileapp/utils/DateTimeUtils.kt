/**
 * Date and time utilities for booking window rules and ISO formatting.
 */
package com.example.smartsolarmobileapp.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {

    private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    private const val DISPLAY_DATE_PATTERN = "EEE, MMM d, yyyy"
    private const val DISPLAY_TIME_PATTERN = "hh:mm a"
    private const val SHORT_DATE_PATTERN = "yyyy-MM-dd"

    private fun getIsoFormat(): SimpleDateFormat {
        return SimpleDateFormat(ISO_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun getDisplayDateFormat(): SimpleDateFormat {
        return SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.getDefault())
    }

    private fun getDisplayTimeFormat(): SimpleDateFormat {
        return SimpleDateFormat(DISPLAY_TIME_PATTERN, Locale.getDefault())
    }

    private fun getShortDateFormat(): SimpleDateFormat {
        return SimpleDateFormat(SHORT_DATE_PATTERN, Locale.getDefault())
    }

    /**
     * Formats a Date object to ISO 8601 UTC string.
     */
    fun toIsoString(date: Date): String {
        return getIsoFormat().format(date)
    }

    /**
     * Parses an ISO 8601 string to a Date object.
     */
    fun parseIsoString(isoString: String?): Date? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val cleanStr = if (isoString.contains(".")) {
                isoString.substring(0, isoString.indexOf('.'))
            } else if (isoString.endsWith("Z")) {
                isoString.substring(0, isoString.length - 1)
            } else {
                isoString
            }
            getIsoFormat().parse(cleanStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats a Date into a human-readable date representation.
     */
    fun formatDisplayDate(date: Date): String {
        return getDisplayDateFormat().format(date)
    }

    /**
     * Formats a Date into a 12-hour AM/PM time representation.
     */
    fun formatDisplayTime(date: Date): String {
        return getDisplayTimeFormat().format(date)
    }

    /**
     * Formats a Date to yyyy-MM-dd format for simple comparisons.
     */
    fun formatShortDate(date: Date): String {
        return getShortDateFormat().format(date)
    }

    /**
     * Enforces the 7-Day Booking Rule.
     * Reservations must be for a future date and scheduled within 7 days from now.
     *
     * @param targetTime The proposed booking timestamp.
     * @param referenceTime The current reference time (defaults to Date()).
     * @return True if target is in the future and does not exceed 7 days from reference.
     */
    fun isWithinSevenDays(targetTime: Date, referenceTime: Date = Date()): Boolean {
        val targetMs = targetTime.time
        val refMs = referenceTime.time

        // Must be in the future (with 1-minute grace period for network/clock differences)
        if (targetMs < (refMs - 60_000L)) {
            return false
        }

        // Maximum boundary: 7 days in milliseconds
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        val maxAllowedMs = refMs + sevenDaysMs

        return targetMs <= maxAllowedMs
    }

    /**
     * Enforces the 12-Hour Notice Rule for modifications and cancellations.
     *
     * @param slotStartTime The scheduled slot start timestamp.
     * @param referenceTime The current reference time (defaults to Date()).
     * @return True if at least 12 hours remain before the slot starts.
     */
    fun isAtLeastTwelveHoursNotice(slotStartTime: Date, referenceTime: Date = Date()): Boolean {
        val twelveHoursMs = 12L * 60L * 60L * 1000L
        val diffMs = slotStartTime.time - referenceTime.time
        return diffMs >= twelveHoursMs
    }

    /**
     * Calculates the maximum selectable booking date (Today + 7 Days).
     */
    fun getMaxBookingDate(from: Date = Date()): Date {
        val calendar = Calendar.getInstance().apply {
            time = from
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        return calendar.time
    }
}

/**
 * Unit tests validating the 12-hour advance notice rule for reservation cancellations and modifications.
 */
package com.example.smartsolarmobileapp

import com.example.smartsolarmobileapp.utils.DateTimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class TwelveHourNoticeRuleTest {

    @Test
    fun isAtLeastTwelveHoursNotice_twentyFourHoursAhead_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 24)
        }
        val slotTime = calendar.time

        assertTrue(
            "A booking 24 hours in the future satisfies the 12-hour cancellation notice",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_fortyEightHoursAhead_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 2)
        }
        val slotTime = calendar.time

        assertTrue(
            "A booking 2 days in the future satisfies the 12-hour cancellation notice",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_exactlyTwelveHoursAhead_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 12)
        }
        val slotTime = calendar.time

        assertTrue(
            "A booking exactly 12 hours ahead meets the minimum notice boundary",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_twelveHoursOneMinuteAhead_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 12)
            add(Calendar.MINUTE, 1)
        }
        val slotTime = calendar.time

        assertTrue(
            "A booking 12 hours and 1 minute ahead satisfies the notice rule",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_elevenHoursFiftyNineMinutesAhead_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 11)
            add(Calendar.MINUTE, 59)
        }
        val slotTime = calendar.time

        assertFalse(
            "A booking 11 hours and 59 minutes ahead violates the 12-hour rule",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_sixHoursAhead_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 6)
        }
        val slotTime = calendar.time

        assertFalse(
            "A booking only 6 hours away cannot be cancelled or modified",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_oneHourAhead_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 1)
        }
        val slotTime = calendar.time

        assertFalse(
            "A booking 1 hour away must be blocked from cancellation",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_pastSlot_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, -2)
        }
        val slotTime = calendar.time

        assertFalse(
            "A past slot can never be cancelled under the 12-hour rule",
            DateTimeUtils.isAtLeastTwelveHoursNotice(slotTime, now)
        )
    }
}

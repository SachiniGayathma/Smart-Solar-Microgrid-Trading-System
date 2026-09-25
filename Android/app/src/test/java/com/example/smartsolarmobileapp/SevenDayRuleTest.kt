/**
 * Unit tests validating the 7-day booking window rule and 12-hour cancellation rule.
 */
package com.example.smartsolarmobileapp

import com.example.smartsolarmobileapp.utils.DateTimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class SevenDayRuleTest {

    @Test
    fun isWithinSevenDays_slotTomorrow_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrow = calendar.time

        assertTrue(
            "A slot scheduled for tomorrow should be within the 7-day window",
            DateTimeUtils.isWithinSevenDays(tomorrow, now)
        )
    }

    @Test
    fun isWithinSevenDays_slotInThreeDays_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 3)
        }
        val inThreeDays = calendar.time

        assertTrue(
            "A slot scheduled 3 days ahead should be within the 7-day window",
            DateTimeUtils.isWithinSevenDays(inThreeDays, now)
        )
    }

    @Test
    fun isWithinSevenDays_slotExactlySevenDays_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 7)
        }
        val sevenDaysAhead = calendar.time

        assertTrue(
            "A slot scheduled at exactly 7 days ahead should be permitted",
            DateTimeUtils.isWithinSevenDays(sevenDaysAhead, now)
        )
    }

    @Test
    fun isWithinSevenDays_slotEightDaysAhead_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 8)
        }
        val eightDaysAhead = calendar.time

        assertFalse(
            "A slot scheduled 8 days ahead must be rejected by the 7-day rule",
            DateTimeUtils.isWithinSevenDays(eightDaysAhead, now)
        )
    }

    @Test
    fun isWithinSevenDays_slotInPast_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterday = calendar.time

        assertFalse(
            "A slot in the past cannot be booked",
            DateTimeUtils.isWithinSevenDays(yesterday, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_slotInTwentyFourHours_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 24)
        }
        val in24Hours = calendar.time

        assertTrue(
            "A slot 24 hours away meets the 12-hour notice requirement",
            DateTimeUtils.isAtLeastTwelveHoursNotice(in24Hours, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_slotInSixHours_returnsFalse() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 6)
        }
        val in6Hours = calendar.time

        assertFalse(
            "A slot 6 hours away violates the 12-hour notice requirement",
            DateTimeUtils.isAtLeastTwelveHoursNotice(in6Hours, now)
        )
    }

    @Test
    fun isAtLeastTwelveHoursNotice_slotInTwelveHoursExact_returnsTrue() {
        val now = Date()
        val calendar = Calendar.getInstance().apply {
            time = now
            add(Calendar.HOUR_OF_DAY, 12)
        }
        val exact12Hours = calendar.time

        assertTrue(
            "A slot exactly 12 hours away satisfies the 12-hour notice requirement",
            DateTimeUtils.isAtLeastTwelveHoursNotice(exact12Hours, now)
        )
    }
}

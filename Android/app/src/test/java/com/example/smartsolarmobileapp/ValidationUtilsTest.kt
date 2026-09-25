/**
 * Module: SE4040 Enterprise Application Development
 * Author: Person 3 (Solar Prosumer Android Specialist)
 * Purpose: Unit tests validating NIC, email, phone, and password business constraints
 * Date: September 2026
 */
package com.example.smartsolarmobileapp

import com.example.smartsolarmobileapp.utils.ValidationUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ValidationUtilsTest verifies that client-side validation correctly conforms
 * to the enterprise rules specified in the SE4040 assignment:
 * 1. NIC as primary key (old 9-character and new 12-digit formats)
 * 2. Proper email structure
 * 3. Valid contact telephone numbers
 * 4. Password length constraint
 */
class ValidationUtilsTest {

    @Test
    fun validOldNic_returnsTrue() {
        // 9 numeric digits followed by uppercase or lowercase 'V' / 'X'
        assertTrue("Expected valid old NIC with V", ValidationUtils.isValidNic("951234567V"))
        assertTrue("Expected valid old NIC with X", ValidationUtils.isValidNic("981234567X"))
        assertTrue("Expected valid old NIC with lowercase v", ValidationUtils.isValidNic("951234567v"))
        assertTrue("Expected valid old NIC with lowercase x", ValidationUtils.isValidNic("981234567x"))
    }

    @Test
    fun validNewNic_returnsTrue() {
        // Exactly 12 numeric digits
        assertTrue("Expected valid 12-digit NIC", ValidationUtils.isValidNic("200012345678"))
        assertTrue("Expected valid 12-digit NIC for older citizen", ValidationUtils.isValidNic("198512345678"))
    }

    @Test
    fun invalidNic_returnsFalse() {
        // Negative test cases for invalid NIC inputs
        assertFalse("Null NIC should be invalid", ValidationUtils.isValidNic(null))
        assertFalse("Empty NIC should be invalid", ValidationUtils.isValidNic(""))
        assertFalse("Whitespace NIC should be invalid", ValidationUtils.isValidNic("   "))
        assertFalse("Too short digits", ValidationUtils.isValidNic("12345"))
        assertFalse("Letters inside numeric block", ValidationUtils.isValidNic("95123A567V"))
        assertFalse("Wrong letter suffix", ValidationUtils.isValidNic("951234567Z"))
        assertFalse("10 digits with letter", ValidationUtils.isValidNic("9512345678V"))
        assertFalse("11 digits", ValidationUtils.isValidNic("20001234567"))
        assertFalse("13 digits", ValidationUtils.isValidNic("2000123456789"))
        assertFalse("12 digits ending in letter", ValidationUtils.isValidNic("20001234567V"))
    }

    @Test
    fun validEmail_returnsTrue() {
        assertTrue("Standard email should be valid", ValidationUtils.isValidEmail("prosumer@solar.lk"))
        assertTrue("Email with subdomain", ValidationUtils.isValidEmail("sarah.energy@sub.grid.org"))
        assertTrue("Email with plus tag", ValidationUtils.isValidEmail("user+trading@example.com"))
    }

    @Test
    fun invalidEmail_returnsFalse() {
        assertFalse("Null email should be invalid", ValidationUtils.isValidEmail(null))
        assertFalse("Plain string without at-sign", ValidationUtils.isValidEmail("plainaddress"))
        assertFalse("Missing domain", ValidationUtils.isValidEmail("user@"))
        assertFalse("Missing username", ValidationUtils.isValidEmail("@example.com"))
        assertFalse("Missing top-level domain", ValidationUtils.isValidEmail("user@domain"))
    }

    @Test
    fun validPhone_returnsTrue() {
        assertTrue("10-digit Sri Lankan phone", ValidationUtils.isValidPhone("0771234567"))
        assertTrue("Phone with +94 international prefix", ValidationUtils.isValidPhone("+94771234567"))
        assertTrue("Landline phone format", ValidationUtils.isValidPhone("0112345678"))
    }

    @Test
    fun invalidPhone_returnsFalse() {
        assertFalse("Null phone should be invalid", ValidationUtils.isValidPhone(null))
        assertFalse("Too short phone", ValidationUtils.isValidPhone("12345"))
        assertFalse("Phone with alphabetic letters", ValidationUtils.isValidPhone("077123ABCD"))
    }

    @Test
    fun passwordLength_validatesCorrectly() {
        assertTrue("6-character password should be valid", ValidationUtils.isValidPassword("123456"))
        assertTrue("Long password should be valid", ValidationUtils.isValidPassword("StrongPass#2026"))
        assertFalse("5-character password should be invalid", ValidationUtils.isValidPassword("12345"))
        assertFalse("Empty password should be invalid", ValidationUtils.isValidPassword(""))
        assertFalse("Null password should be invalid", ValidationUtils.isValidPassword(null))
    }
}

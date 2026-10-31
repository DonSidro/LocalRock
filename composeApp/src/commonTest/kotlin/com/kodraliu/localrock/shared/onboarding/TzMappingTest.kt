package com.kodraliu.localrock.shared.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TzMappingTest {

    @Test
    fun posix_and_country_from_known_tz() {
        assertEquals("PST8PDT,M3.2.0,M11.1.0", posixTzFromIana("America/Los_Angeles"))
        assertEquals("us", countryDomainFromIana("America/Los_Angeles"))
        assertEquals("CET-1CEST,M3.5.0,M10.5.0/3", posixTzFromIana("Europe/Berlin"))
        assertEquals("de", countryDomainFromIana("Europe/Berlin"))
    }

    @Test
    fun puerto_rico_uses_ast_without_dst() {
        // Puerto Rico is UTC-4 year-round and never observes DST, so the POSIX string must
        // not carry US DST rules. Reusing EST5EDT would put the robot's clock and schedules
        // an hour off for the whole US winter.
        assertEquals("AST4", posixTzFromIana("America/Puerto_Rico"))
        assertEquals("us", countryDomainFromIana("America/Puerto_Rico"))
        assertTrue("America/Puerto_Rico" in supportedIanaTimezones())
    }

    @Test
    fun unknown_falls_back_to_default() {
        assertEquals(DEFAULT_POSIX_TZ, posixTzFromIana("Mars/Phobos"))
        assertEquals(DEFAULT_COUNTRY_DOMAIN, countryDomainFromIana("Mars/Phobos"))
    }

    @Test
    fun sanitizeStackServer_stripsApiPrefix() {
        // Roborock firmware always concatenates "api-" + token.r, so the canonical input
        // is "api-<host>" and the function strips that prefix. Inputs without "api-" are
        // passed through host-only with a trailing slash.
        assertEquals("foo.example.com/", sanitizeStackServer("api-foo.example.com"))
        assertEquals("foo.example.com/", sanitizeStackServer("https://api-foo.example.com/some/path"))
        assertEquals("api.roborock.kodraliu.com/", sanitizeStackServer("https://api.roborock.kodraliu.com"))
        assertEquals("plain.example.com/", sanitizeStackServer("plain.example.com"))
    }

    @Test
    fun sanitizeStackServer_rejectsEmpty() {
        assertFailsWith<IllegalArgumentException> { sanitizeStackServer("") }
        assertFailsWith<IllegalArgumentException> { sanitizeStackServer("https://") }
    }
}

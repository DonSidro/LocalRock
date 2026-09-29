package com.kodraliu.localrock.ui.whatsnew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsNewTest {

    @Test
    fun shown_once_to_users_coming_from_an_older_version() {
        // Upgrading from 1.1.0, which never stored a version.
        assertTrue(shouldShowWhatsNew(introSeen = true, lastSeenVersion = null, currentVersion = "1.2.0"))
        assertTrue(shouldShowWhatsNew(introSeen = true, lastSeenVersion = "1.1.0", currentVersion = "1.2.0"))
        // Dismissed.
        assertFalse(shouldShowWhatsNew(introSeen = true, lastSeenVersion = "1.2.0", currentVersion = "1.2.0"))
    }

    @Test
    fun never_shown_on_a_fresh_install_or_without_notes() {
        assertFalse(shouldShowWhatsNew(introSeen = false, lastSeenVersion = null, currentVersion = "1.2.0"))
        assertFalse(shouldShowWhatsNew(introSeen = true, lastSeenVersion = "1.2.0", currentVersion = "9.9.9"))
    }

    @Test
    fun one_entry_per_version() {
        assertEquals(WHATS_NEW.size, WHATS_NEW.map { it.version }.toSet().size)
    }
}

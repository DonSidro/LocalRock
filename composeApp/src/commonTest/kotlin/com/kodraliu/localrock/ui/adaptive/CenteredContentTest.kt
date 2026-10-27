package com.kodraliu.localrock.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

class CenteredContentTest {

    @Test
    fun content_width_is_capped_at_max_width() {
        assertEquals(466f, contentWidthDp(466f, 640f))
        assertEquals(640f, contentWidthDp(669f, 640f))
        assertEquals(640f, contentWidthDp(951f, 640f))
    }
}

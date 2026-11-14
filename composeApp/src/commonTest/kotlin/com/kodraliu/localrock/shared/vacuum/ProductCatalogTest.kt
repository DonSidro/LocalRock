package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductCatalogTest {

    @Test
    fun a_known_model_shows_the_name_roborock_sells_it_under() {
        assertEquals("Roborock Qrevo Pro", ProductCatalog.displayName("roborock.vacuum.a101"))
    }

    @Test
    fun a_model_this_app_does_not_know_is_shown_as_is_not_hidden() {
        assertEquals("roborock.vacuum.a999", ProductCatalog.displayName("roborock.vacuum.a999"))
    }

    @Test
    fun no_model_shows_as_nothing() {
        assertNull(ProductCatalog.displayName(null))
    }
}

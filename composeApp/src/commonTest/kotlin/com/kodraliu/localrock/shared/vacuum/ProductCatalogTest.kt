package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import com.kodraliu.localrock.shared.model.Product
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

    @Test
    fun the_servers_product_name_wins_over_the_catalogue() {
        val product = Product(id = "p", name = "Roborock Qrevo Pro (server)", model = "roborock.vacuum.a101")
        assertEquals("Roborock Qrevo Pro (server)", productDisplayName(product))
    }

    @Test
    fun a_blank_server_name_falls_back_to_the_catalogue_then_to_the_model() {
        assertEquals("Roborock Qrevo Pro", productDisplayName(Product(id = "p", name = " ", model = "roborock.vacuum.a101")))
        assertEquals("roborock.vacuum.zzz", productDisplayName(Product(id = "p", model = "roborock.vacuum.zzz")))
        assertNull(productDisplayName(null))
    }
}

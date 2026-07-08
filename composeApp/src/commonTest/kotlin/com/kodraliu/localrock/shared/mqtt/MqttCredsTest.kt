package com.kodraliu.localrock.shared.mqtt

import com.kodraliu.localrock.shared.model.RegionInfo
import com.kodraliu.localrock.shared.model.Rriot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MqttCredsTest {

    private fun rriot(u: String, s: String, k: String, m: String) = Rriot(
        u = u, s = s, k = k,
        h = "https://api.example.com",
        r = RegionInfo(r = "EU", a = "https://a.example", m = m, l = "https://l.example"),
    )

    @Test
    fun ssl_url_derivesTlsTrueAndCreds() {
        val c = deriveMqttCreds(
            rriot(
                u = "1234567890",
                s = "abcdef0123456789abcdef0123456789",
                k = "zzzzzzzzzzzzzzzz",
                m = "ssl://mqtt-eu-3.roborock.com:8883",
            )
        )
        assertEquals("mqtt-eu-3.roborock.com", c.host)
        assertEquals(8883, c.port)
        assertTrue(c.tls)
        assertEquals("c9cc7cc8", c.username)
        assertEquals("898bf7dbb1fe7823", c.password)
    }

    @Test
    fun mqtt_url_derivesTlsFalse() {
        val c = deriveMqttCreds(
            rriot(
                u = "42",
                s = "0123456789abcdef",
                k = "0123456789abcdef",
                m = "mqtt://api.roborock.kodraliu.com:1883",
            )
        )
        assertEquals("api.roborock.kodraliu.com", c.host)
        assertEquals(1883, c.port)
        assertEquals(false, c.tls)
        assertEquals("f7996f89", c.username)
        assertEquals("99dc9b40748c5354", c.password)
    }

    @Test
    fun thirdVector() {
        val c = deriveMqttCreds(
            rriot(
                u = "user-id",
                s = "session-secret",
                k = "some-key-value",
                m = "ssl://broker.local:8884",
            )
        )
        assertEquals("broker.local", c.host)
        assertEquals(8884, c.port)
        assertTrue(c.tls)
        assertEquals("4d905a77", c.username)
        assertEquals("bdcdeaa264143c7c", c.password)
    }

    @Test
    fun missingScheme_throws() {
        assertFailsWith<MqttCredsException> {
            deriveMqttCreds(rriot("u", "s", "k", "broker.local:1883"))
        }
    }

    @Test
    fun missingPort_throws() {
        assertFailsWith<MqttCredsException> {
            deriveMqttCreds(rriot("u", "s", "k", "ssl://broker.local"))
        }
    }

    @Test
    fun invalidPort_throws() {
        assertFailsWith<MqttCredsException> {
            deriveMqttCreds(rriot("u", "s", "k", "ssl://broker.local:abc"))
        }
    }
}

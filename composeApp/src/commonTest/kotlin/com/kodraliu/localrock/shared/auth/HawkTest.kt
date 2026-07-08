package com.kodraliu.localrock.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals

// Reference vectors verified against the local server's
// build_hawk_authorization (protocol_auth.py:62-92).
class HawkTest {
    private val creds = HawkCreds(
        id = "user-anon",
        session = "session-anon",
        key = "contract-hawk-secret",
    )
    private val ts = 1712345678L

    @Test
    fun vector0_postAppInfo_form() {
        val mac = Hawk.mac(
            creds = creds,
            path = "/user/app/info",
            ts = ts,
            nonce = "contract-0",
            payload = HawkPayload.Form(
                mapOf(
                    "channelToken" to "anon-app-token-0001",
                    "lang" to "en",
                    "locale" to "en-US",
                    "osType" to "IOS",
                    "pushChannel" to "APNS",
                ),
            ),
        )
        assertEquals("pKZxsTCABg6kF0sEiodrNPow4QPejIDollJu2PBIpAc=", mac)
    }

    @Test
    fun vector1_getInboxLatest() {
        val mac = Hawk.mac(
            creds = creds,
            path = "/user/inbox/latest",
            ts = ts,
            nonce = "contract-1",
        )
        assertEquals("MoX7siZAJBmLK6wK7p5idlA2LITGFZaaW8BlmRdlQlo=", mac)
    }

    @Test
    fun vector2_getHomeData() {
        val mac = Hawk.mac(
            creds = creds,
            path = "/v3/user/homes/424242",
            ts = ts,
            nonce = "contract-2",
        )
        assertEquals("6etEC/Ty110CGu6cPmBQcA6aZyU8G6KEWOaLImJAn1g=", mac)
    }

    @Test
    fun vector3_getSceneOrder_withQuery() {
        val mac = Hawk.mac(
            creds = creds,
            path = "/user/scene/order",
            ts = ts,
            nonce = "contract-3",
            query = mapOf("homeId" to "424242"),
        )
        assertEquals("B5dKZVbK2jjgagNQHizyPA+XyOQ+tlKVqd22yv3KohU=", mac)
    }

    @Test
    fun vector4_getHomeScenes() {
        val mac = Hawk.mac(
            creds = creds,
            path = "/user/scene/home/424242",
            ts = ts,
            nonce = "contract-4",
        )
        assertEquals("JE8XTdMSXHeRxNu2mGnYKTS37GcGNaEoryWo3DHw5uE=", mac)
    }

    @Test
    fun authorizationHeaderShape_vector2() {
        val header = Hawk.authorizationHeader(
            creds = creds,
            path = "/v3/user/homes/424242",
            ts = ts,
            nonce = "contract-2",
        )
        assertEquals(
            "Hawk id=\"user-anon\",s=\"session-anon\",ts=\"1712345678\"," +
                "nonce=\"contract-2\",mac=\"6etEC/Ty110CGu6cPmBQcA6aZyU8G6KEWOaLImJAn1g=\"",
            header,
        )
    }
}

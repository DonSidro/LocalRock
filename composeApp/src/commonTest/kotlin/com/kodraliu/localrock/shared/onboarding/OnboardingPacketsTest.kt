package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.testing.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingPacketsTest {

    // Fixed public key PEM from tests/fixtures/onboarding_vectors.json (`fixed_pub_pem`).
    private val FIXED_PUB_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCN8BT5/dAwonKT4LR3dRVedVyj
        cNmXh28d7N8cE5j9UN84Y5Ih1DyFVcRCbXKzbT0ccoaxbxCeuiELNbmPNes9SviH
        a1tmHv4O4fSXNFoedXQGLbmtyuNu1/ZjuFqTcFvzQFINChSigJXQ6Fg7sTQA1yKr
        EVzWS4eunp6qD/l56wIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()

    private val SESSION_KEY = "abcdef0123456789"

    @Test
    fun buildHelloPacket_matchesPythonGolden() {
        val packet = buildHelloPacket(publicKeyPem = FIXED_PUB_PEM)
        // From gen_onboarding_vectors.py — RSA pub key fixed, JSON encoded with separators=(',', ':'),
        // then AES-128-ECB with PKCS7 under PRE_KEY="6433df70f5a3a42e", then framed cmd=16.
        val expected = "312e300000000100100150e7e5119ca33f24c03b6491521c511db68d47ee26364222946b1df1d61fc8d00de838d8b72adcea4b1f5024eb2aeae6cc947031b140f7d9777eb9a9db6a4d72d215af332fd976f182699044d55dbaa8dceb3b34ff314a56c09864afc54f73b03e30425f46c76b3482cdd2e4d2056ef98fdcd6224be4c43fcd5e4a729c85b64abebe5cb7426033f4a9497c2a765338dbedbd66a008f31222b9d4011be08d8701038ee6227c3302b8b9c04c3460c1962fe37252a713c913d353f46cd00a66d37536c9d3f75aee5bcd66496d18cf63d57564ffb9d189410a6fcb439562487bff4f96dfb9a22337a7c9fff850673609e1922b97156cec179882060c7d0a10a30994ba4ff3dca204e7e38aee24216ee40d3145c5a3d3c5d3aa32c576daa8a37a075cbd566ef9617df9c501a7aee6bc1bf2833194330da4aee20146f51f0bfa63e20238fad456e4fda05ef7a47ab3c2ef56828c6cf78cc3"
        assertEquals(expected, packet.toHex())
    }

    @Test
    fun buildWifiConfigPacket_matchesPythonGolden() {
        val body = WifiConfigBody(
            ssid = "my-home-wifi",
            password = "hunter2!",
            serverStack = "api-roborock.example.com/",
            timezone = "America/New_York",
            posixTz = "EST5EDT,M3.2.0,M11.1.0",
            countryDomain = "us",
            tokenS = "S_TOKEN_aaaa",
            tokenT = "T_TOKEN_bbbb",
        )
        val packet = buildWifiConfigPacket(SESSION_KEY, body)
        val expected = "312e3000000001000100e0eed860a7584481fa64ac3b78eae227121e588afdb0ed9897f2917ec4f30f38b0fb6b5abf6e1eb1cf53eb3103d7b973e06b5379a525766c3c50fbce8e9072a55d68bd390406ed629ab96b8dd82cd51991f822d75ea00394849a16ef275d41b7fafb6a7d378c551d50694d3f78a91ecec57d4ec7a45711e5a68fd6b789a12109fede349f86485f0abc2c68e6c427ec7db7fcddfcf96d6b03eea7340b9a09274324b3fd7ee2014a4cbea60377a5bd68fc92c5c37bd2f838c48f8adffcc2d24b7c65ae6eb560d643be89c4f7ee6ed2d0699476465e0c19ecb5cc1441fb9d9f5f24a79ef02c37"
        assertEquals(expected, packet.toHex())
    }
}

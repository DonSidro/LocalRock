package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.testing.fromHex
import kotlin.test.Test
import kotlin.test.assertEquals

class HelloResponseTest {

    // From tests/fixtures/onboarding_vectors.json `fixed_priv_pem_pkcs8`.
    private val FIXED_PRIV_PKCS8 = """
        -----BEGIN PRIVATE KEY-----
        MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAI3wFPn90DCicpPg
        tHd1FV51XKNw2ZeHbx3s3xwTmP1Q3zhjkiHUPIVVxEJtcrNtPRxyhrFvEJ66IQs1
        uY816z1K+IdrW2Ye/g7h9Jc0Wh51dAYtua3K427X9mO4WpNwW/NAUg0KFKKAldDo
        WDuxNADXIqsRXNZLh66enqoP+XnrAgMBAAECgYADbawkCbRwqEvaNJBHXmWgvXb6
        X6xx04ykjWcqN3L3k2+A3i7WcFjx44t1C0289cOHH2EPgh5FpztLGQYdZnEDJck0
        bnRiqKleAIRTuTY6m0ZvUo1IiX5KlieCHDh8DHCCjQbEg/+qhAG4zd5JpRwzgj0T
        cfI2BKK/ay3vG3PguQJBALm3xN6YunTNokr5NpOY+fYQe4XuDxFdzLP0gL9MdoUi
        3jBD6PwhSFPfd8Qu9oJxWjNnc6BoPP300IUQVIWLLG0CQQDDpu5E008nhD+mnYq/
        z+NnxXI89p9oXf/de54f7tuY0WwkYVaMYof+bCZtkQKm8SAuOrOjJMvyFDu/GTQq
        DJi3AkAYqk97QM2CtczYKEEXWTnZdFbzcqqNo+eL/u0aew1CoV7hCiPnWIRCn2tI
        ovQaXvOb4CF7LFztXZZUFdcj5VgBAkB/p4L+1QBoQBlnlJLa48D0DBelPyRy7CHt
        WKKrIyWUZ8+vTPMzVXZ1wgqwolSgp+LfRt2+LFwE7r6syL0IUn+xAkBORthl/Jb7
        pA/SXsLjkEVovd2qaYkoQ8D+vE2rGQZvft8cnpZ9vDLm98FBjAshL46LlO70CVO8
        KwtiJBRD2gVU
        -----END PRIVATE KEY-----
    """.trimIndent()

    // From tests/fixtures/onboarding_vectors.json `hello_response_packet_hex`.
    private val HELLO_RESPONSE_HEX = "312e3000000001001000805e27b4e50e3ebc50c9a54be1ebf73c24c0966a6b5272279194e61a52358b30324e03dd41e39d3c0a636ccaa569aaa7a5112679d286eed6944ec3981d5cc854afe1408b26fe458ceff39395b6c6ce1659028f3108099f053aa43c5b45f34f0616f06a8173ca5a046c95fd7279b2933eacf3b8b789777d5ab92569e4bc8eaa717be03d51fa"

    @Test
    fun parseHelloResponse_extractsSessionKey() {
        val keyPair = importRsa1024KeyPair(FIXED_PRIV_PKCS8)
        val sessionKey = parseHelloResponse(HELLO_RESPONSE_HEX.fromHex(), keyPair)
        // From gen_onboarding_vectors.py — encrypted plaintext was {"id":1,"method":"hello","params":{"key":"abcdef0123456789"}}.
        assertEquals("abcdef0123456789", sessionKey)
    }

    @Test
    fun importRsa1024_publicKey_matchesFixture() {
        val keyPair = importRsa1024KeyPair(FIXED_PRIV_PKCS8)
        val expectedPub = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCN8BT5/dAwonKT4LR3dRVedVyj
            cNmXh28d7N8cE5j9UN84Y5Ih1DyFVcRCbXKzbT0ccoaxbxCeuiELNbmPNes9SviH
            a1tmHv4O4fSXNFoedXQGLbmtyuNu1/ZjuFqTcFvzQFINChSigJXQ6Fg7sTQA1yKr
            EVzWS4eunp6qD/l56wIDAQAB
            -----END PUBLIC KEY-----
        """.trimIndent()
        assertEquals(expectedPub.replace("\r", "").trimEnd(), keyPair.publicKeyPem.trimEnd())
    }
}

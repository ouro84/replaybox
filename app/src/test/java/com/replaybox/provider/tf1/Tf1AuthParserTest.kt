package com.replaybox.provider.tf1

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

class Tf1AuthParserTest {

    @Test
    fun `parses bootstrap fixture with zero error code`() {
        val bootstrap = TestFixtures.load("tf1_auth_bootstrap.json")
        assertThat(Tf1Json.int(bootstrap, "errorCode")).isEqualTo(0)
    }

    @Test
    fun `extracts gigya identity from login payload`() {
        // Contract shape of compte.tf1.fr accounts.login ( userInfo block );
        // cannot be captured as a fixture without credentials.
        val login = buildJsonObject {
            put("errorCode", 0)
            put(
                "userInfo",
                buildJsonObject {
                    put("UID", "30ae1e2b9b8f4a6a91ce540df9d3a1a2")
                    put("UIDSignature", "c2lnbmF0dXJl")
                    put("signatureTimestamp", 1793876489L)
                },
            )
        }
        val identity = Tf1Parsers.gigyaIdentity(login)
        assertThat(identity.uid).isEqualTo("30ae1e2b9b8f4a6a91ce540df9d3a1a2")
        assertThat(identity.signature).isEqualTo("c2lnbmF0dXJl")
        assertThat(identity.timestamp).isEqualTo(1793876489L)
    }

    @Test
    fun `rejects gigya error payload`() {
        val login = buildJsonObject {
            put("errorCode", 403042)
            put("errorMessage", "invalid login identifier or password")
        }
        assertThrows(Tf1AuthException::class.java) { Tf1Parsers.gigyaIdentity(login) }
    }

    @Test
    fun `reads exp claim from a jwt payload`() {
        // JWT payload {"exp":1793876489} as base64url (header/signature faked —
        // only the payload parsing is under test).
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1793876489}""".toByteArray())
        val token = "header.$payload.signature"
        assertThat(Tf1Auth.jwtExpiry(token)).isEqualTo(1793876489000L)
    }

    @Test
    fun `returns null expiry for a malformed token`() {
        assertThat(Tf1Auth.jwtExpiry("not-a-jwt")).isNull()
    }

    @Test
    fun `extracts widevine license and headers`() {
        // Contract shape of the authenticated mediainfo delivery.drms block
        // (cf. cloudstream TF1PlaybackPayload); not capturable anonymously.
        val delivery = buildJsonObject {
            put(
                "drms",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("name", "widevine")
                            put("url", "https://drm-wide.tf1.fr/proxy?id=14564313")
                            put(
                                "h",
                                kotlinx.serialization.json.buildJsonArray {
                                    add(buildJsonObject { put("k", "Authorization"); put("v", "Bearer tok") })
                                },
                            )
                        },
                    )
                },
            )
        }
        val license = Tf1Parsers.licenseFrom(delivery)
        assertThat(license).isNotNull()
        assertThat(license!!.first).isEqualTo("https://drm-wide.tf1.fr/proxy?id=14564313")
        assertThat(license.second).containsExactly("Authorization", "Bearer tok")
    }
}

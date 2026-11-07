package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.account.AccountException
import com.replaybox.core.account.AccountFailure
import okhttp3.FormBody
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Request builders and response parsers of the M6+ sign-in chain (SPEC §Provider M6+).
 *
 * The `m6_auth_*_shape.json` fixtures are SHAPES taken from catchup-tv-and-more
 * `6play.py` @ fa49e5a0, NOT recorded responses (AGENTS.md wants recorded ones): nobody can
 * record the logged-in chain before Arnaud signs in on the box. The spike PR replaces them.
 * `m6_auth_getjwt_no_device.json`, `m6_auth_getjwt_rejected.json` and
 * `m6_auth_upfront_invalid_jwt.json` are real anonymous answers recorded on 2026-11-07.
 */
class M6AuthApiTest {

    private val identity = M6AuthApi.GigyaIdentity("uid-1", "sig-1", "1793876489")

    @Test
    fun loginIsTheGigyaFormOfTheSource() {
        val request = M6AuthApi.loginRequest("someone@example.test", "not-a-password")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.toString()).isEqualTo("https://login-gigya.m6.fr/accounts.login")
        assertThat(request.header("referer")).isEqualTo("https://www.6play.fr/connexion")
        assertThat(request.header("User-Agent")).contains("Mozilla/5.0")
        val form = request.body as FormBody
        val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
        assertThat(fields).containsExactly(
            "loginID", "someone@example.test",
            "password", "not-a-password",
            "apiKey", "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK",
            "format", "jsonp",
            "callback", "jsonp_3bbusffr388pem4",
        )
    }

    @Test
    fun parsesTheJsonpWrappedLogin() {
        val body = "jsonp_3bbusffr388pem4(" + M6Fixtures.text("m6_auth_login_shape.json") + ");"
        assertThat(M6AuthApi.parseLogin(body))
            .isEqualTo(M6AuthApi.GigyaIdentity("uid-shape-0001", "signature-shape=", "1793876489"))
    }

    @Test
    fun parsesAPlainJsonLoginToo() {
        assertThat(M6AuthApi.parseLogin(M6Fixtures.text("m6_auth_login_shape.json")).uid).isEqualTo("uid-shape-0001")
    }

    @Test
    fun refusedLoginIsInvalidCredentials() {
        val body = "jsonp_3bbusffr388pem4(" + M6Fixtures.text("m6_auth_login_error_shape.json") + ");"
        val error = assertThrows(AccountException::class.java) { M6AuthApi.parseLogin(body) }
        assertThat(error.failure).isEqualTo(AccountFailure.INVALID_CREDENTIALS)
        assertThat(error.code).isEqualTo("403042")
    }

    @Test
    fun otherGigyaErrorsAreRejectedWithTheirCode() {
        val error = assertThrows(AccountException::class.java) {
            M6AuthApi.parseLogin("""{"errorCode":400006,"errorMessage":"Invalid parameter value"}""")
        }
        assertThat(error.failure).isEqualTo(AccountFailure.REJECTED)
        assertThat(error.code).isEqualTo("400006")
    }

    @Test
    fun loginWithoutUidIsAServiceError() {
        val error = assertThrows(AccountException::class.java) { M6AuthApi.parseLogin("""{"errorCode":0}""") }
        assertThat(error.failure).isEqualTo(AccountFailure.SERVICE)
    }

    @Test
    fun getJwtCarriesTheGigyaIdentityAndTheDevice() {
        val request = M6AuthApi.jwtRequest(identity, "device-1")
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.toString()).isEqualTo("https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt")
        assertThat(request.header("x-auth-gigya-uid")).isEqualTo("uid-1")
        assertThat(request.header("x-auth-gigya-signature")).isEqualTo("sig-1")
        assertThat(request.header("x-auth-gigya-signature-timestamp")).isEqualTo("1793876489")
        assertThat(request.header("x-auth-device-id")).isEqualTo("device-1")
        assertThat(request.header("x-customer-name")).isEqualTo("m6web")
        assertThat(request.header("x-client-release")).isEqualTo(M6AuthApi.CLIENT_RELEASE)
    }

    @Test
    fun parsesTheJwt() {
        val token = M6AuthApi.parseToken(M6Fixtures.text("m6_auth_getjwt_shape.json"), "getJwt")
        assertThat(token).startsWith("eyJhbGciOiJub25lIn0.")
    }

    @Test
    fun recordedGetJwtErrorsAreServiceErrorsWithTheirStatus() {
        // Recorded anonymously on 2026-11-07: no device id → 400; a device id without a valid
        // Gigya signature → 498.
        val noDevice = assertThrows(AccountException::class.java) {
            M6AuthApi.parseToken(M6Fixtures.text("m6_auth_getjwt_no_device.json"), "getJwt")
        }
        assertThat(noDevice.failure).isEqualTo(AccountFailure.SERVICE)
        assertThat(noDevice.code).isEqualTo("400")
        val rejected = assertThrows(AccountException::class.java) {
            M6AuthApi.parseToken(M6Fixtures.text("m6_auth_getjwt_rejected.json"), "getJwt")
        }
        assertThat(rejected.code).isEqualTo("498")
    }

    @Test
    fun upfrontTokenIsPerServiceUserAndClip() {
        val request = M6AuthApi.upfrontRequest("w9replay", "uid-1", "clip_13179913", "jwt-1")
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.toString()).isEqualTo(
            "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/w9replay/users/uid-1/videos/clip_13179913/upfront-token",
        )
        assertThat(request.header("Authorization")).isEqualTo("Bearer jwt-1")
        assertThat(request.header("x-customer-name")).isEqualTo("m6web")
        assertThat(request.header("x-client-release")).isEqualTo("6.54.2")
    }

    @Test
    fun parsesTheUpfrontToken() {
        assertThat(M6AuthApi.parseToken(M6Fixtures.text("m6_auth_upfront_shape.json"), "upfront-token"))
            .isEqualTo("upfront-shape-token")
    }

    @Test
    fun recordedUpfrontErrorIsAServiceErrorWithItsCode() {
        val error = assertThrows(AccountException::class.java) {
            M6AuthApi.parseToken(M6Fixtures.text("m6_auth_upfront_invalid_jwt.json"), "upfront-token")
        }
        assertThat(error.failure).isEqualTo(AccountFailure.SERVICE)
        assertThat(error.code).isEqualTo("49804")
    }

    @Test
    fun errorMessagesNeverCarryTheBody() {
        val error = assertThrows(AccountException::class.java) { M6AuthApi.parseToken("""{"nope":"secret-ish"}""", "getJwt") }
        assertThat(error.message).doesNotContain("secret-ish")
    }
}

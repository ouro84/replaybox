package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.account.AccountException
import com.replaybox.core.account.AccountFailure
import com.replaybox.core.account.StoredCredentials
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The M6+ sign-in session over a fake transport (zero network): Gigya → getJwt cached until
 * its `exp`, upfront-token with one JWT refresh on 401/498. Bodies are the `_shape` fixtures
 * (shapes from the catchup-tv-and-more source, not recorded responses).
 */
class M6AuthTest {

    private class MemoryCredentials(var pair: Pair<String, String>? = null) : StoredCredentials {
        override fun read() = pair
        override fun save(email: String, password: String) { pair = email to password }
        override fun clear() { pair = null }
    }

    private class FakeTransport : M6AuthTransport {
        val calls = mutableListOf<String>()
        var loginBody = M6Fixtures.text("m6_auth_login_shape.json")
        val upfrontCodes = ArrayDeque<Int>()

        override suspend fun execute(request: Request): M6AuthResponse {
            val host = request.url.host
            calls += when {
                host == "login-gigya.m6.fr" -> "login"
                request.url.encodedPath.endsWith("/getJwt") -> "getJwt"
                request.url.encodedPath.endsWith("/upfront-token") -> "upfront"
                else -> error("unexpected ${request.url}")
            }
            return when (calls.last()) {
                "login" -> M6AuthResponse(200, "jsonp_3bbusffr388pem4($loginBody);")
                "getJwt" -> M6AuthResponse(200, M6Fixtures.text("m6_auth_getjwt_shape.json"))
                else -> {
                    val code = upfrontCodes.removeFirstOrNull() ?: 200
                    if (code == 200) {
                        M6AuthResponse(200, M6Fixtures.text("m6_auth_upfront_shape.json"))
                    } else {
                        M6AuthResponse(code, M6Fixtures.text("m6_auth_upfront_invalid_jwt.json"))
                    }
                }
            }
        }
    }

    /** exp of the getJwt shape token, in ms. */
    private val jwtExpiry = 1793876489000L

    private val logs = mutableListOf<String>()

    private fun auth(
        credentials: MemoryCredentials,
        transport: FakeTransport,
        now: () -> Long = { jwtExpiry - 3_600_000 },
    ) = M6Auth(credentials, deviceId = { "device-1" }, transport = transport, now = now, log = { logs += it })

    @Test
    fun signInStoresCredentialsOnlyAfterTheChainSucceeds() = runBlocking<Unit> {
        val credentials = MemoryCredentials()
        val transport = FakeTransport()
        val auth = auth(credentials, transport)
        assertThat(auth.hasCredentials()).isFalse()
        auth.signIn("someone@example.test", "pw-123456")
        assertThat(credentials.pair).isEqualTo("someone@example.test" to "pw-123456")
        assertThat(auth.hasCredentials()).isTrue()
        assertThat(auth.storedEmail()).isEqualTo("someone@example.test")
        assertThat(transport.calls).containsExactly("login", "getJwt").inOrder()
    }

    @Test
    fun refusedSignInStoresNothing() = runBlocking<Unit> {
        val credentials = MemoryCredentials()
        val transport = FakeTransport().apply { loginBody = M6Fixtures.text("m6_auth_login_error_shape.json") }
        val auth = auth(credentials, transport)
        val error = assertThrows(AccountException::class.java) { runBlocking { auth.signIn("someone@example.test", "bad") } }
        assertThat(error.failure).isEqualTo(AccountFailure.INVALID_CREDENTIALS)
        assertThat(credentials.pair).isNull()
        assertThat(auth.hasCredentials()).isFalse()
        assertThat(transport.calls).containsExactly("login")
    }

    @Test
    fun upfrontTokenReusesTheCachedJwt() = runBlocking<Unit> {
        val transport = FakeTransport()
        val auth = auth(MemoryCredentials("someone@example.test" to "pw-123456"), transport)
        assertThat(auth.upfrontToken("m6replay", "clip_1")).isEqualTo("upfront-shape-token")
        assertThat(auth.upfrontToken("w9replay", "clip_2")).isEqualTo("upfront-shape-token")
        assertThat(transport.calls).containsExactly("login", "getJwt", "upfront", "upfront").inOrder()
    }

    @Test
    fun expiredJwtSignsInAgain() = runBlocking<Unit> {
        val transport = FakeTransport()
        var clock = jwtExpiry - 3_600_000
        val auth = auth(MemoryCredentials("someone@example.test" to "pw-123456"), transport, now = { clock })
        auth.upfrontToken("m6replay", "clip_1")
        clock = jwtExpiry - 30_000
        auth.upfrontToken("m6replay", "clip_1")
        assertThat(transport.calls).containsExactly("login", "getJwt", "upfront", "login", "getJwt", "upfront").inOrder()
    }

    @Test
    fun rejectedJwtIsRefreshedOnce() = runBlocking<Unit> {
        for (code in listOf(401, 498)) {
            val transport = FakeTransport().apply { upfrontCodes += code }
            val auth = auth(MemoryCredentials("someone@example.test" to "pw-123456"), transport)
            assertThat(auth.upfrontToken("m6replay", "clip_1")).isEqualTo("upfront-shape-token")
            assertThat(transport.calls).containsExactly("login", "getJwt", "upfront", "login", "getJwt", "upfront").inOrder()
        }
    }

    @Test
    fun aSecondRejectionFails() = runBlocking<Unit> {
        val transport = FakeTransport().apply { upfrontCodes += listOf(401, 401) }
        val auth = auth(MemoryCredentials("someone@example.test" to "pw-123456"), transport)
        val error = assertThrows(AccountException::class.java) { runBlocking { auth.upfrontToken("m6replay", "clip_1") } }
        assertThat(error.failure).isEqualTo(AccountFailure.SERVICE)
        assertThat(error.code).isEqualTo("401")
        assertThat(transport.calls.count { it == "upfront" }).isEqualTo(2)
    }

    @Test
    fun otherUpfrontErrorsAreNotRetried() = runBlocking<Unit> {
        val transport = FakeTransport().apply { upfrontCodes += 403 }
        val auth = auth(MemoryCredentials("someone@example.test" to "pw-123456"), transport)
        assertThrows(AccountException::class.java) { runBlocking { auth.upfrontToken("m6replay", "clip_1") } }
        assertThat(transport.calls).containsExactly("login", "getJwt", "upfront").inOrder()
    }

    @Test
    fun withoutCredentialsTheTokenIsNotSignedIn() = runBlocking<Unit> {
        val transport = FakeTransport()
        val auth = auth(MemoryCredentials(), transport)
        val error = assertThrows(AccountException::class.java) { runBlocking { auth.upfrontToken("m6replay", "clip_1") } }
        assertThat(error.failure).isEqualTo(AccountFailure.NOT_SIGNED_IN)
        assertThat(transport.calls).isEmpty()
    }

    @Test
    fun signOutForgetsCredentialsAndSession() = runBlocking<Unit> {
        val credentials = MemoryCredentials()
        val transport = FakeTransport()
        val auth = auth(credentials, transport)
        auth.signIn("someone@example.test", "pw-123456")
        auth.signOut()
        assertThat(credentials.pair).isNull()
        assertThat(auth.hasCredentials()).isFalse()
        assertThrows(AccountException::class.java) { runBlocking { auth.upfrontToken("m6replay", "clip_1") } }
    }

    @Test
    fun logsCarryStepsAndStatusesOnly() = runBlocking<Unit> {
        val transport = FakeTransport().apply { upfrontCodes += 498 }
        val auth = auth(MemoryCredentials(), transport)
        auth.signIn("someone@example.test", "pw-123456")
        auth.upfrontToken("m6replay", "clip_1")
        assertThat(logs).isNotEmpty()
        val joined = logs.joinToString("\n")
        for (secret in listOf("someone@example.test", "pw-123456", "uid-shape-0001", "signature-shape", "eyJ", "upfront-shape-token", "device-1")) {
            assertThat(joined).doesNotContain(secret)
        }
        assertThat(joined).contains("gigya login: HTTP 200")
        assertThat(joined).contains("getJwt: HTTP 200")
        assertThat(joined).contains("upfront-token m6replay: HTTP 498")
    }
}

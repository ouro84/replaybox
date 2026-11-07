package com.replaybox.provider.m6

import com.replaybox.core.account.AccountException
import com.replaybox.core.account.AccountFailure
import com.replaybox.core.account.Jwt
import com.replaybox.core.account.ProviderAccount
import com.replaybox.core.account.StoredCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** An auth-chain answer: HTTP status and body. */
data class M6AuthResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** The network seam of [M6Auth]: OkHttp on the box, a fake in unit tests. */
fun interface M6AuthTransport {
    suspend fun execute(request: Request): M6AuthResponse
}

class OkHttpM6AuthTransport(private val client: OkHttpClient = defaultClient()) : M6AuthTransport {
    override suspend fun execute(request: Request): M6AuthResponse = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { M6AuthResponse(it.code, it.body?.string().orEmpty()) }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * M6+ account (Paramètres > M6+): Gigya sign-in, then the 6cloud JWT, then one upfront token
 * per Flux for the drmtoday licence. SOURCED from catchup-tv-and-more `6play.py` @ fa49e5a0,
 * not measured yet (SPEC §Provider M6+): the live spike on the box confirms it.
 *
 * 1. [M6AuthApi.loginRequest] → UID, UIDSignature, signatureTimestamp.
 * 2. [M6AuthApi.jwtRequest] with that identity and [deviceId] → JWT, cached until its `exp`
 *    (re-sign-in a minute before). A refresh is a full sign-in: the Gigya signature is
 *    time-stamped.
 * 3. [upfrontToken] per Épisode; on 401/498 the JWT is refreshed once.
 *
 * Credentials are stored only once the chain up to the JWT succeeds, so a wrong password never
 * makes the M6 Chaînes appear. [log] receives step names and HTTP statuses only: never the
 * e-mail, password, UID, signature, device id or a token.
 */
class M6Auth(
    private val store: StoredCredentials,
    /** Random UUID persisted once per install (not a secret), sent as `x-auth-device-id`. */
    private val deviceId: () -> String,
    private val transport: M6AuthTransport = OkHttpM6AuthTransport(),
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { message -> android.util.Log.i(TAG, message) },
) : ProviderAccount {

    private data class Session(val uid: String, val jwt: String, val expiresAt: Long)

    @Volatile private var session: Session? = null

    /** Cached [hasCredentials]: Providers.all asks it on every access. */
    @Volatile private var signedIn: Boolean? = null
    private val mutex = Mutex()

    override fun hasCredentials(): Boolean = signedIn ?: (store.read() != null).also { signedIn = it }

    override fun storedEmail(): String? = store.read()?.first

    override suspend fun signIn(email: String, password: String) {
        mutex.withLock {
            val fresh = authenticate(email, password)
            store.save(email, password)
            session = fresh
            signedIn = true
        }
        log("sign-in: done")
    }

    override fun signOut() {
        store.clear()
        session = null
        signedIn = false
        log("sign-out")
    }

    /**
     * Upfront token of [clipId] on the Chaîne's [serviceCode], the `x-dt-auth-token` of the
     * drmtoday licence. The source hard-codes service `m6replay`; this uses the real one, like
     * the web app (the spike tells which the server wants).
     */
    suspend fun upfrontToken(serviceCode: String, clipId: String): String {
        var current = session(forceRefresh = false)
        var response = transport.execute(M6AuthApi.upfrontRequest(serviceCode, current.uid, clipId, current.jwt))
        log("upfront-token $serviceCode: HTTP ${response.code}")
        if (response.code == 401 || response.code == 498) {
            current = session(forceRefresh = true)
            response = transport.execute(M6AuthApi.upfrontRequest(serviceCode, current.uid, clipId, current.jwt))
            log("upfront-token $serviceCode (after JWT refresh): HTTP ${response.code}")
        }
        if (!response.ok) {
            throw AccountException(AccountFailure.SERVICE, "M6 upfront-token HTTP ${response.code}", response.code.toString())
        }
        return M6AuthApi.parseToken(response.body, "upfront-token")
    }

    private suspend fun session(forceRefresh: Boolean): Session = mutex.withLock {
        val cached = session
        if (!forceRefresh && cached != null && now() + REFRESH_MARGIN_MS < cached.expiresAt) return cached
        val (email, password) = store.read()
            ?: throw AccountException(AccountFailure.NOT_SIGNED_IN, "M6+ account not signed in")
        authenticate(email, password).also { session = it }
    }

    private suspend fun authenticate(email: String, password: String): Session {
        val login = transport.execute(M6AuthApi.loginRequest(email, password))
        log("gigya login: HTTP ${login.code}")
        val identity = try {
            M6AuthApi.parseLogin(login.body)
        } catch (error: AccountException) {
            log("gigya login: refused" + (error.code?.let { " errorCode=$it" } ?: ""))
            throw error
        }

        val jwtResponse = transport.execute(M6AuthApi.jwtRequest(identity, deviceId()))
        log("getJwt: HTTP ${jwtResponse.code}")
        if (!jwtResponse.ok) {
            throw AccountException(AccountFailure.SERVICE, "M6 getJwt HTTP ${jwtResponse.code}", jwtResponse.code.toString())
        }
        val jwt = M6AuthApi.parseToken(jwtResponse.body, "getJwt")
        val expiresAt = Jwt.expiry(jwt) ?: (now() + DEFAULT_JWT_TTL_MS)
        return Session(identity.uid, jwt, expiresAt)
    }

    companion object {
        const val TAG = "M6Auth"
        private const val REFRESH_MARGIN_MS = 60_000L
        private const val DEFAULT_JWT_TTL_MS = 15 * 60_000L
    }
}

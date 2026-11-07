package com.replaybox.core.account

/** Where a Provider account keeps its e-mail / password pair: [CredentialStore] on the box, a fake in tests. */
interface StoredCredentials {
    fun read(): Pair<String, String>?

    fun save(email: String, password: String)

    fun clear()
}

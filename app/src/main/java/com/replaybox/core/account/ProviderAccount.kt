package com.replaybox.core.account

/**
 * The account a Provider needs (TF1+ today, M6+ in V2), as Paramètres sees it.
 * Credentials stay in the Provider's [CredentialStore]; nothing here logs them.
 */
interface ProviderAccount {
    fun storedEmail(): String?

    fun hasCredentials(): Boolean

    /** Stores the credentials and signs in; throws with a readable message on failure. */
    suspend fun signIn(email: String, password: String)

    fun signOut()
}

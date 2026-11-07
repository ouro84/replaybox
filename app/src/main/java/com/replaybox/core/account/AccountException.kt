package com.replaybox.core.account

/** Why a Provider account step failed, as Paramètres and the player explain it (in French, by the UI). */
enum class AccountFailure {
    /** The broadcaster rejected the e-mail / password pair. */
    INVALID_CREDENTIALS,

    /** Too many attempts: the broadcaster locked the account for a while. */
    ACCOUNT_LOCKED,

    /** The broadcaster refused the sign-in for another reason; [AccountException.code] says which. */
    REJECTED,

    /** No credentials are stored (signed out meanwhile). */
    NOT_SIGNED_IN,

    /** The broadcaster's service answered something unusable (HTTP error, missing field). */
    SERVICE,
}

/**
 * An account step failure. [message] is English, short and free of credentials, tokens and
 * user ids: it reaches logs and the on-box Santé. The UI picks its French text from [failure].
 */
open class AccountException(
    val failure: AccountFailure,
    message: String,
    /** Broadcaster error code or HTTP status, when there is one. */
    val code: String? = null,
) : Exception(message)

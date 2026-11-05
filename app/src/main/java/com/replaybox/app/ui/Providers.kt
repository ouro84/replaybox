package com.replaybox.app.ui

import com.replaybox.provider.api.Provider
import com.replaybox.provider.tf1.Tf1Provider

/**
 * One Provider per Chaîne (glossary). M0 ships TF1+; France TV joins in M1
 * (its anonymous chain is already verified per SPEC).
 */
object Providers {
    val all: List<Provider> = listOf(
        Tf1Provider(),
    )
}

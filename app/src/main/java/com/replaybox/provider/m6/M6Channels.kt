package com.replaybox.provider.m6

import com.replaybox.core.model.Channel

/**
 * The M6 group Chaînes served by the M6 Index, keyed on the programmes' `service_display.code`
 * (SPEC §Provider M6+, decision Q16, measured 2026-11-07). Every other service of the catalogue
 * (parispremierereplay, tevareplay, 6playmaxott, podcast…) is dropped.
 */
object M6Channels {
    private val byService: Map<String, Channel> = linkedMapOf(
        "m6replay" to Channel("m6", "M6"),
        "w9replay" to Channel("w9", "W9"),
        "6terreplay" to Channel("6ter", "6ter"),
        "gulli" to Channel("gulli", "Gulli"),
        "6play" to Channel("m6plus", "M6+"),
    )

    val GROUP: List<Channel> = byService.values.toList()

    fun forService(code: String?): Channel? = code?.let { byService[it] }
}

package com.replaybox.provider.francetv

import kotlinx.serialization.json.JsonObject

object FtvTestFixtures {
    fun load(name: String): JsonObject {
        val stream = FtvTestFixtures::class.java.getResourceAsStream("/fixtures/$name")
            ?: throw AssertionError("fixture not found: $name")
        return FtvJson.parse(stream.readBytes().decodeToString())
    }
}

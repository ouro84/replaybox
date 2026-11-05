package com.replaybox.provider.tf1

import kotlinx.serialization.json.JsonObject

object TestFixtures {
    fun load(name: String): JsonObject {
        val stream = TestFixtures::class.java.getResourceAsStream("/fixtures/$name")
            ?: throw AssertionError("fixture not found: $name")
        return Tf1Json.parse(stream.readBytes().decodeToString())
    }
}

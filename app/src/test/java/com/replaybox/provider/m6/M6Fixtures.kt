package com.replaybox.provider.m6

object M6Fixtures {
    fun text(name: String): String {
        val stream = M6Fixtures::class.java.getResourceAsStream("/fixtures/$name")
            ?: throw AssertionError("fixture not found: $name")
        return stream.readBytes().decodeToString()
    }
}

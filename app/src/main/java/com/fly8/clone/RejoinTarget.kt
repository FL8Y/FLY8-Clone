package com.fly8.clone

object RejoinTarget {
    private val https = Regex("""https://(?:www\.)?roblox\.com/games/(\d+)(?:[^\s]*)?""")
    private val scheme = Regex("""roblox://placeId=(\d+)(?:[^\s]*)?""", RegexOption.IGNORE_CASE)

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        if (https.matches(trimmed)) return trimmed
        if (scheme.matches(trimmed)) return trimmed
        val id = trimmed.toLongOrNull()
        return if (id != null && id > 0) "roblox://placeId=" + id else null
    }

    fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}

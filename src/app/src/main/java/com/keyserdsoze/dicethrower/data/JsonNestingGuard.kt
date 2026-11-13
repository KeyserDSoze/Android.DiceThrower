package com.keyserdsoze.dicethrower.data

/**
 * Android's org.json parser recurses once per nested array or object without any limit, so a few
 * hundred compressed bytes of "[[[[…" in an untrusted link or file exhaust the stack with a
 * StackOverflowError instead of a JSONException. Documents from outside the app are scanned with
 * the parser's own lexical rules (single- or double-quoted strings with escapes, C-style block
 * comments, line comments and # comments) before parsing, and rejected when they nest deeper
 * than [MAX_DEPTH].
 */
internal object JsonNestingGuard {
    const val MAX_DEPTH = 64

    fun requireDepthAtMost(raw: String, maxDepth: Int = MAX_DEPTH) {
        var depth = 0
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '"' || char == '\'' -> {
                    index++
                    while (index < raw.length && raw[index] != char) index += if (raw[index] == '\\') 2 else 1
                }
                char == '/' && raw.startsWith("/*", index) -> {
                    val end = raw.indexOf("*/", index + 2)
                    index = if (end < 0) raw.length else end + 1
                }
                char == '#' || (char == '/' && raw.startsWith("//", index)) -> {
                    while (index < raw.length && raw[index] != '\n' && raw[index] != '\r') index++
                }
                char == '{' || char == '[' -> {
                    depth++
                    require(depth <= maxDepth) { "JSON document nests deeper than $maxDepth levels" }
                }
                char == '}' || char == ']' -> if (depth > 0) depth--
            }
            index++
        }
    }

    /** Runs [parser] after the depth check; a StackOverflowError still becomes an argument error. */
    inline fun <T> parse(raw: String, parser: (String) -> T): T {
        requireDepthAtMost(raw)
        return try {
            parser(raw)
        } catch (overflow: StackOverflowError) {
            throw IllegalArgumentException("JSON document is nested too deeply", overflow)
        }
    }
}

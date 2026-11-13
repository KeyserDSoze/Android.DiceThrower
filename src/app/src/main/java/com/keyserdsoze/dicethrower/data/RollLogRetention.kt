package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.RollLog

/**
 * Log retention is applied per character: logs belong to each character's sync revision, so
 * rolling one character must never drop (and thereby re-stamp) another character's history.
 * A retention of 0 keeps everything.
 */
object RollLogRetention {
    fun append(logs: List<RollLog>, log: RollLog, retention: Int): List<RollLog> {
        val all = logs + log
        if (retention <= 0) return all
        val dropped = all.filter { it.characterId == log.characterId }
            .sortedByDescending { it.timestamp }
            .drop(retention)
            .mapTo(HashSet()) { it.id }
        return if (dropped.isEmpty()) all else all.filterNot { it.id in dropped }
    }
}

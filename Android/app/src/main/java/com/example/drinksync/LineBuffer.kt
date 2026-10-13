package com.example.drinksync

/**
 * Splits an RFCOMM byte stream into lines. Unterminated input is capped at
 * [maxPending] characters so a peer that never sends a newline cannot grow memory.
 */
class LineBuffer(private val maxPending: Int = DrinkEvent.MAX_LINE_LENGTH * 4) {
    private val pending = StringBuilder()

    fun add(chunk: String): List<String> {
        pending.append(chunk)
        val lines = mutableListOf<String>()
        while (true) {
            val text = pending.toString()
            val newline = text.indexOf('\n')
            if (newline < 0) break
            val line = text.substring(0, newline).trim()
            pending.delete(0, newline + 1)
            if (line.isNotEmpty()) {
                lines.add(line)
            }
        }
        val remainder = pending.toString().trim()
        if (remainder.isNotEmpty() && DrinkEvent.parseGrams(remainder) != null) {
            lines.add(remainder)
            pending.clear()
        } else if (pending.length > maxPending) {
            pending.clear()
        }
        return lines
    }
}

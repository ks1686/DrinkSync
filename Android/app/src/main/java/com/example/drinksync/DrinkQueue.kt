package com.example.drinksync

/**
 * FIFO of drink events waiting for the UI to apply them. [current] is the head; each
 * event gets a unique sequence number so two identical amounts still trigger recomposition.
 */
class DrinkQueue {
    data class Event(val seq: Long, val grams: Double)

    private var nextSeq = 0L
    private val pending = ArrayDeque<Event>()

    val current: Event? get() = pending.firstOrNull()

    fun enqueue(grams: Double): Event {
        nextSeq += 1
        return Event(nextSeq, grams).also { pending.addLast(it) }
    }

    /** Drop the head after it has been applied and return the new head. */
    fun markProcessed(): Event? {
        pending.removeFirstOrNull()
        return current
    }
}

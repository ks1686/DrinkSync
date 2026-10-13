package com.example.drinksync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrinkQueueTest {
    @Test
    fun processesInArrivalOrder() {
        val queue = DrinkQueue()
        queue.enqueue(10.0)
        queue.enqueue(20.0)
        assertEquals(10.0, queue.current!!.grams, 0.0)
        assertEquals(20.0, queue.markProcessed()!!.grams, 0.0)
        assertNull(queue.markProcessed())
        assertNull(queue.markProcessed())
    }

    @Test
    fun identicalAmountsGetDistinctEvents() {
        val queue = DrinkQueue()
        val first = queue.enqueue(12.5)
        val second = queue.enqueue(12.5)
        assertNotEquals(first, second)
    }
}

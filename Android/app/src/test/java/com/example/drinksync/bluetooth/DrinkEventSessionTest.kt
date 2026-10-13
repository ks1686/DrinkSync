package com.example.drinksync.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class DrinkEventSessionTest {
    private fun run(input: InputStream): Pair<List<Double>, String> {
        val out = ByteArrayOutputStream()
        val drinks = mutableListOf<Double>()
        DrinkEventSession(input, out, log = {}).run { drinks.add(it) }
        return drinks to out.toString(Charsets.UTF_8.name())
    }

    @Test
    fun acknowledgesEachValidEvent() {
        val (drinks, acks) = run(
            ByteArrayInputStream("Weight difference: 12.50 grams\nWeight difference: 8.00 grams\n".toByteArray()),
        )
        assertEquals(listOf(12.5, 8.0), drinks)
        assertEquals("OK\nOK\n", acks)
    }

    @Test
    fun invalidLinesGetNoAck() {
        val (drinks, acks) = run(
            ByteArrayInputStream("hello\nWeight difference: -3 grams\nWeight difference: 1e9 grams\n".toByteArray()),
        )
        assertEquals(emptyList<Double>(), drinks)
        assertEquals("", acks)
    }

    @Test
    fun reassemblesEventSplitAcrossReads() {
        val chunks = ArrayDeque(listOf("Weight diff", "erence: 30.00 gr", "ams\n"))
        val input = object : InputStream() {
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val next = chunks.removeFirstOrNull() ?: return -1
                val bytes = next.toByteArray()
                bytes.copyInto(b, off)
                return bytes.size
            }
        }
        val (drinks, acks) = run(input)
        assertEquals(listOf(30.0), drinks)
        assertEquals("OK\n", acks)
    }

    @Test
    fun stopsWhenNotRunning() {
        val out = ByteArrayOutputStream()
        var drinks = 0
        DrinkEventSession(
            ByteArrayInputStream("Weight difference: 12.50 grams\n".toByteArray()),
            out,
            isRunning = { false },
            log = {},
        ).run { drinks++ }
        assertEquals(0, drinks)
    }
}

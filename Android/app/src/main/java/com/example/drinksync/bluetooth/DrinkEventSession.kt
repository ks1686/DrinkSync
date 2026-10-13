package com.example.drinksync.bluetooth

import android.util.Log
import com.example.drinksync.DrinkEvent
import com.example.drinksync.LineBuffer
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

private const val TAG = "DrinkSyncApp"
private val ACK = "OK\n".toByteArray(StandardCharsets.UTF_8)

/**
 * Reads wire-format v1 lines from one connected client until EOF or [isRunning] is false.
 * Each valid drink event is reported through [onDrink] and acknowledged with "OK\n".
 * Invalid lines get no reply. IOExceptions propagate to the caller.
 */
class DrinkEventSession(
    private val input: InputStream,
    private val output: OutputStream,
    private val isRunning: () -> Boolean = { true },
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    fun run(onDrink: (grams: Double) -> Unit) {
        val buffer = ByteArray(1024)
        val lines = LineBuffer()
        while (isRunning()) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            val chunk = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
            for (line in lines.add(chunk)) {
                val grams = DrinkEvent.parseGrams(line)
                if (grams != null) {
                    onDrink(grams)
                    output.write(ACK)
                    output.flush()
                } else {
                    log("Received message format mismatch: '${line.take(64)}'")
                }
            }
        }
    }
}

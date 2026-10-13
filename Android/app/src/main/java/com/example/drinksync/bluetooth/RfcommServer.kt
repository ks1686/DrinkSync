package com.example.drinksync.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

private const val TAG = "DrinkSyncApp"

/**
 * Classic Bluetooth RFCOMM server that accepts one coaster at a time.
 * Callbacks run on the server thread; the caller hops to the UI thread.
 */
class RfcommServer(
    private val adapter: () -> BluetoothAdapter?,
    private val hasPermission: () -> Boolean,
    private val onStatus: (String) -> Unit,
    private val onDrink: (grams: Double) -> Unit,
) {
    companion object {
        const val SERVICE_NAME = "DrinkSyncApp"
        val SERVICE_UUID: UUID = UUID.fromString("c7506ec6-09d3-4979-9db3-3b85acad20fd")
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var listenSocket: BluetoothServerSocket? = null

    @Volatile
    private var activeClient: BluetoothSocket? = null

    val isAlive: Boolean get() = thread?.isAlive == true

    fun start() {
        if (isAlive) {
            Log.d(TAG, "Bluetooth server thread already running.")
            return
        }
        running = true
        thread = Thread(::runServer, "DrinkSync-RFCOMM").also { it.start() }
    }

    fun stop() {
        running = false
        closeQuietly(activeClient)
        closeQuietly(listenSocket)
    }

    @SuppressLint("MissingPermission") // Checked via hasPermission() before any adapter call.
    private fun runServer() {
        val bt = adapter()
        if (bt == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.")
            onStatus("Bluetooth Not Supported")
            return
        }
        if (!bt.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled.")
            onStatus("Bluetooth Disabled")
            return
        }
        if (!hasPermission()) {
            Log.e(TAG, "Bluetooth permission missing in server thread!")
            onStatus("Permission Error (Thread)")
            return
        }

        try {
            while (running) {
                if (listenSocket == null) {
                    listenSocket = bt.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    onStatus("Listening...")
                }
                val client = try {
                    listenSocket?.accept()
                } catch (e: IOException) {
                    if (!running) break
                    Log.e(TAG, "IOException accepting client: ${e.message}")
                    closeQuietly(listenSocket)
                    listenSocket = null
                    onStatus("Reconnecting...")
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                } ?: break
                activeClient = client
                onStatus("Connected")
                serveClient(client)
                activeClient = null
                if (running) onStatus("Listening...")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in Bluetooth server: ${e.message}", e)
            onStatus("Security Error")
        } catch (e: IOException) {
            Log.e(TAG, "IOException in Bluetooth server setup: ${e.message}", e)
            onStatus("Server Error")
        } finally {
            closeQuietly(listenSocket)
            listenSocket = null
            if (running) onStatus("Server Stopped / No Connection")
        }
    }

    private fun serveClient(socket: BluetoothSocket) {
        try {
            DrinkEventSession(socket.inputStream, socket.outputStream, isRunning = { running })
                .run(onDrink)
            Log.i(TAG, "Client disconnected.")
        } catch (e: IOException) {
            if (running) {
                Log.e(TAG, "IOException during inputStream.read: ${e.message}")
                onStatus("Connection Lost")
            }
        } finally {
            closeQuietly(socket)
        }
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }
}

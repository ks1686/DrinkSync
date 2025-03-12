package com.example.drinksync

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothService {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val handler = Handler(Looper.getMainLooper())

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionJob: Job? = null
    private var receiveJob: Job? = null

    var isConnected = AtomicBoolean(false)

    interface BluetoothListener {
        fun onConnected()
        fun onConnectionFailed()
        fun onDisconnected()
        fun onMessageReceived(message: String)
        fun onMessageSent(message: String)
        fun onSendFailed()
    }

    private var listener: BluetoothListener? = null

    fun setBluetoothListener(listener: BluetoothListener) {
        this.listener = listener
    }

    fun connectToServer(deviceAddress: String) {
        if (isConnected.get()) {
            Log.w("BluetoothService", "Already connected. Disconnect first.")
            return
        }

        connectionJob = coroutineScope.launch {
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            var tempSocket: BluetoothSocket? = null

            try {
                tempSocket = device?.createRfcommSocketToServiceRecord(uuid)
                bluetoothAdapter?.cancelDiscovery()

                withContext(Dispatchers.IO) {
                    tempSocket?.connect()
                }

                bluetoothSocket = tempSocket
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                isConnected.set(true)

                handler.post { listener?.onConnected() }
                startReceivingMessages()
            } catch (e: IOException) {
                Log.e("BluetoothService", "Connection failed", e)
                closeConnection()
                handler.post { listener?.onConnectionFailed() }
            }
        }
    }

    fun sendMessage(message: String) {
        if (!isConnected.get()) {
            Log.e("BluetoothService", "Not connected. Cannot send message.")
            listener?.onSendFailed()
            return
        }

        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    outputStream?.write(message.toByteArray())
                }
                handler.post { listener?.onMessageSent(message) }
            } catch (e: IOException) {
                Log.e("BluetoothService", "Failed to send message", e)
                handler.post { listener?.onSendFailed() }
            }
        }
    }

    private fun startReceivingMessages() {
        if (receiveJob?.isActive == true) return

        receiveJob = coroutineScope.launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isConnected.get()) {
                try {
                    bytes = withContext(Dispatchers.IO) {
                        inputStream?.read(buffer)
                    } ?: -1

                    if (bytes > 0) {
                        val receivedMessage = String(buffer, 0, bytes)
                        handler.post { listener?.onMessageReceived(receivedMessage) }
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothService", "Receive failed", e)
                    if (isConnected.getAndSet(false)) {
                        handler.post { listener?.onDisconnected() }
                    }
                    break
                }
            }
        }
    }

    fun closeConnection() {
        if (!isConnected.getAndSet(false)) {
            return
        }

        connectionJob?.cancel()
        receiveJob?.cancel()

        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to close connection", e)
        } finally {
            handler.post { listener?.onDisconnected() }
        }
    }
}
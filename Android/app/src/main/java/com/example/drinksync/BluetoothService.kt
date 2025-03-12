package com.example.drinksync

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.*

class BluetoothService {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")

    fun connectToServer(deviceAddress: String): Boolean {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        return try {
            bluetoothSocket = device?.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            true
        } catch (e: IOException) {
            Log.e("BluetoothService", "Connection failed", e)
            false
        }
    }

    fun sendMessage(message: String) {
        try {
            bluetoothSocket?.outputStream?.write(message.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to send message", e)
        }
    }

    fun receiveMessage(): String? {
        return try {
            val buffer = ByteArray(1024)
            val bytes = bluetoothSocket?.inputStream?.read(buffer)
            bytes?.let { String(buffer, 0, it) }
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to receive message", e)
            null
        }
    }

    fun closeConnection() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to close connection", e)
        }
    }
}
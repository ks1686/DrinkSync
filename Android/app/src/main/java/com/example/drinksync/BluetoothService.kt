package com.example.drinksync

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    companion object {
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private fun hasBluetoothPermission(): Boolean {
        val permissions = listOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getPairedDevicesNamesAndAddresses(): List<Pair<String, String>> {
        return try {
            if (!hasBluetoothPermission()) {
                emptyList()
            } else {
                val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                pairedDevices.map { device ->
                    device.name to device.address
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun connectToPairedDevice(address: String): Boolean {
        return try {
            // Check if Bluetooth permissions are granted
            if (!hasBluetoothPermission()) {
                throw SecurityException("Bluetooth permissions are not granted")
            }

            // Get the Bluetooth device by its address
            val device = bluetoothAdapter?.getRemoteDevice(address)
                ?: throw IllegalArgumentException("Device not found")

            // Create and connect the Bluetooth socket
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()

            // Initialize input and output streams
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream

            true // Connection successful
        } catch (e: SecurityException) {
            // Handle missing permissions
            e.printStackTrace()
            false
        } catch (e: IllegalArgumentException) {
            // Handle invalid device address
            e.printStackTrace()
            false
        } catch (e: Exception) {
            // Handle other exceptions (e.g., connection failure)
            e.printStackTrace()
            false
        }
    }

    fun sendMessage(message: String) {
        try {
            outputStream?.write(message.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun receiveMessage(): String? {
        return try {
            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: -1
            if (bytesRead > 0) {
                String(buffer, 0, bytesRead)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
package com.example.drinksync

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private lateinit var statusTextView: TextView
    private val MY_UUID: UUID = UUID.fromString("c0de2023-cafe-babe-cafe-2023c0debabe")
    private val REQUEST_BLUETOOTH_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusText)

        // Check and request Bluetooth permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {
            startServerInThread()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServerInThread()
            } else {
                runOnUiThread {
                    statusTextView.text = "Bluetooth permissions are required to run this app."
                }
            }
        }
    }

    private fun startServerInThread() {
        Thread { startServer() }.start()
    }

    private fun startServer() {
        try {
            Log.d("DrinkSync", "Starting Bluetooth server...")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                val serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "DrinkSyncApp", MY_UUID
                )
                Log.d("DrinkSync", "Server socket created, waiting for connection...")
                val socket = serverSocket?.accept()
                socket?.also {
                    Log.d("DrinkSync", "Client connected!")
                    runOnUiThread {
                        statusTextView.text = "Connected!"
                    }
                    val inputStream = it.inputStream
                    val outputStream = it.outputStream
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)

                    Log.d("DrinkSync", "Received message: $incomingMessage")
                    runOnUiThread {
                        statusTextView.append("\nReceived: $incomingMessage")
                    }
                    outputStream.write("Hello world!".toByteArray())
                    serverSocket?.close()
                    Log.d("DrinkSync", "Server socket closed.")
                }
            } else {
                Log.d("DrinkSync", "Bluetooth permission not granted.")
            }
        } catch (e: SecurityException) {
            Log.e("DrinkSync", "SecurityException: ${e.message}")
            runOnUiThread {
                statusTextView.text = "SecurityException: ${e.message}"
            }
        } catch (e: Exception) {
            Log.e("DrinkSync", "Error: ${e.message}")
            runOnUiThread {
                statusTextView.text = "Error: ${e.message}"
            }
        }
    }
}
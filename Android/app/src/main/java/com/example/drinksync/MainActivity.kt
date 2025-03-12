package com.example.drinksync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.drinksync.ui.theme.DrinkSyncTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothService: BluetoothService
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var deviceAddress: String = "YOUR_BLUETOOTH_DEVICE_ADDRESS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()

        // Request Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }

        bluetoothService = BluetoothService()
        bluetoothService.setBluetoothListener(object : BluetoothService.BluetoothListener {
            override fun onConnected() {
                Log.d("MainActivity", "Bluetooth Connected")
            }

            override fun onConnectionFailed() {
                Log.d("MainActivity", "Bluetooth Connection Failed")
            }

            override fun onDisconnected() {
                Log.d("MainActivity", "Bluetooth Disconnected")
            }

            override fun onMessageReceived(message: String) {
                Log.d("MainActivity", "Received: $message")
                processReceivedData(message)
            }

            override fun onMessageSent(message: String) {
                Log.d("MainActivity", "Message Sent: $message")
            }

            override fun onSendFailed() {
                Log.d("MainActivity", "Message Send Failed")
            }
        })

        setContent {
            DrinkSyncTheme {
                MainScreen(bluetoothService)
            }
        }
    }

    private fun processReceivedData(data: String) {
        scope.launch {
            val prefs = Prefs(this@MainActivity)
            withContext(Dispatchers.Main) {
                try {
                    val ounces = data.toIntOrNull()
                    if (ounces != null) {
                        val currentIntake = prefs.getInt("currentIntake", 0)
                        val newIntake = currentIntake + ounces
                        prefs.saveInt("currentIntake", newIntake)

                        val dailyGoal = prefs.getInt("dailyGoal", 64)
                        if (newIntake >= dailyGoal) {
                            val currentStreak = prefs.getInt("streak", 0)
                            prefs.saveInt("streak", currentStreak + 1)
                            bluetoothService.sendMessage("True")
                        } else {
                            bluetoothService.sendMessage("True")
                        }
                    } else {
                        Log.w("MainActivity", "Received data is not a valid integer: $data")
                        bluetoothService.sendMessage("False")
                    }
                } catch (e: NumberFormatException) {
                    Log.e("MainActivity", "Error parsing data: $data", e)
                    bluetoothService.sendMessage("False")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hydration_reminder", "Hydration Reminder", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminds you to drink water" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.closeConnection()
    }
}
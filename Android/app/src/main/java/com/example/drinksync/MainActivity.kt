package com.example.drinksync

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.drinksync.bluetooth.BluetoothPermissions
import com.example.drinksync.bluetooth.RfcommServer
import com.example.drinksync.notifications.HydrationNotifier
import com.example.drinksync.ui.MainScreen
import com.example.drinksync.ui.theme.DrinkSyncTheme

private const val TAG = "DrinkSyncApp"

/** Hosts the Compose UI and owns the RFCOMM server for the activity's lifetime. */
class MainActivity : ComponentActivity() {

    private var connectionStatus by mutableStateOf("Not Connected")
    private var incomingDrink by mutableStateOf<DrinkQueue.Event?>(null)
    private val drinkQueue = DrinkQueue()
    private var permissionRequestInFlight = false

    private val server by lazy {
        RfcommServer(
            adapter = { getSystemService(BluetoothManager::class.java)?.adapter },
            hasPermission = ::hasBluetoothConnectPermission,
            onStatus = { status -> runOnUiThread { connectionStatus = status } },
            onDrink = { grams -> runOnUiThread { onDrinkReceived(grams) } },
        )
    }

    private val requestBluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRequestInFlight = false
            if (hasBluetoothConnectPermission()) {
                server.start()
            } else {
                Log.w(TAG, "Bluetooth permission denied.")
                connectionStatus = "Bluetooth permissions are required."
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, if (isGranted) "Notification permission granted" else "Notification permission denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        HydrationNotifier.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        checkAndRequestBluetoothPermissions()

        setContent {
            DrinkSyncTheme {
                MainScreen(
                    connectionStatus = connectionStatus,
                    incomingDrink = incomingDrink,
                    onIntakeProcessed = { incomingDrink = drinkQueue.markProcessed() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Retry after the user enables Bluetooth or grants the permission in system settings.
        if (!permissionRequestInFlight && hasBluetoothConnectPermission() && !server.isAlive) {
            server.start()
        }
    }

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    private fun onDrinkReceived(grams: Double) {
        drinkQueue.enqueue(grams)
        if (incomingDrink == null) incomingDrink = drinkQueue.current
        val ozToAdd = DrinkEvent.gramsToOz(grams)
        connectionStatus = if (ozToAdd > 0) "Received $ozToAdd oz" else "Received sip (${"%.1f".format(grams)} g)"
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, BluetoothPermissions.connectPermission()) ==
            PackageManager.PERMISSION_GRANTED

    private fun checkAndRequestBluetoothPermissions() {
        val missing = BluetoothPermissions.required().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            server.start()
        } else {
            Log.i(TAG, "Requesting Bluetooth permissions: ${missing.joinToString()}")
            permissionRequestInFlight = true
            requestBluetoothPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

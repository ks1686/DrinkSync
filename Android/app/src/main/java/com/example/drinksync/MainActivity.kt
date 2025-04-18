package com.example.drinksync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.drinksync.ui.theme.DrinkSyncTheme
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.mutableStateOf // Ensure this is imported
import androidx.compose.runtime.getValue // Ensure this is imported
import androidx.compose.runtime.setValue // Ensure this is imported
import java.io.IOException // Import IOException for catch block
import kotlin.math.roundToInt // Import for rounding


// Constant for Log Tag
private const val TAG = "DrinkSyncApp"

class MainActivity : ComponentActivity() {

    // Lazy initialization of BluetoothAdapter
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    // Unique UUID for the Bluetooth service
    private val MY_UUID: UUID = UUID.fromString("c7506ec6-09d3-4979-9db3-3b85acad20fd") // Replace with your unique UUID

    // Request code for Bluetooth permissions
    private val REQUEST_BLUETOOTH_PERMISSION = 1

    // Conversion Constant: Grams per Fluid Ounce
    private val GRAMS_PER_OZ = 29.5735 // More precise value

    // Activity Result Launcher for Notification Permission
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.w(TAG, "Notification permission denied")
                // Optionally inform the user why notifications are useful
            }
        }

    // State variable for displaying Bluetooth connection status
    private var connectionStatus by mutableStateOf("Not Connected")

    // State variable to hold the amount of water intake received via Bluetooth (in ounces)
    // Nullable Int: null means no new data to process.
    private var intakeToAddFromBluetooth by mutableStateOf<Int?>(null)

    @RequiresApi(Build.VERSION_CODES.O) // Required for NotificationChannel and java.time APIs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enable drawing behind system bars
        createNotificationChannel() // Create notification channel on app start

        // Request Notification Permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Requesting Notification permission")
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check and Request Bluetooth Permissions
        checkAndRequestBluetoothPermissions()

        // Set the Compose content for the activity
        setContent {
            DrinkSyncTheme {
                // MainScreen composable, passing down connection status,
                // the intake amount from Bluetooth, and a function to reset the intake amount.
                MainScreen(
                    connectionStatus = connectionStatus,
                    intakeToAdd = intakeToAddFromBluetooth,
                    onIntakeProcessed = { intakeToAddFromBluetooth = null } // Lambda to reset the trigger
                )
            }
        }
    }

    // Handles the result of permission requests
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSION -> {
                // Check if BLUETOOTH_CONNECT permission was granted (most critical one for server)
                val connectPermissionIndex = permissions.indexOf(Manifest.permission.BLUETOOTH_CONNECT)
                if (connectPermissionIndex != -1 && grantResults.getOrNull(connectPermissionIndex) == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Bluetooth Connect permission granted.")
                    startServerInThread() // Start the server thread now that permission is granted
                } else {
                    Log.w(TAG, "Bluetooth Connect permission denied.")
                    // Update status and inform the user
                    connectionStatus = "Bluetooth permissions are required."
                    // Consider showing a dialog explaining why permissions are needed
                    // and potentially directing the user to settings.
                }
                return // Exit after handling this request code
            }
            // Handle other permission request codes if needed
        }
    }

    // Checks current Bluetooth permissions and requests them if missing
    private fun checkAndRequestBluetoothPermissions() {
        // Required permissions vary slightly by Android version
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and above require BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Older versions might need BLUETOOTH and BLUETOOTH_ADMIN,
            // but BLUETOOTH_CONNECT implies server functionality. Check specific needs.
            // For simplicity, focusing on modern requirements. Add older ones if targeting lower APIs.
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN) // Example for older APIs
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All required Bluetooth permissions already granted.")
            startServerInThread() // Start server if permissions are present
        } else {
            Log.i(TAG, "Requesting Bluetooth permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                REQUEST_BLUETOOTH_PERMISSION
            )
        }
    }


    // Creates the notification channel required for Android Oreo (API 26) and above
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = "Hydration Reminders" // Channel name visible to user
        val descriptionText = "Channel for water intake reminders and progress"
        val importance = NotificationManager.IMPORTANCE_HIGH // Importance level
        val channel = NotificationChannel("hydration_reminder", name, importance).apply {
            description = descriptionText
            // Configure additional channel settings here (e.g., lights, vibration) if desired
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel 'hydration_reminder' created.")
    }

    // Starts the Bluetooth server logic in a background thread
    private fun startServerInThread() {
        Log.d(TAG, "Attempting to start Bluetooth server thread.")
        // Basic check to avoid multiple threads; more robust checks might be needed
        // if this function could be called multiple times rapidly.
        Thread { startServer() }.start()
    }

    // --- Bluetooth Server Logic ---
    private fun startServer() {
        // Check adapter availability
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.")
            runOnUiThread { connectionStatus = "Bluetooth Not Supported" }
            return
        }
        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled == false) {
            Log.w(TAG, "Bluetooth is disabled.")
            runOnUiThread { connectionStatus = "Bluetooth Disabled" }
            // Consider prompting the user to enable Bluetooth:
            // val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT) // Requires handling onActivityResult
            return
        }


        var serverSocket: BluetoothServerSocket? = null
        var clientSocket: BluetoothSocket? = null // Rename to avoid confusion with serverSocket

        // Regex to parse "Average weight: <number> grams"
        val regex = """Average weight:\s*(\d+\.?\d*)\s*grams""".toRegex()
        Log.d(TAG, "Bluetooth server thread started. Regex defined.")

        try {
            Log.i(TAG, "Attempting to create RFCOMM server socket...")
            // Check permission right before creating the socket (important for thread safety)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_CONNECT permission missing in server thread!")
                runOnUiThread { connectionStatus = "Permission Error (Thread)" }
                return // Cannot proceed without permission
            }

            // Create a listening server socket
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("DrinkSyncApp", MY_UUID)
            Log.i(TAG, "Server socket created. Waiting for client connection...")
            runOnUiThread { connectionStatus = "Listening..." }

            // Blocking call: waits for a client to connect.
            // This will throw an IOException if the socket is closed or an error occurs.
            clientSocket = serverSocket?.accept()
            Log.i(TAG, "Client connection accepted!")
            runOnUiThread { connectionStatus = "Connected" }

            // --- Connection Established ---
            clientSocket?.also { socket -> // Use 'socket' for the connected client socket
                val inputStream = socket.inputStream
                val outputStream = socket.outputStream // For sending data back (optional)
                val buffer = ByteArray(1024) // Buffer for reading data
                var bytesRead: Int // Number of bytes read

                Log.d(TAG, "Input/Output streams obtained. Starting read loop.")
                // Loop to continuously read data from the client
                try {
                    while (true) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) {
                            // End of stream reached - client disconnected gracefully
                            Log.i(TAG, "Input stream ended (bytesRead = -1). Client disconnected.")
                            break // Exit the read loop
                        }

                        // Process the received data
                        val incomingMessage = String(buffer, 0, bytesRead).trim()
                        Log.d(TAG, "Received raw data: '$incomingMessage'")

                        // --- PARSING LOGIC ---
                        val matchResult = regex.find(incomingMessage)
                        if (matchResult != null) {
                            val gramsString = matchResult.groupValues[1]
                            val grams = gramsString.toDoubleOrNull()

                            if (grams != null && grams > 0) {
                                // Valid grams value parsed
                                val ozToAdd = (grams / GRAMS_PER_OZ).roundToInt()
                                Log.i(TAG, "Parsed $grams g -> $ozToAdd oz. Updating state.")

                                // Update the shared state on the UI thread
                                runOnUiThread {
                                    intakeToAddFromBluetooth = ozToAdd
                                    connectionStatus = "Received $ozToAdd oz" // Update status
                                    // Consider resetting status to "Connected" after a delay
                                }
                                // Optional: Send confirmation back
                                // outputStream.write("OK $grams g\n".toByteArray())

                            } else {
                                Log.w(TAG, "Regex matched, but failed to parse valid grams from '$gramsString'")
                                // outputStream.write("ERR Parse\n".toByteArray())
                            }
                        } else {
                            Log.w(TAG, "Received message format mismatch: '$incomingMessage'")
                            // outputStream.write("ERR Format\n".toByteArray())
                        }
                        // --- END PARSING ---

                        // If expecting only one message per connection, uncomment break:
                        // break
                    } // End of while loop
                } catch (e: IOException) {
                    // Handle errors during reading (e.g., connection reset)
                    Log.e(TAG, "IOException during inputStream.read: ${e.message}")
                    runOnUiThread { connectionStatus = "Connection Lost" }
                } finally {
                    // Ensure the client socket is closed when done reading
                    try {
                        Log.d(TAG, "Closing client socket.")
                        socket.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error closing client socket: ${e.message}", e)
                    }
                    // Update status if it wasn't already set to an error state
                    if (connectionStatus == "Connected" || connectionStatus.startsWith("Received")) {
                        runOnUiThread { connectionStatus = "Disconnected" }
                    }
                }
            } // End of clientSocket?.also

        } catch (e: SecurityException) {
            // Handle permission errors during listen/accept (less likely if checked before)
            Log.e(TAG, "SecurityException in Bluetooth server: ${e.message}", e)
            runOnUiThread { connectionStatus = "Security Error" }
        } catch (e: IOException) {
            // Handle errors during socket creation (listen) or connection acceptance (accept)
            Log.e(TAG, "IOException in Bluetooth server setup/accept: ${e.message}", e)
            runOnUiThread { connectionStatus = "Server Error" }
        } catch (e: Exception) {
            // Catch any other unexpected errors
            Log.e(TAG, "Unexpected error in Bluetooth server thread: ${e.message}", e)
            runOnUiThread { connectionStatus = "Unknown Server Error" }
        } finally {
            // Always attempt to close the server socket
            try {
                serverSocket?.close()
                Log.d(TAG, "Server socket closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing server socket: ${e.message}", e)
            }
            // Update status if connection was never made and no error occurred before
            if (clientSocket == null && connectionStatus !in listOf("Listening...", "Connected", "Disconnected", "Connection Lost") && !connectionStatus.contains("Error") && !connectionStatus.contains("Permission")) {
                runOnUiThread { connectionStatus = "Server Stopped / No Connection" }
            }
        }
    } // --- End of startServer ---
} // --- End of MainActivity ---


// =========================================================================
// ==                    SHARED PREFERENCES HELPER                        ==
// =========================================================================

class Prefs(context: Context) {
    // Use application context to avoid memory leaks
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("DrinkSyncPrefs", Context.MODE_PRIVATE)

    // --- Save Methods ---
    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun saveLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun saveStringSet(key: String, value: Set<String>?) {
        prefs.edit().putStringSet(key, value).apply()
    }

    fun saveString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    // --- Get Methods ---
    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        // Return a copy to prevent modification of the original set from prefs
        return prefs.getStringSet(key, defaultValue)?.toSet()
    }

    fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }
}

// =========================================================================
// ==                          COMPOSABLE SCREENS                         ==
// =========================================================================

// --- Main Screen Composable with Navigation ---
@RequiresApi(Build.VERSION_CODES.O) // Needed for child composables
@Composable
fun MainScreen(
    connectionStatus: String,
    intakeToAdd: Int?, // Intake amount from Bluetooth (oz)
    onIntakeProcessed: () -> Unit // Callback to reset the intake amount trigger
) {
    val navController = rememberNavController()
    val context = LocalContext.current // Get context for child composables

    Scaffold(
        // Bottom navigation bar
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        // Main content area
        Column(
            modifier = Modifier
                .fillMaxSize() // Fill available space
                .padding(innerPadding) // Apply padding provided by Scaffold
        ) {
            // Display Bluetooth Connection Status at the top
            Text(
                text = "Status: $connectionStatus",
                modifier = Modifier
                    .fillMaxWidth() // Take full width
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Add padding
                fontSize = 14.sp, // Slightly smaller font
                color = MaterialTheme.colorScheme.onSurfaceVariant // Use theme color
            )

            // Navigation Host for switching between screens
            NavHost(
                navController = navController,
                startDestination = "hydration", // Start on the Hydration screen
                modifier = Modifier.weight(1f) // Allow NavHost to take remaining vertical space
            ) {
                // Define composable destinations for each navigation item
                composable("hydration") {
                    HydrationScreen(
                        context = context,
                        intakeToAddFromBluetooth = intakeToAdd, // Pass down Bluetooth data
                        onIntakeProcessed = onIntakeProcessed // Pass down reset callback
                    )
                }
                composable("achievements") { AchievementScreen(context) }
                composable("settings") { SettingsScreen(context) }
            }
        }
    }
}

// --- Bottom Navigation Bar Composable ---
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    // Remember the current navigation back stack entry to determine the selected item
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        // Define navigation items
        val items = listOf(
            Triple("hydration", Icons.Filled.WaterDrop, "Hydration"),
            Triple("achievements", Icons.Filled.Star, "Achievements"),
            Triple("settings", Icons.Filled.Settings, "Settings")
        )

        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = currentRoute == route, // Highlight the selected item
                onClick = {
                    // Navigate only if not already on the selected screen
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            // Pop up to the start destination to avoid building a large back stack
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true // Save state of the popped screens
                            }
                            // Avoid multiple copies of the same destination when reselecting
                            launchSingleTop = true
                            // Restore state when reselecting a previously visited item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}


// --- Hydration Tracking Screen Composable ---
@RequiresApi(Build.VERSION_CODES.O) // For java.time and NotificationChannel logic
@Composable
fun HydrationScreen(
    context: Context,
    intakeToAddFromBluetooth: Int?, // Intake amount from Bluetooth (oz)
    onIntakeProcessed: () -> Unit // Callback to reset the trigger
) {
    // Remember an instance of the Prefs helper class
    val prefs = remember { Prefs(context) }

    // --- State Variables ---
    // Daily goal (derived state to react to changes in Settings)
    val dailyGoal by remember { derivedStateOf { prefs.getInt("dailyGoal", 64) } }
    // Current intake for the day
    var currentIntake by remember { mutableIntStateOf(prefs.getInt("currentIntake", 0)) }
    // Text field state for manual intake editing
    var editIntake by remember { mutableStateOf("") }
    // Flag for whether the daily goal has been hit today
    var hasHitGoal by remember { mutableStateOf(prefs.getBoolean("hasHitGoal", false)) }
    // Total intake accumulated over the app's lifetime
    var totalIntake by remember { mutableIntStateOf(prefs.getInt("totalIntake", 0)) }
    // Date of the last daily reset
    var lastResetDate by remember { mutableStateOf(prefs.getString("lastResetDate", LocalDate.now(ZoneId.systemDefault()).toString())) }
    // Current consecutive days streak of hitting the goal
    var streak by remember { mutableIntStateOf(prefs.getInt("streak", 0)) }
    // Whether notifications are enabled (reacts to Settings changes)
    val notificationsEnabled by remember { derivedStateOf { prefs.getBoolean("notifications", true) } }
    // Set of notification percentages already triggered today (loaded from prefs)
    val notificationTriggers = remember {
        mutableStateOf(prefs.getStringSet("notificationTriggers", emptySet())?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf())
    }

    // Achievement states (loaded from prefs)
    var firstSip by remember { mutableStateOf(prefs.getBoolean("firstSip", false)) }
    var halfwayToGoal by remember { mutableStateOf(prefs.getBoolean("halfwayToGoal", false)) }
    var hydrationHero by remember { mutableStateOf(prefs.getBoolean("hydrationHero", false)) }
    var bigGulp by remember { mutableStateOf(prefs.getBoolean("bigGulp", false)) }
    var lateNightSip by remember { mutableStateOf(prefs.getBoolean("lateNightSip", false)) }
    var earlyBirdDrinker by remember { mutableStateOf(prefs.getBoolean("earlyBirdDrinker", false)) }
    // Note: Some achievements like "Quick Drink", "Frequent Hydrator", "Total Hydration Master"
    // are checked directly in the AchievementScreen based on current state/time.

    // --- Helper Functions ---

    // Saves boolean value to SharedPreferences
    fun saveBooleanPref(key: String, value: Boolean) {
        prefs.saveBoolean(key, value)
    }

    // Saves integer value to SharedPreferences
    fun saveIntPref(key: String, value: Int) {
        prefs.saveInt(key, value)
    }

    // Saves string value to SharedPreferences
    fun saveStringPref(key: String, value: String?) {
        prefs.saveString(key, value)
    }

    // Saves Set<String> to SharedPreferences
    fun saveStringSetPref(key: String, value: Set<String>?) {
        prefs.saveStringSet(key, value)
    }

    // Sends progress notification if conditions are met
    fun sendProgressNotification(progressPercent: Int) {
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled, skipping progress notification.")
            return
        }
        // Check if notification permission is granted (important on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted, cannot send progress notification.")
            return
        }

        val notificationId = 2 // Consistent ID for progress updates

        val builder = NotificationCompat.Builder(context, "hydration_reminder")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your actual icon
            .setContentTitle("Hydration Update!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Dismiss notification when tapped

        val contentText = when (progressPercent) {
            25 -> "Great job! You're 25% of the way to your goal."
            50 -> "You're halfway there! Keep hydrating!"
            100 -> "Congratulations! You've reached your hydration goal for today! 🎉"
            else -> return // Only send for specific milestones
        }

        builder.setContentText(contentText)
        // Use NotificationManagerCompat for broader compatibility
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
                Log.i(TAG, "Sent progress notification for $progressPercent%")
            } catch (e: SecurityException) {
                // This can happen if permissions change unexpectedly
                Log.e(TAG, "SecurityException sending notification: ${e.message}", e)
            }
        }
    }


    // Core logic for logging water intake and updating state/achievements
    fun logWaterIntake(amountToAddOz: Int) {
        // Only update intake if amount is positive. Allow 0 for triggering checks after manual set.
        if (amountToAddOz > 0) {
            Log.d(TAG, "Logging $amountToAddOz oz intake.")
            currentIntake += amountToAddOz
            totalIntake += amountToAddOz
            saveIntPref("currentIntake", currentIntake)
            saveIntPref("totalIntake", totalIntake)
        } else if (amountToAddOz == 0) {
            Log.d(TAG, "logWaterIntake(0) called, performing checks only.")
        } else {
            return // Ignore negative amounts
        }


        // --- Update Achievement States ---
        if (!firstSip && currentIntake > 0) { // Check currentIntake > 0 for first sip
            firstSip = true
            saveBooleanPref("firstSip", true)
            Log.i(TAG, "Achievement unlocked: First Sip!")
        }
        val goal = dailyGoal // Use local val for checks
        if (goal > 0) { // Avoid division by zero if goal is not set
            if (currentIntake >= goal / 2 && !halfwayToGoal) {
                halfwayToGoal = true
                saveBooleanPref("halfwayToGoal", true)
                Log.i(TAG, "Achievement unlocked: Halfway to Goal!")
            } else if (currentIntake < goal / 2 && halfwayToGoal) {
                // Reset if intake drops below threshold (e.g., manual edit)
                halfwayToGoal = false
                saveBooleanPref("halfwayToGoal", false)
            }

            if (currentIntake >= goal && !hydrationHero) {
                hydrationHero = true
                saveBooleanPref("hydrationHero", true)
                Log.i(TAG, "Achievement unlocked: Hydration Hero!")
            }
        }

        // Time-based achievements (only trigger if amount added > 0)
        if (amountToAddOz > 0) {
            val currentTime = LocalTime.now()
            if (!lateNightSip && currentTime.hour >= 0 && currentTime.hour < 3) { // 12 AM to 2:59 AM
                lateNightSip = true
                saveBooleanPref("lateNightSip", true)
                Log.i(TAG, "Achievement unlocked: Late Night Sip!")
            }
            if (!earlyBirdDrinker && currentTime.hour >= 5 && currentTime.hour < 7) { // 5 AM to 6:59 AM
                earlyBirdDrinker = true
                saveBooleanPref("earlyBirdDrinker", true)
                Log.i(TAG, "Achievement unlocked: Early Bird Drinker!")
            }
        }


        // Amount-based achievement (for the specific log action, only if > 0)
        if (amountToAddOz >= 32 && !bigGulp) {
            bigGulp = true
            saveBooleanPref("bigGulp", true)
            Log.i(TAG, "Achievement unlocked: Big Gulp!")
        }

        // Update daily goal hit status
        val goalMet = if (goal > 0) currentIntake >= goal else false
        if (goalMet != hasHitGoal) {
            hasHitGoal = goalMet
            saveBooleanPref("hasHitGoal", hasHitGoal)
        }

        // --- Check and Send Notifications ---
        if (goal > 0) {
            val progressPercent = (currentIntake.toFloat() / goal.toFloat() * 100).toInt()
            val triggers = notificationTriggers.value.toMutableSet() // Work with a mutable copy
            var triggerUpdated = false

            if (progressPercent >= 25 && !triggers.contains(25)) {
                sendProgressNotification(25)
                triggers.add(25)
                triggerUpdated = true
            }
            if (progressPercent >= 50 && !triggers.contains(50)) {
                sendProgressNotification(50)
                triggers.add(50)
                triggerUpdated = true
            }
            if (progressPercent >= 100 && !triggers.contains(100)) {
                sendProgressNotification(100)
                triggers.add(100)
                triggerUpdated = true
            }

            // Save updated triggers if any were added
            if (triggerUpdated) {
                notificationTriggers.value = triggers // Update the state
                saveStringSetPref("notificationTriggers", triggers.map { it.toString() }.toSet())
            }
        }

        // Clear manual edit field after logging or setting
        editIntake = ""
    }


    // Resets daily values (intake, goal status, triggers) and updates streak
    fun resetDailyValues() {
        Log.i(TAG, "Performing daily reset...")
        val today = LocalDate.now(ZoneId.systemDefault())

        // Update streak based on *yesterday's* goal status
        if (hasHitGoal) {
            streak += 1
            Log.d(TAG, "Streak increased to $streak")
        } else {
            if (streak > 0) Log.d(TAG, "Streak reset from $streak")
            streak = 0
        }
        saveIntPref("streak", streak)

        // Reset daily counters and flags
        currentIntake = 0
        saveIntPref("currentIntake", 0)

        hasHitGoal = false
        saveBooleanPref("hasHitGoal", false)

        halfwayToGoal = false // Reset daily achievement flags
        saveBooleanPref("halfwayToGoal", false)
        // Keep lifetime achievements like firstSip, bigGulp, etc.

        // Reset notification triggers for the new day
        notificationTriggers.value = mutableSetOf()
        saveStringSetPref("notificationTriggers", emptySet())

        // Update the last reset date
        lastResetDate = today.toString()
        saveStringPref("lastResetDate", lastResetDate)

        Log.i(TAG, "Daily reset complete for $today. Current Intake: 0, Streak: $streak")
    }


    // --- Effects ---

    // Effect to process intake data received from Bluetooth
    LaunchedEffect(intakeToAddFromBluetooth) {
        if (intakeToAddFromBluetooth != null && intakeToAddFromBluetooth > 0) {
            Log.d(TAG, "LaunchedEffect triggered: Processing Bluetooth intake ($intakeToAddFromBluetooth oz)")
            logWaterIntake(intakeToAddFromBluetooth)
            onIntakeProcessed() // IMPORTANT: Reset the trigger in MainActivity
        }
    }

    // Effect to check for daily reset when the app comes into the foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lastResetDate) { // Re-run if lastResetDate changes
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(TAG, "App resumed. Checking for daily reset needed.")
                val today = LocalDate.now(ZoneId.systemDefault()).toString()
                if (today != lastResetDate) {
                    Log.i(TAG, "Date changed ($lastResetDate -> $today). Performing daily reset.")
                    resetDailyValues()
                } else {
                    Log.d(TAG, "Date ($today) hasn't changed since last reset.")
                    // Ensure notification triggers are loaded correctly on resume
                    // (in case app was killed and state lost)
                    val loadedTriggers = prefs.getStringSet("notificationTriggers", emptySet())?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
                    if (notificationTriggers.value != loadedTriggers) {
                        notificationTriggers.value = loadedTriggers
                        Log.d(TAG, "Reloaded notification triggers from prefs.")
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Cleanup observer when the effect leaves composition
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // --- UI Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Padding around the content
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top), // Space between elements, align top
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Stay Hydrated!", style = MaterialTheme.typography.headlineMedium) // Title

        // --- Progress Indicator ---
        val goal = dailyGoal // Local val for calculations
        val progress = if (goal > 0) currentIntake.toFloat() / goal.toFloat() else 0f
        val cappedProgress = minOf(progress, 1f) // Cap progress at 100% for the bar
        // Change color when goal is exceeded
        val progressColor = if (progress <= 1f) MaterialTheme.colorScheme.primary else Color(0xFF66BB6A) // Green when over goal

        LinearProgressIndicator(
            progress = { cappedProgress }, // Use lambda for state reading
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp), // Make the indicator thicker
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, // Background color
        )

        // Text showing current intake vs goal
        Text(
            text = "$currentIntake oz / $goal oz",
            style = MaterialTheme.typography.bodyLarge
        )

        // Display message when goal is exceeded
        if (currentIntake > goal && goal > 0) {
            val surplus = currentIntake - goal
            Text(
                text = "✅ Goal surpassed by $surplus oz!",
                color = Color(0xFF66BB6A), // Green color
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp)) // Add some space

        // --- Action Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button to log a standard amount (e.g., 8 oz)
            Button(onClick = { logWaterIntake(8) }) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Log 8 oz")
            }
        }

        Spacer(modifier = Modifier.height(8.dp)) // Add some space

        // --- Manual Edit Section ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.wrapContentSize() // Don't take full width
        ) {
            // Text field for manually editing/setting the current intake
            OutlinedTextField(
                value = editIntake,
                onValueChange = {
                    // Allow only digits, limit length if desired
                    if (it.all { char -> char.isDigit() } && it.length <= 4) {
                        editIntake = it
                    }
                },
                label = { Text("Set Intake (oz)") },
                singleLine = true,
                // keyboardOptions removed here
                modifier = Modifier.width(150.dp) // Adjust width as needed
            )

            // Button to apply the manually entered intake value
            Button(
                onClick = {
                    val newIntake = editIntake.toIntOrNull()
                    if (newIntake != null && newIntake >= 0) {
                        Log.d(TAG, "Manually setting intake to $newIntake oz")
                        // Calculate difference for total intake adjustment
                        val intakeDifference = newIntake - currentIntake
                        // Update total intake before changing currentIntake
                        totalIntake += intakeDifference
                        saveIntPref("totalIntake", totalIntake)

                        // Directly set current intake
                        currentIntake = newIntake
                        // Call logWaterIntake with 0 to trigger achievement/notification checks
                        // without adding more oz to the already set value.
                        logWaterIntake(0)
                    }
                },
                // Enable button only if the input field contains a valid number
                enabled = editIntake.toIntOrNull() != null
            ) {
                Text("Update")
            }
        }
    }
}


// --- Achievements Screen Composable ---
@RequiresApi(Build.VERSION_CODES.O) // For java.time checks if needed
@Composable
fun AchievementScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    // --- Achievement States (using derivedStateOf for reactivity) ---
    val currentStreak by remember { derivedStateOf { prefs.getInt("streak", 0) } }
    val firstSip by remember { derivedStateOf { prefs.getBoolean("firstSip", false) } }
    val halfwayToGoal by remember { derivedStateOf { prefs.getBoolean("halfwayToGoal", false) } }
    val hydrationHero by remember { derivedStateOf { prefs.getBoolean("hydrationHero", false) } }
    val totalIntake by remember { derivedStateOf { prefs.getInt("totalIntake", 0) } }
    val lateNightSip by remember { derivedStateOf { prefs.getBoolean("lateNightSip", false) } }
    val earlyBirdDrinker by remember { derivedStateOf { prefs.getBoolean("earlyBirdDrinker", false) } }
    val bigGulp by remember { derivedStateOf { prefs.getBoolean("bigGulp", false) } }

    // Note: Some achievements might need more complex logic or state not easily derived here,
    // e.g., "Frequent Hydrator" would require tracking daily log counts,
    // "Quick Drink" requires comparing log time with app open time.
    // These are simplified here based on the available state.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top), // Space items, align top
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Achievements", style = MaterialTheme.typography.headlineMedium)

        // Display current streak
        Text(
            text = "Current Streak: $currentStreak day${if (currentStreak != 1) "s" else ""}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // --- List of Achievements ---
        // Use a LazyColumn if the list becomes very long
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start // Align achievement text left
        ) {
            AchievementItem(
                achievementName = "First Sip",
                description = "Log your first water intake.",
                isAchieved = firstSip
            )
            AchievementItem(
                achievementName = "Halfway Hydrated",
                description = "Reach 50% of your daily goal.",
                isAchieved = halfwayToGoal
            )
            AchievementItem(
                achievementName = "Hydration Hero",
                description = "Reach 100% of your daily goal.",
                isAchieved = hydrationHero
            )
            AchievementItem(
                achievementName = "Big Gulp",
                description = "Log 32 oz or more in a single session.",
                isAchieved = bigGulp
            )
            AchievementItem(
                achievementName = "Early Bird Drinker",
                description = "Log water between 5:00 AM and 7:00 AM.",
                isAchieved = earlyBirdDrinker
            )
            AchievementItem(
                achievementName = "Late Night Sip",
                description = "Log water between 12:00 AM and 3:00 AM.",
                isAchieved = lateNightSip
            )
            AchievementItem(
                achievementName = "Hydration Master (1000 oz)", // Example total intake achievement
                description = "Log over 1,000 oz of water in total.",
                isAchieved = totalIntake >= 1000
            )
            AchievementItem(
                achievementName = "Hydration Legend (10000 oz)", // Example total intake achievement
                description = "Log over 10,000 oz of water in total.",
                isAchieved = totalIntake >= 10000
            )

            // Streak-based achievements (conditional display)
            if (currentStreak >= 7) {
                AchievementItem(
                    achievementName = "One Week Warrior",
                    description = "Maintain a 7-day hydration streak.",
                    isAchieved = true // Implicitly true if displayed
                )
            }
            if (currentStreak >= 30) {
                AchievementItem(
                    achievementName = "Consistent Hydrator",
                    description = "Maintain a 30-day hydration streak!",
                    isAchieved = true // Implicitly true if displayed
                )
            }
            // Add more achievements as needed
        }
    }
}

// --- Composable for displaying a single achievement item ---
@Composable
fun AchievementItem(achievementName: String, description: String, isAchieved: Boolean) {
    val icon = if (isAchieved) "✅" else "🔒" // Use emoji for achieved/locked status
    val textColor = if (isAchieved) LocalContentColor.current else Color.Gray // Dim locked achievements

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(icon, fontSize = 18.sp) // Emoji icon
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = achievementName,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f) // Slightly more transparent description
            )
        }
    }
}


// --- Settings Screen Composable ---
@Composable
fun SettingsScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    // State for notification toggle
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    // State for daily goal text input
    var dailyGoalText by remember { mutableStateOf(prefs.getInt("dailyGoal", 64).toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // --- Notification Toggle ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // Push elements to sides
        ) {
            Text("Enable Notifications", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { newValue ->
                    notificationsEnabled = newValue
                    prefs.saveBoolean("notifications", newValue) // Save immediately
                    Log.d(TAG, "Notifications setting changed to: $newValue")
                }
            )
        }

        Divider() // Visual separator

        // --- Daily Goal Setting ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Daily Goal (oz):", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField( // Use OutlinedTextField for better visuals
                value = dailyGoalText,
                onValueChange = { text ->
                    // Allow only digits, limit length
                    if (text.all { it.isDigit() } && text.length <= 4) {
                        dailyGoalText = text
                        // Save valid integer goals immediately
                        val newGoal = text.toIntOrNull()
                        if (newGoal != null && newGoal > 0) {
                            prefs.saveInt("dailyGoal", newGoal)
                            Log.d(TAG, "Daily goal saved: $newGoal oz")
                        } else if (newGoal == 0) {
                            // Handle zero goal if necessary, maybe disable features?
                            prefs.saveInt("dailyGoal", 0) // Save 0 if needed
                            Log.d(TAG, "Daily goal saved as 0 oz")
                        }
                    }
                },
                singleLine = true,
                // keyboardOptions removed here
                modifier = Modifier.width(100.dp) // Adjust width
            )
        }

        Divider()

        // --- Bluetooth Settings Button ---
        Button(
            onClick = {
                Log.d(TAG, "Opening Bluetooth settings.")
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open Bluetooth settings: ${e.message}", e)
                    // Optionally show a toast message to the user
                }
            },
            modifier = Modifier.fillMaxWidth() // Make button take full width
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Bluetooth Settings")
        }

        // Add more settings options here (e.g., reset data, change units)
    }
}

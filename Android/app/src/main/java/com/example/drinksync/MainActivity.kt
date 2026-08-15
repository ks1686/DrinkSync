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

    @Volatile
    private var serverRunning = false
    private var serverThread: Thread? = null
    private var listenSocket: BluetoothServerSocket? = null
    private var activeClient: BluetoothSocket? = null

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

    override fun onDestroy() {
        serverRunning = false
        try {
            activeClient?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing client socket: ${e.message}", e)
        }
        try {
            listenSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket: ${e.message}", e)
        }
        super.onDestroy()
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
        if (serverThread?.isAlive == true) {
            Log.d(TAG, "Bluetooth server thread already running.")
            return
        }
        Log.d(TAG, "Attempting to start Bluetooth server thread.")
        serverRunning = true
        serverThread = Thread { startServer() }.also { it.start() }
    }

    // --- Bluetooth Server Logic ---
    private fun startServer() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.")
            runOnUiThread { connectionStatus = "Bluetooth Not Supported" }
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            Log.w(TAG, "Bluetooth is disabled.")
            runOnUiThread { connectionStatus = "Bluetooth Disabled" }
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission missing in server thread!")
            runOnUiThread { connectionStatus = "Permission Error (Thread)" }
            return
        }

        Log.d(TAG, "Bluetooth server thread started.")
        try {
            listenSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("DrinkSyncApp", MY_UUID)
            runOnUiThread { connectionStatus = "Listening..." }
            while (serverRunning) {
                val client = try {
                    listenSocket?.accept()
                } catch (e: IOException) {
                    if (serverRunning) {
                        Log.e(TAG, "IOException accepting client: ${e.message}")
                        runOnUiThread { connectionStatus = "Server Error" }
                    }
                    break
                } ?: break
                activeClient = client
                runOnUiThread { connectionStatus = "Connected" }
                serveClient(client)
                activeClient = null
                if (serverRunning) {
                    runOnUiThread { connectionStatus = "Listening..." }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in Bluetooth server: ${e.message}", e)
            runOnUiThread { connectionStatus = "Security Error" }
        } catch (e: IOException) {
            Log.e(TAG, "IOException in Bluetooth server setup: ${e.message}", e)
            runOnUiThread { connectionStatus = "Server Error" }
        } finally {
            try {
                listenSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing server socket: ${e.message}", e)
            }
            listenSocket = null
            if (serverRunning) {
                runOnUiThread { connectionStatus = "Server Stopped / No Connection" }
            }
        }
    }

    private fun serveClient(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream
        val buffer = ByteArray(1024)
        try {
            while (serverRunning) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    Log.i(TAG, "Client disconnected.")
                    break
                }
                val incomingMessage = String(buffer, 0, bytesRead).trim()
                Log.d(TAG, "Received raw data: '$incomingMessage'")
                val grams = DrinkEvent.parseGrams(incomingMessage)
                if (grams != null) {
                    val ozToAdd = DrinkEvent.gramsToOz(grams)
                    runOnUiThread {
                        intakeToAddFromBluetooth = ozToAdd
                        connectionStatus = "Received $ozToAdd oz"
                    }
                    outputStream.write("OK\n".toByteArray())
                } else {
                    Log.w(TAG, "Received message format mismatch: '$incomingMessage'")
                }
            }
        } catch (e: IOException) {
            if (serverRunning) {
                Log.e(TAG, "IOException during inputStream.read: ${e.message}")
                runOnUiThread { connectionStatus = "Connection Lost" }
            }
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket: ${e.message}", e)
            }
        }
    }
} // --- End of MainActivity ---


// =========================================================================
// ==                    SHARED PREFERENCES HELPER                        ==
// =========================================================================

class Prefs(context: Context) : PrefsLike {
    // Use application context to avoid memory leaks
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("DrinkSyncPrefs", Context.MODE_PRIVATE)

    override fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun saveLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun saveStringSet(key: String, value: Set<String>?) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override fun saveString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        return prefs.getStringSet(key, defaultValue)?.toSet()
    }

    override fun getString(key: String, defaultValue: String?): String? {
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
    val store = remember { HydrationStore(Prefs(context)) }

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
                        store = store,
                        intakeToAddFromBluetooth = intakeToAdd,
                        onIntakeProcessed = onIntakeProcessed
                    )
                }
                composable("achievements") { AchievementScreen(store) }
                composable("settings") { SettingsScreen(context, store) }
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
    store: HydrationStore,
    intakeToAddFromBluetooth: Int?, // Intake amount from Bluetooth (oz)
    onIntakeProcessed: () -> Unit // Callback to reset the trigger
) {
    val state = store.snapshot
    var editIntake by remember { mutableStateOf("") }

    fun sendProgressNotification(progressPercent: Int) {
        if (!state.notifications) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val contentText = when (progressPercent) {
            25 -> "Great job! You're 25% of the way to your goal."
            50 -> "You're halfway there! Keep hydrating!"
            100 -> "Congratulations! You've reached your hydration goal for today! 🎉"
            else -> return
        }
        val builder = NotificationCompat.Builder(context, "hydration_reminder")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hydration Update!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(2, builder.build())
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException sending notification: ${e.message}", e)
            }
        }
    }

    fun maybeNotify(after: HydrationSnapshot) {
        val goal = after.dailyGoal
        if (goal <= 0) return
        val progressPercent = (after.currentIntake.toFloat() / goal.toFloat() * 100).toInt()
        listOf(25, 50, 100).forEach { milestone ->
            if (progressPercent >= milestone && milestone !in after.notificationTriggers) {
                sendProgressNotification(milestone)
                store.markNotificationTrigger(milestone)
            }
        }
    }

    fun logWaterIntake(amountToAddOz: Int) {
        store.addIntake(amountToAddOz)
        maybeNotify(store.snapshot)
        editIntake = ""
    }

    LaunchedEffect(intakeToAddFromBluetooth) {
        if (intakeToAddFromBluetooth != null && intakeToAddFromBluetooth > 0) {
            logWaterIntake(intakeToAddFromBluetooth)
            onIntakeProcessed()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                store.applyDailyResetIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        val goal = state.dailyGoal
        val currentIntake = state.currentIntake
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
                        store.setIntake(newIntake)
                        maybeNotify(store.snapshot)
                        editIntake = ""
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
fun AchievementScreen(store: HydrationStore) {
    val state = store.snapshot
    val currentStreak = state.streak
    val firstSip = state.firstSip
    val halfwayToGoal = state.halfwayToGoal
    val hydrationHero = state.hydrationHero
    val totalIntake = state.totalIntake
    val lateNightSip = state.lateNightSip
    val earlyBirdDrinker = state.earlyBirdDrinker
    val bigGulp = state.bigGulp

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
fun SettingsScreen(context: Context, store: HydrationStore) {
    val state = store.snapshot
    var dailyGoalText by remember(state.dailyGoal) { mutableStateOf(state.dailyGoal.toString()) }

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
                checked = state.notifications,
                onCheckedChange = { newValue ->
                    store.setNotifications(newValue)
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
                        if (newGoal != null) {
                            store.setDailyGoal(newGoal)
                            Log.d(TAG, "Daily goal saved: $newGoal oz")
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

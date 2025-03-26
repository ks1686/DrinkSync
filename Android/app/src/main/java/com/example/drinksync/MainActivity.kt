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
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.drinksync.ui.theme.DrinkSyncTheme
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalTime
import java.time.Duration
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private val MY_UUID: UUID = UUID.fromString("c7506ec6-09d3-4979-9db3-3b85acad20fd")
    private val REQUEST_BLUETOOTH_PERMISSION = 1
    private var connectionStatus by mutableStateOf("Not Connected")

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()

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

        setContent {
            DrinkSyncTheme {
                MainScreen(connectionStatus)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, // Changed from Array<out String> to Array<String>
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServerInThread()
            } else {
                connectionStatus = "Bluetooth permissions are required to run this app."
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
                        connectionStatus = "Connected!"
                    }
                    val inputStream = it.inputStream
                    val outputStream = it.outputStream
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)

                    Log.d("DrinkSync", "Received message: $incomingMessage")
                    runOnUiThread {
                        connectionStatus = "Received: $incomingMessage"
                    }
                    outputStream.write("Hello world!".toByteArray())
                    serverSocket?.close()
                    Log.d("DrinkSync", "Server socket closed.")
                }
            } else {
                Log.d("DrinkSync", "Bluetooth permission not granted.")
                connectionStatus = "Bluetooth permission not granted."
            }
        } catch (e: SecurityException) {
            Log.e("DrinkSync", "SecurityException: ${e.message}")
            connectionStatus = "SecurityException: ${e.message}"
        } catch (e: Exception) {
            Log.e("DrinkSync", "Error: ${e.message}")
            connectionStatus = "Error: ${e.message}"
        }
    }
}

// SharedPreferences helper class for storing and retrieving data
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("DrinkSyncPrefs", Context.MODE_PRIVATE)

    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun saveLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun saveStringSet(key: String, value: Set<String>?) {
        prefs.edit().putStringSet(key, value).apply()
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        return prefs.getStringSet(key, defaultValue)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(connectionStatus: String) {
    val navController = rememberNavController()
    val context = LocalContext.current // Get app context

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Display Connection Status at the top
            Text(
                text = "Connection Status: $connectionStatus",
                modifier = Modifier.padding(8.dp),
                fontSize = 16.sp
            )

            NavHost(
                navController, startDestination = "hydration",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("hydration") { HydrationScreen(context) }
                composable("achievements") { AchievementScreen(context) }
                composable("settings") { SettingsScreen(context) }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.WaterDrop, contentDescription = "Hydration") },
            label = { Text("Hydration") },
            selected = false,
            onClick = { navController.navigate("hydration") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Star, contentDescription = "Achievements") },
            label = { Text("Achievements") },
            selected = false,
            onClick = { navController.navigate("achievements") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = { navController.navigate("settings") }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HydrationScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    val dailyGoal by remember { mutableIntStateOf(prefs.getInt("dailyGoal", 64)) }
    var currentIntake by remember { mutableIntStateOf(prefs.getInt("currentIntake", 0)) }
    var editIntake by remember { mutableStateOf("") }
    var hasHitGoal by remember {
        mutableStateOf(prefs.getBoolean("hasHitGoal", false))
    }

    // New states for achievements:
    var firstSip by remember { mutableStateOf(prefs.getBoolean("firstSip", false)) }
    var halfwayToGoal by remember { mutableStateOf(prefs.getBoolean("halfwayToGoal", false)) }
    var hydrationHero by remember { mutableStateOf(prefs.getBoolean("hydrationHero", false)) }
    var bigGulp by remember { mutableStateOf(prefs.getBoolean("bigGulp", false)) }

    // Total water intake across app usage
    var totalIntake by remember { mutableIntStateOf(prefs.getInt("totalIntake", 0)) }

    // Time app was opened (for "Quick Drink" achievement)
    val appOpenTime = remember { mutableStateOf(System.currentTimeMillis()) }

    // Number of water intake logs in the current day (for "Frequent Hydrator")
    var dailyLogCount by remember { mutableIntStateOf(0) }

    //Boolean for if a time between 12 AM and 3 AM was logged
    var lateNightSip by remember { mutableStateOf(prefs.getBoolean("lateNightSip", false)) }

    //Boolean for if a time between 5 AM and 7 AM was logged
    var earlyBirdDrinker by remember { mutableStateOf(prefs.getBoolean("earlyBirdDrinker", false)) }

    // Function to save the boolean
    fun saveHasHitGoal(context: Context, value: Boolean) {
        val editor = Prefs(context)
        editor.saveBoolean("hasHitGoal", value)
    }

    fun saveAchievement(key: String, value: Boolean) {
        prefs.saveBoolean(key, value)
    }

    fun saveTotalIntake(intake: Int) {
        prefs.saveInt("totalIntake", intake)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Stay Hydrated!", fontSize = 24.sp)

        val progress = currentIntake / dailyGoal.toFloat()
        val cappedProgress = minOf(progress, 1f)
        val progressColor = if (progress <= 1f) Color(0xFF2196F3) else Color(0xFF66BB6A)

        LinearProgressIndicator(
            progress = { cappedProgress },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor
        )
        if (currentIntake > dailyGoal) {
            val surplus = currentIntake - dailyGoal
            Text(
                text = "✅ You’ve passed your goal by $surplus oz!",
                color = Color(0xFF66BB6A),
                fontSize = 16.sp
            )
        }

        Text("Intake: $currentIntake oz / $dailyGoal oz")

        // Log 8 oz button
        Button(
            onClick = {
                // Allow logging up to the daily goal, and beyond.
                val intakeAmount = 8
                currentIntake += intakeAmount
                totalIntake += intakeAmount
                saveTotalIntake(totalIntake)
                prefs.saveInt("currentIntake", currentIntake)
                dailyLogCount++ //Increment log counter

                // Achievement logic
                if (!firstSip) {
                    firstSip = true
                    saveAchievement("firstSip", true)
                }

                // Check for Halfway to Goal Achievement
                if (currentIntake >= dailyGoal / 2) {
                    halfwayToGoal = true
                    saveAchievement("halfwayToGoal", true)
                }

                if (currentIntake >= dailyGoal && !hydrationHero) {
                    hydrationHero = true
                    saveAchievement("hydrationHero", true)
                }
                // Check time of day for Late Night Sip
                val currentTime = LocalTime.now()
                if (!lateNightSip && currentTime.isAfter(LocalTime.MIDNIGHT) && currentTime.isBefore(
                        LocalTime.of(3, 0)
                    )
                ) {
                    lateNightSip = true
                    saveAchievement("lateNightSip", true)
                }

                // Check time of day for Morning Dew
                if (!earlyBirdDrinker && currentTime.isAfter(LocalTime.of(5, 0)) && currentTime.isBefore(
                        LocalTime.of(7, 0)
                    )
                ) {
                    earlyBirdDrinker = true
                    saveAchievement("earlyBirdDrinker", true)
                }
                // Set Big Gulp boolean when 32 oz or more is logged in a session
                if (intakeAmount >= 32 && !bigGulp) {
                    bigGulp = true
                    saveAchievement("bigGulp", true)
                }
                // Update hasHitGoal based on the new intake
                hasHitGoal = currentIntake >= dailyGoal
                saveHasHitGoal(context, hasHitGoal) // Also save the updated value to prefs

            }
        ) {
            Text("Log 8 oz")
        }

        // Smaller edit intake field + update button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            OutlinedTextField(
                value = editIntake,
                onValueChange = { editIntake = it },
                label = { Text("Edit Intake") },
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )

            Button(
                onClick = {
                    val newIntake = editIntake.toIntOrNull()
                    if (newIntake != null && newIntake >= 0) {
                        // Update the current intake AND total intake
                        val intakeDifference = newIntake - currentIntake // Calculate the difference
                        currentIntake = newIntake
                        totalIntake += intakeDifference // Update total intake

                        prefs.saveInt("currentIntake", currentIntake)
                        saveTotalIntake(totalIntake)

                        // Update hasHitGoal based on the new intake
                        hasHitGoal = currentIntake >= dailyGoal
                        saveHasHitGoal(context, hasHitGoal) // Also save the updated value to prefs

                        // Reset halfwayToGoal if currentIntake < dailyGoal / 2
                        if (currentIntake < dailyGoal / 2) {
                            halfwayToGoal = false
                            saveAchievement("halfwayToGoal", false)
                        }
                    }
                }
            ) {
                Text("Update")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AchievementScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    // This is crucial:  Fetch the value from prefs EACH TIME the composable is drawn.
    val hasHitGoal by remember {
        derivedStateOf { prefs.getBoolean("hasHitGoal", false) }
    }

    val streak by remember { mutableIntStateOf(prefs.getInt("streak", 0)) }

    // Achievement States - Fetch from Prefs
    val firstSip by remember { derivedStateOf { prefs.getBoolean("firstSip", false) } }
    val halfwayToGoal by remember { derivedStateOf { prefs.getBoolean("halfwayToGoal", false) } }
    val hydrationHero by remember { derivedStateOf { prefs.getBoolean("hydrationHero", false) } }
    val totalIntake by remember { derivedStateOf { prefs.getInt("totalIntake", 0) } }
    val appOpenTimeState = remember { mutableStateOf(System.currentTimeMillis()) } // Correctly declared outside button
    val lastLogTime = remember { mutableStateOf(prefs.getLong("lastLogTime", 0L)) }
    val thirstResponder by remember {
        derivedStateOf {
            prefs.getStringSet("dailyLogs", emptySet())?.size ?: 0
        }
    }
    val lateNightSip by remember { derivedStateOf { prefs.getBoolean("lateNightSip", false) } }
    val earlyBirdDrinker by remember { derivedStateOf { prefs.getBoolean("earlyBirdDrinker", false) } }
    val bigGulp by remember { derivedStateOf { prefs.getBoolean("bigGulp", false) } }

    // Update AppOpenTime
    val appOpenTime = remember { mutableStateOf(System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Streak: $streak days", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Achievements Unlocked:")

        // Conditional display for "Hit Daily Goal!" achievement
        val dailyGoalEmoji = if (hasHitGoal) "✅" else "\uD83D\uDD12" // Use a locked emoji when not achieved
        Text("$dailyGoalEmoji Hit Daily Goal!")

        // Achievements with locked/unlocked status
        AchievementItem(
            achievementName = "First Sip!",
            isAchieved = firstSip,
            description = "Log your first water intake."
        )
        AchievementItem(
            achievementName = "Halfway to Goal!",
            isAchieved = halfwayToGoal,
            description = "Reach 50% of your daily hydration goal."
        )
        AchievementItem(
            achievementName = "Hydration Hero!",
            isAchieved = hydrationHero,
            description = "Hit your daily hydration goal for the first time."
        )
        AchievementItem(
            achievementName = "Total Hydration Master (10,000 oz)!",
            isAchieved = totalIntake >= 10000,
            description = "Log over 10,000 oz of water in total."
        )
        AchievementItem(
            achievementName = "Quick Drink",
            isAchieved = (System.currentTimeMillis() - appOpenTime.value) < 300000,
            description = "Log water within 5 minutes of opening the app."
        )
        AchievementItem(
            achievementName = "Frequent Hydrator",
            isAchieved = thirstResponder >= 5,
            description = "Log water intake at least 5 times in a single day."
        )
        AchievementItem(
            achievementName = "Late Night Sip",
            isAchieved = lateNightSip,
            description = "Log water intake between 12:00 AM and 3:00 AM."
        )
        AchievementItem(
            achievementName = "Early Bird Drinker",
            isAchieved = earlyBirdDrinker,
            description = "Log water intake between 5:00 AM and 7:00 AM."
        )
        AchievementItem(
            achievementName = "Big Gulp",
            isAchieved = bigGulp,
            description = "Log 32 oz or more of water in one logging session."
        )
        if (streak >= 7) Text("🏅 One-Week Warrior!")
        if (streak >= 30) Text("🔥 Consistent Hydrator - 30-day streak!")
    }
}

@Composable
fun AchievementItem(achievementName: String, isAchieved: Boolean, description: String) {
    val emoji = if (isAchieved) "✅" else "\uD83D\uDD12"
    Column {
        Text("$emoji $achievementName")
        Text(
            text = description,
            fontSize = 12.sp,
            color = Color.Gray
        ) // Smaller, grayed-out description
    }
}

@Composable
fun SettingsScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var dailyGoal by remember { mutableIntStateOf(prefs.getInt("dailyGoal", 64)) }
    var dailyGoalText by remember { mutableStateOf(dailyGoal.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", fontSize = 24.sp)

        // Notifications toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications")
            Switch(checked = notificationsEnabled, onCheckedChange = {
                notificationsEnabled = it
                prefs.saveBoolean("notifications", notificationsEnabled)
            })
        }

        // TextField for Daily Goal
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily Goal (oz): ")
            Box(
                modifier = Modifier
                    .width(75.dp)
                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(6.dp))
                    .padding(4.dp)
            ) {
                BasicTextField(
                    value = dailyGoalText,
                    onValueChange = { text ->
                        dailyGoalText = text
                        val newGoal = text.toIntOrNull()
                        if (newGoal != null) {
                            dailyGoal = newGoal
                            prefs.saveInt("dailyGoal", dailyGoal)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        // Bluetooth Button - Opens Bluetooth Settings
        Button(onClick = {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Connect Bluetooth Tracker")
        }
    }
}
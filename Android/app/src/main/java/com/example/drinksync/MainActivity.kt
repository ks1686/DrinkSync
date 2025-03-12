package com.example.drinksync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.drinksync.ui.theme.DrinkSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            DrinkSyncTheme {
                MainScreen()
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
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current // Get app context

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
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

@Composable
fun HydrationScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    val dailyGoal by remember { mutableIntStateOf(prefs.getInt("dailyGoal", 64)) }
    var currentIntake by remember { mutableIntStateOf(prefs.getInt("currentIntake", 0)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Stay Hydrated!", fontSize = 24.sp)
        LinearProgressIndicator(
            progress = { currentIntake / dailyGoal.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = Color.Blue
        )
        Text("Intake: $currentIntake oz / $dailyGoal oz")

        Button(onClick = {
            if (currentIntake < dailyGoal) {
                currentIntake += 8
                prefs.saveInt("currentIntake", currentIntake) // Save intake
            }
        }) {
            Text("Log 8 oz")
        }
    }
}

@Composable
fun AchievementScreen(context: Context) {
    val prefs = remember { Prefs(context) }
    val streak by remember { mutableIntStateOf(prefs.getInt("streak", 0)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Streak: $streak days", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Achievements Unlocked:")
        if (streak >= 7) Text("🏅 Hydration Hero - 7-day streak!")
        if (streak >= 30) Text("🔥 Ultimate Hydration Master - 30-day streak!")
    }
}

@Composable
fun SettingsScreen(context: Context) {
    val prefs = remember { Prefs(context) }

    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var dailyGoal by remember { mutableIntStateOf(prefs.getInt("dailyGoal", 64)) }
    var dailyGoalText by remember { mutableStateOf(dailyGoal.toString()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
            BasicTextField(
                value = dailyGoalText,
                onValueChange = { text ->
                    dailyGoalText = text
                    val newGoal = text.toIntOrNull()
                    if (newGoal != null) {
                        dailyGoal = newGoal
                        prefs.saveInt("dailyGoal", dailyGoal)
                    }
                }
            )
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

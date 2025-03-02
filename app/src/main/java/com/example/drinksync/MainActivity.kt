package com.example.drinksync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController, startDestination = "hydration",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("hydration") { HydrationScreen() }
            composable("achievements") { AchievementScreen() }
            composable("settings") { SettingsScreen() }
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
            icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Achievements") },
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
fun HydrationScreen() {
    val dailyGoal by remember { mutableIntStateOf(64) }
    var currentIntake by remember { mutableIntStateOf(0) }

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
        Button(onClick = { if (currentIntake < dailyGoal) currentIntake += 8 }) {
            Text("Log 8 oz")
        }
    }
}

@Composable
fun AchievementScreen() {
    val streak by remember { mutableIntStateOf(0) }
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
fun SettingsScreen() {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var dailyGoal by remember { mutableStateOf("64") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", fontSize = 24.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications")
            Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
        }

        Text("Set Daily Hydration Goal (oz)")
        TextField(
            value = dailyGoal,
            onValueChange = { dailyGoal = it.filter { char -> char.isDigit() } },
            label = { Text("Daily Goal") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Connect Bluetooth Tracker")
        }
    }
}

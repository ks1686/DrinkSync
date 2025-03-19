package com.example.drinksync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings

@Composable
fun MainScreen(bluetoothService: BluetoothService) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var currentIntake by remember { mutableStateOf(prefs.getInt("currentIntake", 0)) }
    val dailyGoal = prefs.getInt("dailyGoal", 64)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Current Water Intake: \$currentIntake oz",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(
            onClick = {
                val newIntake = currentIntake + 8
                currentIntake = newIntake
                prefs.saveInt("currentIntake", newIntake)
                if (newIntake >= dailyGoal) {
                    val currentStreak = prefs.getInt("streak", 0)
                    prefs.saveInt("streak", currentStreak + 1)
                    bluetoothService.sendMessage("True")
                } else {
                    bluetoothService.sendMessage("True")
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(text = "Add 8 oz")
        }
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        ) {
            Text(text = "Open Settings")
        }
    }
}
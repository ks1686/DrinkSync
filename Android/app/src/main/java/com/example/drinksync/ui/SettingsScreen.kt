package com.example.drinksync.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.drinksync.HydrationStore

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
                    Log.d(UI_TAG, "Notifications setting changed to: $newValue")
                }
            )
        }

        HorizontalDivider() // Visual separator

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
                            Log.d(UI_TAG, "Daily goal saved: $newGoal oz")
                        }
                    }
                },
                singleLine = true,
                // keyboardOptions removed here
                modifier = Modifier.width(100.dp) // Adjust width
            )
        }

        HorizontalDivider()

        // --- Bluetooth Settings Button ---
        Button(
            onClick = {
                Log.d(UI_TAG, "Opening Bluetooth settings.")
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(UI_TAG, "Could not open Bluetooth settings: ${e.message}", e)
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

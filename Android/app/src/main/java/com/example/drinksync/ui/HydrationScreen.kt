package com.example.drinksync.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.drinksync.HydrationStore
import com.example.drinksync.notifications.HydrationNotifier

// --- Hydration Tracking Screen Composable ---
@Composable
fun HydrationScreen(
    context: Context,
    store: HydrationStore,
) {
    val state = store.snapshot
    var editIntake by remember { mutableStateOf("") }

    fun logWaterIntake(amountToAddOz: Int) {
        store.addIntake(amountToAddOz)
        HydrationNotifier.notifyMilestones(context, store)
        editIntake = ""
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
                        HydrationNotifier.notifyMilestones(context, store)
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

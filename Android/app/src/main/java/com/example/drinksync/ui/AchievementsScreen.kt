package com.example.drinksync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drinksync.HydrationStore

// --- Achievements Screen Composable ---
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

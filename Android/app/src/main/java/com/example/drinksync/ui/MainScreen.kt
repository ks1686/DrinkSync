package com.example.drinksync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.drinksync.DrinkQueue
import com.example.drinksync.HydrationStore
import com.example.drinksync.data.SharedPrefs
import com.example.drinksync.notifications.HydrationNotifier

internal const val UI_TAG = "DrinkSyncApp"

// --- Main Screen Composable with Navigation ---
@Composable
fun MainScreen(
    connectionStatus: String,
    incomingDrink: DrinkQueue.Event?,
    onIntakeProcessed: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current // Get context for child composables
    val store = remember { HydrationStore(SharedPrefs(context)) }

    LaunchedEffect(incomingDrink) {
        val grams = incomingDrink?.grams
        if (grams != null) {
            if (grams > 0) {
                store.addGrams(grams)
                HydrationNotifier.notifyMilestones(context, store)
            }
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

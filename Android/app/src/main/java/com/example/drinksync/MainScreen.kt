package com.example.drinksync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.launch

@Composable
fun MainScreen(bluetoothService: BluetoothService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pull list of paired devices asynchronously
    val pairedDevices by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = bluetoothService.getPairedDevicesNamesAndAddresses()
    }

    // Track which device is selected from the list
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }
    // Simple control to open/close a dropdown menu
    var devicesMenuExpanded by remember { mutableStateOf(false) }
    // Track sync status
    var syncStatus by remember { mutableStateOf("Not Synced") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dropdown menu to select a paired device
        Box {
            Button(
                onClick = { devicesMenuExpanded = !devicesMenuExpanded },
                modifier = Modifier.padding(bottom = 8.dp),
                enabled = pairedDevices.isNotEmpty() // Disable if no paired devices
            ) {
                Text(text = "Select Paired Device")
            }

            DropdownMenu(
                expanded = devicesMenuExpanded,
                onDismissRequest = { devicesMenuExpanded = false }
            ) {
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "No paired devices available",
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    pairedDevices.forEach { (name, address) ->
                        DropdownMenuItem(
                            text = { Text(text = "$name ($address)") },
                            onClick = {
                                selectedDeviceAddress = address
                                devicesMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Button to sync with the selected device
        Button(
            onClick = {
                selectedDeviceAddress?.let { addr ->
                    scope.launch {
                        val isConnected = bluetoothService.connectToPairedDevice(addr)
                        if (isConnected) {
                            // Wait for a confirmation message from the Raspberry Pi
                            val confirmationMessage = bluetoothService.receiveMessage()
                            if (confirmationMessage != null) {
                                // Send a sync message to the Raspberry Pi
                                bluetoothService.sendMessage("Sync")
                                syncStatus = "Synced: $confirmationMessage"
                            } else {
                                syncStatus = "Failed to Sync"
                            }
                        } else {
                            syncStatus = "Failed to Connect"
                        }
                    }
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = selectedDeviceAddress != null // Disable if no device is selected
        ) {
            Text(text = "Sync with Device")
        }

        // Display sync status
        Text(text = syncStatus)

        // Button to open system settings
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
package com.example.drinksync.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothPermissionsTest {
    @Test
    fun android12AndUpNeedsOnlyConnect() {
        assertArrayEquals(arrayOf("android.permission.BLUETOOTH_CONNECT"), BluetoothPermissions.required(31))
        assertEquals("android.permission.BLUETOOTH_CONNECT", BluetoothPermissions.connectPermission(35))
    }

    @Test
    fun legacyUsesInstallTimePermissions() {
        assertArrayEquals(
            arrayOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN"),
            BluetoothPermissions.required(30),
        )
        assertEquals("android.permission.BLUETOOTH", BluetoothPermissions.connectPermission(24))
    }
}

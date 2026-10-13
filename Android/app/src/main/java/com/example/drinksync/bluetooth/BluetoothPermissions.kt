package com.example.drinksync.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

/**
 * Runtime permissions the RFCOMM server needs.
 *
 * Android 12+ (API 31): accepting connections on an RFCOMM server socket needs only
 * BLUETOOTH_CONNECT. BLUETOOTH_SCAN is for discovery, which this app never does.
 * Android 11 and lower: BLUETOOTH and BLUETOOTH_ADMIN are install-time permissions.
 */
// BLUETOOTH_CONNECT is a String constant inlined at compile time; it is only used on API 31+.
@SuppressLint("InlinedApi")
object BluetoothPermissions {
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

    /** The permission whose absence means the server cannot run. */
    fun connectPermission(sdkInt: Int = Build.VERSION.SDK_INT): String =
        if (sdkInt >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
}

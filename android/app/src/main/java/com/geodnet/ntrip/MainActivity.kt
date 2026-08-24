package com.geodnet.ntrip

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.geodnet.ntrip.ui.NtripScreen
import com.geodnet.ntrip.ui.NtripViewModel
import com.geodnet.ntrip.ui.theme.NtripAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NtripViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op either way; the BLE UI checks state and re-prompts as needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions.launch(requiredPermissions())

        setContent {
            NtripAppTheme {
                NtripScreen(viewModel)
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions.toTypedArray()
    }
}

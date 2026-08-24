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

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NtripAppTheme {
                NtripScreen(viewModel)
            }
        }
    }
}

package com.example

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.screens.MdmDashboard
import com.example.ui.theme.EduGuardTheme
import com.example.ui.viewmodel.MdmViewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {

    private val viewModel: MdmViewModel by viewModels()

    // Activity Contract to request VpnService permission from user securely
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startVpnService()
        }
    }

    // Google Play Services QR Scanner trigger
    private fun triggerQrScanner() {
        try {
            val scanner = GmsBarcodeScanning.getClient(this)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawText = barcode.rawValue
                    if (!rawText.isNullOrBlank()) {
                        val success = viewModel.applySetupFromEncodedConfig(rawText)
                        if (success) {
                            Toast.makeText(this, "Configuration loaded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to apply scanned configuration. Invalid format.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "No configuration text found in scan.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Scan cancelled/failed. Direct entry is available as well.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Google Code Scanner unavailable on this device. Please paste configuration.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EduGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MdmDashboard(
                        viewModel = viewModel,
                        onRequestActivateVpn = {
                            try {
                                val intent = VpnService.prepare(this)
                                if (intent != null) {
                                    vpnLauncher.launch(intent)
                                } else {
                                    // Already approved before
                                    viewModel.startVpnService()
                                }
                            } catch (e: Exception) {
                                // Fallback directly for testing
                                viewModel.startVpnService()
                            }
                        },
                        onRequestScanConfigQr = {
                            triggerQrScanner()
                        }
                    )
                }
            }
        }
    }
}

package com.scooterre.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.ui.ScooterApp
import com.scooterre.client.viewmodel.ScooterViewModel

class MainActivity : FragmentActivity() {

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private val viewModel: ScooterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureBluetoothPermissions()
        handleImportIntent(intent)
        if (savedInstanceState == null) handleReminderIntent(intent)
        setContent {
            ScooterApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInsuranceState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
        handleReminderIntent(intent)
    }

    /** Tapping an insurance-plate reminder opens the documents of the scooter it names. */
    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.getStringExtra(InsuranceReminders.EXTRA_OPEN) != InsuranceReminders.OPEN_DOCUMENTS) return
        val mac = intent.getStringExtra(InsuranceReminders.EXTRA_MAC)
        intent.removeExtra(InsuranceReminders.EXTRA_OPEN)
        viewModel.runWhenUnlocked { viewModel.openDocuments(mac) }
    }

    /** scooterre://import?code=... / ?file=<name in getExternalFilesDir> and
     * scooterre://login?action=qr - see the matching AndroidManifest intent-filter comment for
     * why these exist alongside the in-app UI. The `file` variant exists so a code can reach the
     * app via `adb push` (a plain file copy) instead of as URI text - keeps the secret out of any
     * command-line argument. */
    private fun handleImportIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "scooterre") return
        // A locked app must not import keys or open screens before the user has unlocked it.
        viewModel.runWhenUnlocked { runCatching { handleDeepLink(uri) } }
    }

    private fun handleDeepLink(uri: android.net.Uri) {
        when (uri.host) {
            "import" -> {
                val fileName = uri.getQueryParameter("file")
                val code = if (fileName != null) {
                    java.io.File(getExternalFilesDir(null), java.io.File(fileName).name).readText(Charsets.UTF_8)
                } else {
                    uri.getQueryParameter("code") ?: return
                }
                viewModel.onImportTextChanged(code)
                viewModel.importDevice()
            }
            "login" -> {
                if (uri.getQueryParameter("action") == "qr") viewModel.startQrLogin()
            }
        }
    }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }
}

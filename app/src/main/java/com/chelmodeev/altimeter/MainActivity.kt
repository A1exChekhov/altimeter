package com.chelmodeev.altimeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.chelmodeev.altimeter.health.HealthReader
import com.chelmodeev.altimeter.track.TrackingService
import com.chelmodeev.altimeter.ui.AltimeterScreen
import com.chelmodeev.altimeter.ui.ScreenActions
import com.chelmodeev.altimeter.ui.theme.AltimeterTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            viewModel.onLocationPermission(granted)
        }

    private val healthPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.onHealthPermissionsResult()
        }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) viewModel.connectBluetoothHeartRate()
        }

    private val huaweiHealthPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.onHuaweiHealthAuthorizationResult(result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AltimeterTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                AltimeterScreen(
                    state = state,
                    actions = ScreenActions(
                        onSendWatch = viewModel::sendToWatch,
                        onWearEngine = { viewModel.sendViaWearEngine(this) },
                        onRefreshVitals = viewModel::refreshVitals,
                        onRequestHealth = ::requestHealthPermissions,
                        onRequestHuaweiHealth = ::requestHuaweiHealthPermissions,
                        onOpenHealthSync = ::openHealthSync,
                        onOpenHealthConnect = ::openHealthConnect,
                        onRepairHealth = ::repairHealthPermissions,
                        onConnectBluetooth = ::requestBluetoothHeartRate,
                        onGrantLocation = ::requestLocationPermissions,
                        onSetUnit = viewModel::setUnit,
                        onCalibAuto = viewModel::setCalibrationAuto,
                        onCalibManual = viewModel::calibrateManual,
                        onCalibQnh = viewModel::setQnh,
                        onToggleTopo = viewModel::setTopo,
                        onToggleKeepOn = viewModel::setKeepScreenOn,
                        onToggleAutoSend = viewModel::setAutoSend,
                        onResetStats = viewModel::resetStats,
                        onStartTrack = { TrackingService.start(this) },
                        onStopTrack = { TrackingService.stop(this) },
                        onViewTrack = viewModel::viewTrack,
                        onShareTrack = ::shareTrack,
                    ),
                )
            }
        }

        lifecycleScope.launch {
            viewModel.ui.map { it.keepScreenOn }.distinctUntilChanged().collect { on ->
                if (on) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        if (hasLocationPermission()) {
            viewModel.onLocationPermission(true)
        } else {
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        locationPermissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestHealthPermissions() {
        runCatching { healthPermissionLauncher.launch(HealthReader.PERMISSIONS) }
    }

    private fun requestHuaweiHealthPermissions() {
        val intent = viewModel.huaweiHealthAuthorizationIntent()
        if (intent == null) {
            viewModel.onHuaweiHealthAuthorizationUnavailable()
        } else {
            huaweiHealthPermissionLauncher.launch(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    private fun openHealthSync() {
        val intent = packageManager.getLaunchIntentForPackage(HEALTH_SYNC_PACKAGE)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.vitals_health_sync_not_installed, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun openHealthConnect() {
        runCatching {
            startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this))
        }.onFailure {
            Toast.makeText(this, R.string.vitals_health_connect_open_failed, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun repairHealthPermissions() {
        lifecycleScope.launch {
            HealthReader(this@MainActivity).revokeAllPermissions()
            requestHealthPermissions()
        }
    }

    private fun requestBluetoothHeartRate() {
        if (Build.VERSION.SDK_INT >= 31) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            val granted = permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                viewModel.connectBluetoothHeartRate()
            } else {
                bluetoothPermissionLauncher.launch(permissions)
            }
        } else {
            viewModel.connectBluetoothHeartRate()
        }
    }

    private fun shareTrack(path: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/gpx+xml")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.track_share)))
        }
    }

    companion object {
        private const val HEALTH_SYNC_PACKAGE = "nl.appyhapps.healthsync"
    }
}

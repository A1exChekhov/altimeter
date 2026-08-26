package com.chelmodeev.altimeter

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
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
import com.chelmodeev.altimeter.localization.AppLanguage
import com.chelmodeev.altimeter.track.TrackingService
import com.chelmodeev.altimeter.track.AutoTrackService
import com.chelmodeev.altimeter.util.Fmt
import com.chelmodeev.altimeter.ui.AltimeterScreen
import com.chelmodeev.altimeter.ui.ScreenActions
import com.chelmodeev.altimeter.ui.theme.AltimeterTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val viewModel: MainViewModel by viewModels()
    private var pendingAutoTrackEnable = false
    private var startTrackAfterLocationGrant = false

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val granted = fineGranted ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            viewModel.onLocationPermission(granted, fineGranted)
            if (startTrackAfterLocationGrant) {
                startTrackAfterLocationGrant = false
                if (fineGranted) TrackingService.start(this)
                else showPreciseLocationRequired()
            }
            if (pendingAutoTrackEnable) {
                if (fineGranted) continueAutoTrackPermission()
                else {
                    pendingAutoTrackEnable = false
                    showPreciseLocationRequired()
                }
            }
        }

    private val activityRecognitionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingAutoTrackEnable) viewModel.setAutoTrack(true)
            pendingAutoTrackEnable = false
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

    private val offlineMapLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importOfflineMap(uri)
        }

    private val trackImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importTrack(uri)
        }

    private val locationPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) shareLocation(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLanguage.syncProcessLocale(this)

        setContent {
            val state by viewModel.ui.collectAsStateWithLifecycle()
            AltimeterTheme(darkTheme = state.darkTheme) {
                AltimeterScreen(
                    state = state,
                    actions = ScreenActions(
                        onSendWatch = viewModel::sendToWatch,
                        onWearEngine = { viewModel.sendViaWearEngine(this) },
                        onRefreshVitals = viewModel::refreshVitals,
                        onRequestHealth = ::requestHealthPermissions,
                        onRequestHuaweiHealth = ::requestHuaweiHealthPermissions,
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
                        onToggleDarkTheme = viewModel::setDarkTheme,
                        onToggleAutoTrack = ::setAutoTrackEnabled,
                        onSetTrackSampling = viewModel::setTrackSampling,
                        onSetLanguage = { AppLanguage.set(this, it) },
                        onResetStats = viewModel::resetStats,
                        onStartTrack = ::startTrackWithPermission,
                        onStopTrack = { TrackingService.stop(this) },
                        onViewTrack = viewModel::viewTrack,
                        onShareTrack = ::shareTrack,
                        onDeleteTrack = viewModel::deleteTrack,
                        onMinimizeApp = { moveTaskToBack(true) },
                        onShareLocation = { shareLocation(null) },
                        onShareLocationWithPhoto = { locationPhotoLauncher.launch("image/*") },
                        onImportTrack = {
                            trackImportLauncher.launch(
                                arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")
                            )
                        },
                        onImportOfflineMap = { offlineMapLauncher.launch(arrayOf("*/*")) },
                        onDownloadOfflineMap = viewModel::downloadOfflineMap,
                        onActivateOfflineMap = viewModel::activateOfflineMap,
                        onUseOnlineMap = viewModel::useOnlineMap,
                        onDeleteOfflineMap = viewModel::deleteOfflineMap,
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

        lifecycleScope.launch {
            viewModel.ui.map { it.autoTrackEnabled }.distinctUntilChanged().collect { enabled ->
                AutoTrackService.setEnabled(this@MainActivity, enabled)
            }
        }

        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationGranted = fineGranted || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (locationGranted) {
            viewModel.onLocationPermission(true, fineGranted)
        } else {
            requestLocationPermissions()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        locationPermissionLauncher.launch(perms.toTypedArray())
    }

    private fun setAutoTrackEnabled(enabled: Boolean) {
        if (!enabled) {
            pendingAutoTrackEnable = false
            viewModel.setAutoTrack(false)
            return
        }
        pendingAutoTrackEnable = true
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestLocationPermissions()
        } else {
            continueAutoTrackPermission()
        }
    }

    private fun continueAutoTrackPermission() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            pendingAutoTrackEnable = false
            showPreciseLocationRequired()
            return
        }
        if (Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setAutoTrack(true)
            pendingAutoTrackEnable = false
        } else {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun startTrackWithPermission() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            TrackingService.start(this)
            return
        }
        startTrackAfterLocationGrant = true
        requestLocationPermissions()
    }

    private fun showPreciseLocationRequired() {
        Toast.makeText(this, R.string.track_precise_location_required, Toast.LENGTH_LONG).show()
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

    override fun onPostResume() {
        super.onPostResume()
        viewModel.onAppForegrounded()
    }

    override fun onPause() {
        viewModel.onAppBackgrounded()
        super.onPause()
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

    private fun shareLocation(photo: Uri?) {
        val state = viewModel.ui.value
        val latitude = state.latitude ?: return
        val longitude = state.longitude ?: return
        val coordinates = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        val altitude = state.altitude?.let { Fmt.altitude(this, it, state.unit) } ?: "—"
        val pressure = state.pressureHpa?.let { Fmt.pressure(this, it) } ?: "—"
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val mapLink = "https://maps.google.com/?q=$latitude,$longitude"
        val message = buildString {
            appendLine("📍 $coordinates")
            appendLine(getString(R.string.location_share_measurements, altitude, pressure))
            appendLine(time)
            appendLine(mapLink)
            appendLine()
            appendLine("Errarium™ by Aleksey Hermes")
            append("errarium.ai@gmail.com")
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (photo == null) "text/plain" else "image/*")
            .putExtra(Intent.EXTRA_TEXT, message)
        if (photo != null) {
            send.putExtra(Intent.EXTRA_STREAM, photo)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.location_share_title)))
    }
}

package com.chelmodeev.altimeter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.Advice
import com.chelmodeev.altimeter.model.AdviceKind
import com.chelmodeev.altimeter.model.AdviceSeverity
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.BluetoothVitalsState
import com.chelmodeev.altimeter.model.CalibrationMode
import com.chelmodeev.altimeter.model.MslSource
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.model.VitalsSource
import com.chelmodeev.altimeter.ui.theme.zoneAccent
import com.chelmodeev.altimeter.util.Fmt
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class ScreenActions(
    val onSendWatch: () -> Unit,
    val onWearEngine: () -> Unit,
    val onRefreshVitals: () -> Unit,
    val onRequestHealth: () -> Unit,
    val onRequestHuaweiHealth: () -> Unit,
    val onOpenHealthConnect: () -> Unit,
    val onRepairHealth: () -> Unit,
    val onConnectBluetooth: () -> Unit,
    val onGrantLocation: () -> Unit,
    val onSetUnit: (AltUnit) -> Unit,
    val onCalibAuto: () -> Unit,
    val onCalibManual: (String) -> Unit,
    val onCalibQnh: (String) -> Unit,
    val onToggleTopo: (Boolean) -> Unit,
    val onToggleKeepOn: (Boolean) -> Unit,
    val onToggleAutoSend: (Boolean) -> Unit,
    val onToggleDarkTheme: (Boolean) -> Unit,
    val onToggleAutoTrack: (Boolean) -> Unit,
    val onResetStats: () -> Unit,
    val onStartTrack: () -> Unit,
    val onStopTrack: () -> Unit,
    val onViewTrack: (String) -> Unit,
    val onShareTrack: (String) -> Unit,
    val onShareLocation: () -> Unit,
    val onShareLocationWithPhoto: () -> Unit,
    val onImportTrack: () -> Unit,
    val onImportOfflineMap: () -> Unit,
    val onDownloadOfflineMap: (String) -> Unit,
    val onActivateOfflineMap: (String) -> Unit,
    val onUseOnlineMap: () -> Unit,
    val onDeleteOfflineMap: (String) -> Unit,
)

private enum class AppSection { HOME, MAP, TRACK, ANALYTICS }

@Composable
fun AltimeterScreen(state: UiState, actions: ScreenActions) {
    val accent by animateColorAsState(zoneAccent(state.altitude), tween(900), label = "accent")
    var showSettings by remember { mutableStateOf(false) }
    var showHealthDetails by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }
    val homeScroll = rememberScrollState()
    val trackScroll = rememberScrollState()
    val analyticsScroll = rememberScrollState()
    val mapSession = rememberTouristMapSession()
    val scope = rememberCoroutineScope()

    fun select(target: AppSection) {
        if (section == target) {
            scope.launch {
                when (target) {
                    AppSection.HOME -> homeScroll.animateScrollTo(0)
                    AppSection.TRACK -> trackScroll.animateScrollTo(0)
                    AppSection.ANALYTICS -> analyticsScroll.animateScrollTo(0)
                    AppSection.MAP -> Unit
                }
            }
        } else {
            section = target
        }
    }

    BackHandler(enabled = section != AppSection.HOME) { section = AppSection.HOME }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(section, state.tracking.recording, ::select) },
    ) { contentPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
            )

            when (section) {
                AppSection.HOME -> HomePage(
                    mapSession = mapSession,
                    state = state,
                    accent = accent,
                    actions = actions,
                    scrollState = homeScroll,
                    onOpenMap = { select(AppSection.MAP) },
                    onOpenTrack = { select(AppSection.TRACK) },
                    onOpenSettings = { showSettings = true },
                    onOpenHealth = { showHealthDetails = true },
                )
                AppSection.MAP -> MapPage(
                    mapSession = mapSession,
                    state = state,
                    accent = accent,
                    actions = actions,
                    onOpenSettings = { showSettings = true },
                )
                AppSection.TRACK -> TrackPage(
                    state = state,
                    actions = actions,
                    scrollState = trackScroll,
                    onOpenMap = { select(AppSection.MAP) },
                    onOpenSettings = { showSettings = true },
                )
                AppSection.ANALYTICS -> AnalyticsPage(
                    state = state,
                    accent = accent,
                    actions = actions,
                    scrollState = analyticsScroll,
                    onOpenSettings = { showSettings = true },
                    onOpenHealth = { showHealthDetails = true },
                )
            }

            if (showSettings) {
                SettingsSheet(
                    state = state,
                    actions = actions,
                    onDismiss = { showSettings = false },
                )
            }
            if (showHealthDetails) {
                VitalsDetailsSheet(
                    state = state,
                    actions = actions,
                    onDismiss = { showHealthDetails = false },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    section: AppSection,
    trackRecording: Boolean,
    onSelect: (AppSection) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
        val items = listOf(
            Triple(AppSection.HOME, Icons.Rounded.Home, R.string.nav_home),
            Triple(AppSection.MAP, Icons.Rounded.Map, R.string.nav_map),
            Triple(AppSection.TRACK, Icons.Rounded.Route, R.string.nav_track),
            Triple(AppSection.ANALYTICS, Icons.Rounded.Insights, R.string.nav_analytics),
        )
        items.forEach { (target, icon, label) ->
            NavigationBarItem(
                selected = section == target,
                onClick = { onSelect(target) },
                icon = {
                    Box {
                        Icon(icon, contentDescription = stringResource(label))
                        if (target == AppSection.TRACK && trackRecording) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .background(Color(0xFFFF5252), CircleShape)
                            )
                        }
                    }
                },
                label = { Text(stringResource(label), maxLines = 1, fontSize = 10.sp) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun HomePage(
    mapSession: TouristMapSession,
    state: UiState,
    accent: Color,
    actions: ScreenActions,
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenMap: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        HeaderRow(
            trackRecording = state.tracking.recording,
            onTracks = onOpenTrack,
            onSettings = onOpenSettings,
        )
        Spacer(Modifier.height(4.dp))
        Readout(state, accent, actions)
        if (!state.locationPermissionGranted) {
            Spacer(Modifier.height(10.dp))
            PermissionCard(actions.onGrantLocation)
        }
        Spacer(Modifier.height(12.dp))
        HomeStatusRow(state)
        Spacer(Modifier.height(12.dp))
        MapCard(
            session = mapSession,
            latitude = state.latitude,
            longitude = state.longitude,
            accuracyMeters = state.gpsAccuracy,
            topo = state.topoMap,
            accent = accent,
            trackPoints = state.mapTrack,
            trackRecording = state.tracking.recording,
            offlineMapPath = state.offlineMaps.activePath,
            expanded = false,
            onToggleExpanded = onOpenMap,
            modifier = Modifier.fillMaxWidth().height(230.dp),
        )
        Spacer(Modifier.height(14.dp))
        VitalsCard(state, actions, onDetails = onOpenHealth)
        state.advices.firstOrNull()?.let {
            Spacer(Modifier.height(14.dp))
            AdviceCard(listOf(it))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MapPage(
    mapSession: TouristMapSession,
    state: UiState,
    accent: Color,
    actions: ScreenActions,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        PageHeader(R.string.nav_map, Icons.Rounded.Map, onOpenSettings)
        MapCard(
            session = mapSession,
            latitude = state.latitude,
            longitude = state.longitude,
            accuracyMeters = state.gpsAccuracy,
            topo = state.topoMap,
            accent = accent,
            trackPoints = state.mapTrack,
            trackRecording = state.tracking.recording,
            offlineMapPath = state.offlineMaps.activePath,
            expanded = true,
            onToggleExpanded = {},
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (state.latitude != null && state.longitude != null) {
            Text(
                text = Fmt.coords(state.latitude, state.longitude),
                style = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Button(
                    onClick = actions.onShareLocation,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.location_share), maxLines = 1)
                }
                OutlinedButton(
                    onClick = actions.onShareLocationWithPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.location_share_photo), maxLines = 1)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TrackPage(
    state: UiState,
    actions: ScreenActions,
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        PageHeader(R.string.nav_track, Icons.Rounded.Route, onOpenSettings)
        TrackCard(
            state = state,
            actions = actions,
            onViewTrack = { path -> actions.onViewTrack(path); onOpenMap() },
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AnalyticsPage(
    state: UiState,
    accent: Color,
    actions: ScreenActions,
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        PageHeader(R.string.nav_analytics, Icons.Rounded.Insights, onOpenSettings)
        VitalsCard(state, actions, onDetails = onOpenHealth)
        Spacer(Modifier.height(14.dp))
        ChartCard(state, accent)
        Spacer(Modifier.height(14.dp))
        DetailsGrid(state)
        if (state.advices.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AdviceCard(state.advices)
        }
        Spacer(Modifier.height(14.dp))
        WatchCard(state, actions)
        Spacer(Modifier.height(18.dp))
        Footer()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PageHeader(
    title: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpenSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings))
        }
    }
}

@Composable
private fun HomeStatusRow(state: UiState) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HomeStatusCell(
            value = if (state.hasFix) "${state.satellitesUsed}/${state.satellitesTotal}" else "—",
            caption = "GPS",
            modifier = Modifier.weight(1f),
        )
        HomeStatusCell(
            value = state.pressureHpa?.let { Fmt.pressure(context, it) } ?: "—",
            caption = stringResource(R.string.detail_pressure),
            modifier = Modifier.weight(1f),
        )
        HomeStatusCell(
            value = state.gpsVertAccuracy?.let {
                Fmt.accuracy(context, it.toDouble(), state.unit)
            } ?: "—",
            caption = stringResource(R.string.detail_vacc),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeStatusCell(value: String, caption: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                caption,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    trackRecording: Boolean,
    onTracks: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.brand_name),
                fontSize = 9.5.sp,
                letterSpacing = 3.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onTracks) {
            Icon(
                Icons.Rounded.Route,
                contentDescription = stringResource(R.string.track_open),
                tint = if (trackRecording) Color(0xFFFF5252)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Readout(state: UiState, accent: Color, actions: ScreenActions) {
    val context = LocalContext.current
    val altitudeText = MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.altitude != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Fmt.altitudeValue(state.altitude, state.unit),
                    style = TextStyle(
                        fontSize = 86.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-2).sp,
                        fontFeatureSettings = "tnum",
                        brush = Brush.verticalGradient(
                            listOf(altitudeText, accent)
                        ),
                    ),
                    maxLines = 1,
                )
                Text(
                    text = Fmt.unitLabel(context, state.unit).lowercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 5.dp, bottom = 15.dp),
                )
            }
        } else {
            Text(
                text = "– – – –",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    when {
                        !state.locationPermissionGranted -> R.string.perm_needed
                        state.isCalibrating -> R.string.waiting_calibration
                        else -> R.string.waiting_fix
                    }
                ),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            val accuracy = state.accuracy
            if (state.altitude != null && accuracy != null) {
                Pill(text = Fmt.accuracy(context, accuracy, state.unit), color = accent)
            }
            val vs = state.verticalSpeedMpm
            if (vs != null && abs(vs) >= 0.5) {
                Pill(
                    text = Fmt.vspeed(context, vs, state.unit),
                    color = MaterialTheme.colorScheme.onSurface,
                    icon = {
                        Icon(
                            when {
                                vs >= 0.5 -> Icons.AutoMirrored.Rounded.TrendingUp
                                vs <= -0.5 -> Icons.AutoMirrored.Rounded.TrendingDown
                                else -> Icons.AutoMirrored.Rounded.TrendingFlat
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = state.placeName
                    ?: if (state.latitude != null) stringResource(R.string.place_unknown) else "—",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (state.latitude != null && state.longitude != null) {
            Text(
                text = Fmt.coords(state.latitude, state.longitude),
                fontSize = 11.5.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Button(
            onClick = if (state.tracking.recording) actions.onStopTrack else actions.onStartTrack,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.tracking.recording) Color(0xFFD84A4A)
                else accent.copy(alpha = 0.92f),
                contentColor = Color(0xFF07111F),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Icon(
                if (state.tracking.recording) Icons.Rounded.Stop else Icons.Rounded.Route,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (state.tracking.recording) {
                    stringResource(
                        R.string.track_hero_stop,
                        Fmt.distance(context, state.tracking.distanceM),
                    )
                } else {
                    stringResource(R.string.track_start)
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, icon: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.06f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            icon?.invoke()
            Text(text = text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChipsRow(state: UiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatusChip(
            text = if (state.hasFix) {
                stringResource(R.string.chip_gps, state.satellitesUsed, state.satellitesTotal)
            } else {
                stringResource(R.string.chip_gps_search)
            },
            dotColor = if (state.hasFix) Color(0xFF6FCF97) else Color(0xFFF2B94B),
        )
        StatusChip(
            text = stringResource(if (state.hasBarometer) R.string.chip_baro else R.string.chip_no_baro),
            dotColor = if (state.hasBarometer) Color(0xFF6FCF97) else Color(0xFF8FA3C2),
        )
        StatusChip(
            text = stringResource(
                when (state.calibrationMode) {
                    CalibrationMode.AUTO_GPS -> R.string.chip_calib_auto
                    CalibrationMode.MANUAL_ALTITUDE -> R.string.chip_calib_manual
                    CalibrationMode.QNH -> R.string.chip_calib_qnh
                }
            ),
            dotColor = Color(0xFF7FB4FF),
        )
        if (state.mslSource != MslSource.NONE) {
            StatusChip(
                text = stringResource(
                    when (state.mslSource) {
                        MslSource.API34 -> R.string.src_api34
                        MslSource.NMEA_MSL -> R.string.src_nmea
                        MslSource.GEOID_CORRECTED -> R.string.src_geoid
                        else -> R.string.src_ellipsoid
                    }
                ),
                dotColor = Color(0xFF4DD0C4),
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, dotColor: Color) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.05f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = text,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    SectionCard {
        Text(
            text = stringResource(R.string.perm_needed),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_permission)) }
    }
}

@Composable
private fun AdviceCard(advices: List<Advice>) {
    SectionCard {
        Text(
            text = stringResource(R.string.advice_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        advices.forEach { advice ->
            Row(modifier = Modifier.padding(vertical = 5.dp)) {
                val (icon, tint) = when (advice.severity) {
                    AdviceSeverity.WARNING -> Icons.Rounded.Error to Color(0xFFFF8A80)
                    AdviceSeverity.CAUTION -> Icons.Rounded.WarningAmber to Color(0xFFF2B94B)
                    AdviceSeverity.INFO -> Icons.Rounded.Lightbulb to Color(0xFF4DD0C4)
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(9.dp))
                Text(
                    text = adviceText(advice),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.advice_disclaimer),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun adviceText(a: Advice): String = when (a.kind) {
    AdviceKind.PRESSURE_FALLING_FAST ->
        stringResource(R.string.advice_pressure_falling_fast, a.value.orEmpty())
    AdviceKind.PRESSURE_FALLING ->
        stringResource(R.string.advice_pressure_falling, a.value.orEmpty())
    AdviceKind.PRESSURE_RISING -> stringResource(R.string.advice_pressure_rising)
    AdviceKind.ALTITUDE_ACCLIMATIZE -> stringResource(R.string.advice_alt_acclimatize)
    AdviceKind.ALTITUDE_HIGH -> stringResource(R.string.advice_alt_high)
    AdviceKind.ALTITUDE_VERY_HIGH -> stringResource(R.string.advice_alt_very_high)
    AdviceKind.FAST_ASCENT -> stringResource(R.string.advice_fast_ascent)
    AdviceKind.HYDRATION -> stringResource(R.string.advice_hydration)
    AdviceKind.SPO2_LOW -> stringResource(R.string.advice_spo2_low, a.value.orEmpty())
    AdviceKind.SPO2_VERY_LOW -> stringResource(R.string.advice_spo2_very_low, a.value.orEmpty())
    AdviceKind.HR_HIGH -> stringResource(R.string.advice_hr_high, a.value.orEmpty())
    AdviceKind.GPS_WEAK -> stringResource(R.string.advice_gps_weak)
}

@Composable
private fun VitalsCard(
    state: UiState,
    actions: ScreenActions,
    onDetails: () -> Unit,
) {
    val vitals = state.vitals
    val hasHeartPermission = vitals.heartRatePermissionGranted ||
        vitals.restingHeartRatePermissionGranted
    val hasAllPermissions = hasHeartPermission && vitals.spo2PermissionGranted &&
        vitals.stepsPermissionGranted
    val missingValues = listOf(
        vitals.heartRateBpm,
        vitals.spo2Percent,
        vitals.stepsToday,
    ).count { it == null }
    val metricColor = MaterialTheme.colorScheme.onSurface

    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactVitalStat(
                label = stringResource(R.string.vitals_hr),
                value = vitals.heartRateBpm?.toString() ?: "—",
                unit = stringResource(R.string.vitals_bpm),
                color = metricColor,
                modifier = Modifier.weight(1f),
            )
            CompactVitalStat(
                label = stringResource(R.string.vitals_spo2_short),
                value = vitals.spo2Percent?.roundToInt()?.let { "$it" } ?: "—",
                unit = "%",
                color = metricColor,
                modifier = Modifier.weight(1f),
            )
            CompactVitalStat(
                label = stringResource(R.string.vitals_steps),
                value = vitals.stepsToday?.toString() ?: "—",
                unit = "",
                color = metricColor,
                modifier = Modifier.weight(1f),
            )
        }

        val status = when {
            vitals.healthConnectError == "PERMISSION" ->
                stringResource(R.string.vitals_permission_repair_short)
            !hasAllPermissions -> stringResource(R.string.vitals_access_required_short)
            vitals.stepsToday != null && vitals.heartRateBpm == null && vitals.spo2Percent == null ->
                stringResource(R.string.vitals_only_steps_short)
            missingValues > 0 -> stringResource(R.string.vitals_data_incomplete_short)
            vitals.bluetoothState == BluetoothVitalsState.CONNECTED ->
                stringResource(R.string.vitals_source_bluetooth)
            else -> stringResource(R.string.vitals_ready)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(6.dp))
            if (!hasAllPermissions || vitals.healthConnectError == "PERMISSION") {
                TextButton(
                    onClick = if (vitals.healthConnectError == "PERMISSION") {
                        actions.onRepairHealth
                    } else {
                        actions.onRequestHealth
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = status,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = status,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (vitals.refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = actions.onRefreshVitals, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.vitals_refresh),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            IconButton(onClick = onDetails, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.vitals_sources),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactVitalStat(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.045f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(40.dp),
            ) {
                val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
                val valueWithUnit = buildAnnotatedString {
                    append(value)
                    if (unit.isNotEmpty()) {
                        append('\u00A0')
                        withStyle(
                            SpanStyle(
                                color = unitColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        ) {
                            append(unit)
                        }
                    }
                }
                Text(
                    text = valueWithUnit,
                    style = TextStyle(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VitalsDetailsSheet(
    state: UiState,
    actions: ScreenActions,
    onDismiss: () -> Unit,
) {
    val vitals = state.vitals
    val hasHeartPermission = vitals.heartRatePermissionGranted ||
        vitals.restingHeartRatePermissionGranted
    val hasAllPermissions = hasHeartPermission && vitals.spo2PermissionGranted &&
        vitals.stepsPermissionGranted
    val bluetoothBusy = vitals.bluetoothState == BluetoothVitalsState.SCANNING ||
        vitals.bluetoothState == BluetoothVitalsState.CONNECTING

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.vitals_sources),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.vitals_permissions_status,
                    if (hasHeartPermission) "✓" else "—",
                    if (vitals.spo2PermissionGranted) "✓" else "—",
                    if (vitals.stepsPermissionGranted) "✓" else "—",
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (vitals.healthConnectError == "PERMISSION") {
                Text(
                    text = stringResource(R.string.vitals_permission_repair_hint),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFFFCC80),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = actions.onRepairHealth,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vitals_repair_access))
                }
                Spacer(Modifier.height(8.dp))
            } else if (!hasAllPermissions) {
                Button(
                    onClick = actions.onRequestHealth,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vitals_health_connect_grant))
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = actions.onRefreshVitals,
                enabled = !vitals.refreshing && vitals.permissionsGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vitals.refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(stringResource(R.string.vitals_get_data))
            }
            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = actions.onConnectBluetooth,
                enabled = !bluetoothBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (bluetoothBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(
                    stringResource(
                        if (vitals.bluetoothState == BluetoothVitalsState.CONNECTED) {
                            R.string.vitals_ble_reconnect
                        } else {
                            R.string.vitals_ble_connect
                        }
                    )
                )
            }
            bluetoothStatusText(vitals.bluetoothState, vitals.bluetoothDeviceName)?.let { text ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = actions.onOpenHealthConnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vitals_open_health_connect))
            }
            Spacer(Modifier.height(14.dp))
            listOfNotNull(
                vitalsSourceLabel(vitals.heartRateSource, vitals.heartRateOrigin)?.let {
                    stringResource(R.string.vitals_hr) to it
                },
                vitalsSourceLabel(vitals.spo2Source, vitals.spo2Origin)?.let {
                    stringResource(R.string.vitals_spo2_short) to it
                },
                vitalsSourceLabel(vitals.stepsSource, vitals.stepsOrigin)?.let {
                    stringResource(R.string.vitals_steps) to it
                },
            ).forEach { (label, source) ->
                Text(
                    text = "$label · $source",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun VitalsCardLegacy(state: UiState, actions: ScreenActions) {
    val context = LocalContext.current
    val vitals = state.vitals
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.vitals_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (vitals.permissionsGranted || vitals.huaweiHealthAuthorized) {
                if (vitals.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = actions.onRefreshVitals, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.vitals_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (!vitals.huaweiHealthConfigured) {
            Text(
                text = stringResource(R.string.vitals_repo_mode),
                fontSize = 11.sp,
                color = Color(0xFF8BC34A),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.vitals_repo_hint),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val bluetoothBusy = vitals.bluetoothState == BluetoothVitalsState.SCANNING ||
                vitals.bluetoothState == BluetoothVitalsState.CONNECTING
            FilledTonalButton(
                onClick = actions.onConnectBluetooth,
                enabled = !bluetoothBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (bluetoothBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(
                    stringResource(
                        if (vitals.bluetoothState == BluetoothVitalsState.CONNECTED) {
                            R.string.vitals_ble_reconnect
                        } else {
                            R.string.vitals_ble_connect
                        }
                    )
                )
            }
            bluetoothStatusText(vitals.bluetoothState, vitals.bluetoothDeviceName)?.let { status ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = if (vitals.bluetoothState == BluetoothVitalsState.CONNECTED) {
                        Color(0xFF8BC34A)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        } else if (vitals.huaweiHealthAuthorized) {
            Text(
                text = stringResource(R.string.vitals_huawei_connected),
                fontSize = 11.sp,
                color = Color(0xFF8BC34A),
            )
            Spacer(Modifier.height(8.dp))
        } else {
            if (!vitals.huaweiHealthInstalled) {
                Text(
                    text = stringResource(R.string.vitals_huawei_not_installed),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.vitals_huawei_hint),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(9.dp))
                FilledTonalButton(onClick = actions.onRequestHuaweiHealth) {
                    Text(stringResource(R.string.vitals_huawei_grant))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val hasHeartRatePermission = vitals.heartRatePermissionGranted ||
            vitals.restingHeartRatePermissionGranted
        val allHealthConnectPermissions = hasHeartRatePermission &&
            vitals.spo2PermissionGranted && vitals.stepsPermissionGranted
        if (!allHealthConnectPermissions) {
            when {
                vitals.available -> OutlinedButton(onClick = actions.onRequestHealth) {
                    Text(stringResource(R.string.vitals_health_connect_grant))
                }
                vitals.needsProviderInstall -> Text(
                    text = stringResource(R.string.vitals_install_provider),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vitals.available || vitals.needsProviderInstall) Spacer(Modifier.height(8.dp))
        }

        if (vitals.available) {
            Text(
                text = stringResource(
                    R.string.vitals_permissions_status,
                    if (hasHeartRatePermission) "✓" else "—",
                    if (vitals.spo2PermissionGranted) "✓" else "—",
                    if (vitals.stepsPermissionGranted) "✓" else "—",
                ),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        val errorText = when (vitals.huaweiError) {
            "HUAWEI_HEALTH_NOT_INSTALLED" -> stringResource(R.string.vitals_huawei_not_installed)
            "HUAWEI_APP_ID_NOT_CONFIGURED" -> stringResource(R.string.vitals_huawei_not_configured)
            "HUAWEI_AUTHORIZATION_CANCELLED" -> stringResource(R.string.vitals_huawei_auth_cancelled)
            "HUAWEI_AUTHORIZATION_UNAVAILABLE" -> stringResource(R.string.vitals_huawei_auth_unavailable)
            null -> null
            else -> stringResource(R.string.vitals_huawei_error, vitals.huaweiError)
        }
        if (vitals.huaweiHealthConfigured && errorText != null) {
            Text(
                text = errorText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (vitals.healthConnectError != null) {
            Text(
                text = stringResource(R.string.vitals_health_connect_error, vitals.healthConnectError),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            !vitals.huaweiHealthAuthorized && !vitals.permissionsGranted -> {
                Text(
                    text = stringResource(R.string.vitals_sources_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VitalStat(
                        label = stringResource(
                            if (vitals.heartRateIsResting) R.string.vitals_hr_resting
                            else R.string.vitals_hr,
                        ),
                        value = vitals.heartRateBpm?.toString() ?: "—",
                        unit = stringResource(R.string.vitals_bpm),
                        atMs = vitals.heartRateAtMs,
                        source = vitalsSourceLabel(vitals.heartRateSource, vitals.heartRateOrigin),
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.weight(1f),
                    )
                    VitalStat(
                        label = stringResource(R.string.vitals_spo2),
                        value = vitals.spo2Percent?.let { "${it.roundToInt()}%" } ?: "—",
                        unit = "",
                        atMs = vitals.spo2AtMs,
                        source = vitalsSourceLabel(vitals.spo2Source, vitals.spo2Origin),
                        color = Color(0xFF80D8FF),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                VitalStat(
                    label = stringResource(R.string.vitals_steps),
                    value = vitals.stepsToday?.toString() ?: "—",
                    unit = stringResource(R.string.vitals_steps_today),
                    atMs = vitals.stepsAtMs,
                    source = vitalsSourceLabel(vitals.stepsSource, vitals.stepsOrigin),
                    color = Color(0xFFB9F6CA),
                    modifier = Modifier.fillMaxWidth(),
                )
                val missingHealthConnectTypes = buildList {
                    if (vitals.heartRateBpm == null && hasHeartRatePermission) {
                        add(stringResource(R.string.vitals_hr))
                    }
                    if (vitals.spo2Percent == null && vitals.spo2PermissionGranted) {
                        add(stringResource(R.string.vitals_spo2))
                    }
                }
                if (!vitals.huaweiHealthAuthorized && missingHealthConnectTypes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            R.string.vitals_missing_data_types,
                            missingHealthConnectTypes.joinToString(", "),
                        ),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFFFCC80),
                    )
                }
                if (vitals.heartRateBpm == null && vitals.spo2Percent == null &&
                    vitals.stepsToday == null
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vitals_no_data),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vitals.hrSeries.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    HrSparkline(
                        series = vitals.hrSeries,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vitals_auto),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VitalStat(
    label: String,
    value: String,
    unit: String,
    atMs: Long?,
    source: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = color,
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (atMs != null) {
                Text(
                    text = stringResource(R.string.vitals_updated, Fmt.timeShort(context, atMs)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (source != null) {
                Text(
                    text = source,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun vitalsSourceLabel(source: VitalsSource?, origin: String?): String? {
    val base = when (source) {
        VitalsSource.HUAWEI_HEALTH -> stringResource(R.string.vitals_source_huawei)
        VitalsSource.HEALTH_CONNECT -> stringResource(R.string.vitals_source_health_connect)
        VitalsSource.BLUETOOTH -> stringResource(R.string.vitals_source_bluetooth)
        null -> return null
    }
    val originLabel = when (origin) {
        "com.huawei.health" -> null
        "nodomain.freeyourgadget.gadgetbridge",
        "nodomain.freeyourgadget.gadgetbridge.nightly",
        -> "Gadgetbridge"
        else -> origin
    }
    return originLabel?.takeIf { it.isNotBlank() }
        ?.let { "$base · $it" }
        ?: base
}

@Composable
private fun bluetoothStatusText(state: BluetoothVitalsState, deviceName: String?): String? =
    when (state) {
        BluetoothVitalsState.IDLE -> stringResource(R.string.vitals_ble_hint)
        BluetoothVitalsState.SCANNING -> stringResource(R.string.vitals_ble_scanning)
        BluetoothVitalsState.CONNECTING -> stringResource(R.string.vitals_ble_connecting)
        BluetoothVitalsState.CONNECTED -> stringResource(
            R.string.vitals_ble_connected,
            deviceName ?: "Huawei Watch",
        )
        BluetoothVitalsState.NOT_FOUND -> stringResource(R.string.vitals_ble_not_found)
        BluetoothVitalsState.BLUETOOTH_OFF -> stringResource(R.string.vitals_ble_off)
        BluetoothVitalsState.ERROR -> stringResource(R.string.vitals_ble_error)
    }

@Composable
private fun HrSparkline(series: List<Pair<Long, Long>>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val t0 = series.first().first
        val t1 = series.last().first
        if (t1 <= t0) return@Canvas
        val minV = (series.minOf { it.second } - 5).coerceAtLeast(30)
        val maxV = (series.maxOf { it.second } + 5).coerceAtMost(220)
        val range = (maxV - minV).coerceAtLeast(1)
        val path = androidx.compose.ui.graphics.Path()
        series.forEachIndexed { i, (t, v) ->
            val x = (t - t0).toFloat() / (t1 - t0).toFloat() * size.width
            val y = size.height - (v - minV).toFloat() / range.toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(state: UiState, accent: Color) {
    val context = LocalContext.current
    var windowMs by remember { mutableStateOf(60L * 60L * 1_000L) }
    var altitudeConnected by rememberSaveable { mutableStateOf(true) }
    var heartConnected by rememberSaveable { mutableStateOf(true) }
    var oxygenConnected by rememberSaveable { mutableStateOf(true) }
    var stepsConnected by rememberSaveable { mutableStateOf(true) }
    val ranges = listOf(
        15L * 60L * 1_000L to R.string.chart_range_15m,
        60L * 60L * 1_000L to R.string.chart_range_1h,
        3L * 60L * 60L * 1_000L to R.string.chart_range_3h,
        6L * 60L * 60L * 1_000L to R.string.chart_range_6h,
    )
    val altitudeColor = Color(0xFFD5A657)
    val heartColor = Color(0xFFE17070)
    val oxygenColor = Color(0xFF62A9D8)
    val stepsColor = Color(0xFF72B98B)
    val altitudeLabel = stringResource(R.string.chart_altitude)
    val heartLabel = stringResource(R.string.vitals_hr)
    val stepsLabel = stringResource(R.string.vitals_steps)
    val stepBars = state.vitals.stepsSeries.mapIndexed { index, sample ->
        val previous = state.vitals.stepsSeries.getOrNull(index - 1)?.second ?: 0L
        sample.first to (sample.second - previous).coerceAtLeast(0L).toDouble()
    }
    val allLines = listOf(
        TrendLine(
            key = "altitude",
            label = altitudeLabel,
            unit = if (state.unit == AltUnit.METERS) " м" else " ft",
            color = altitudeColor,
            points = state.history.map { it.timeMs to it.altitude },
        ),
        TrendLine(
            key = "heart",
            label = heartLabel,
            unit = " ${stringResource(R.string.vitals_bpm)}",
            color = heartColor,
            points = state.vitals.hrSeries.map { it.first to it.second.toDouble() },
        ),
        TrendLine(
            key = "oxygen",
            label = "SpO₂",
            unit = "%",
            color = oxygenColor,
            points = state.vitals.spo2Series,
            decimals = 1,
        ),
        TrendLine(
            key = "steps",
            label = stepsLabel,
            unit = "",
            color = stepsColor,
            points = stepBars,
            style = TrendStyle.BARS,
        ),
    )
    val connections = mapOf(
        "altitude" to altitudeConnected,
        "heart" to heartConnected,
        "oxygen" to oxygenConnected,
        "steps" to stepsConnected,
    )
    val lines = allLines.filter { connections[it.key] == true && it.points.isNotEmpty() }
    val scales = trendScales(lines, windowMs)

    SectionCard {
        Text(
            text = stringResource(R.string.chart_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ranges.forEach { (range, label) ->
                FilterChip(
                    selected = windowMs == range,
                    onClick = { windowMs = range },
                    label = { Text(stringResource(label), fontSize = 10.5.sp) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ChartConnector(altitudeColor, altitudeLabel, altitudeConnected) {
                altitudeConnected = !altitudeConnected
            }
            ChartConnector(heartColor, heartLabel, heartConnected) {
                heartConnected = !heartConnected
            }
            ChartConnector(oxygenColor, "SpO₂", oxygenConnected) {
                oxygenConnected = !oxygenConnected
            }
            ChartConnector(stepsColor, stepsLabel, stepsConnected) {
                stepsConnected = !stepsConnected
            }
        }
        if (scales.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                scales.forEach { scale ->
                    Text(
                        text = "${scale.line.label} ${formatTrendValue(scale.min, scale.line.decimals)}–" +
                            "${formatTrendValue(scale.max, scale.line.decimals)}${scale.line.unit}",
                        fontSize = 9.5.sp,
                        color = scale.line.color,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_empty),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CombinedTrendChart(
                lines = lines,
                windowMs = windowMs,
                gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                axisColor = MaterialTheme.colorScheme.onSurfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            )
        }
    }
}

@Composable
private fun ChartConnector(color: Color, text: String, connected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = connected,
        onClick = onClick,
        leadingIcon = {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (connected) color else MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
        label = {
            Text(
                text = text,
                fontSize = 10.5.sp,
                color = if (connected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun DetailsGrid(state: UiState) {
    val context = LocalContext.current
    val cells = buildList {
        add(
            stringResource(R.string.detail_pressure) to
                (state.pressureHpa?.let { Fmt.pressure(context, it) } ?: "—")
        )
        add(
            stringResource(R.string.detail_vacc) to
                (state.gpsVertAccuracy?.let { Fmt.accuracy(context, it.toDouble(), state.unit) } ?: "—")
        )
        add(
            stringResource(R.string.detail_ascent) to
                Fmt.altitudeSigned(context, state.totalAscent, state.unit)
        )
        add(
            stringResource(R.string.detail_descent) to
                Fmt.altitudeSigned(context, -state.totalDescent, state.unit)
        )
        add(
            stringResource(R.string.detail_min) to
                (state.minAltitude?.let { Fmt.altitude(context, it, state.unit) } ?: "—")
        )
        add(
            stringResource(R.string.detail_max) to
                (state.maxAltitude?.let { Fmt.altitude(context, it, state.unit) } ?: "—")
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCells.forEach { (label, value) ->
                    DetailCell(label, value, Modifier.weight(1f))
                }
                if (rowCells.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WatchCard(state: UiState, actions: ScreenActions) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Watch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.watch_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        state.watch.statusText?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.watch_hint),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = actions.onSendWatch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.watch_send))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Switch(checked = state.autoSendToWatch, onCheckedChange = actions.onToggleAutoSend)
            Spacer(Modifier.size(10.dp))
            Text(text = stringResource(R.string.watch_auto), fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSheet(state: UiState, actions: ScreenActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            TrackCard(
                state = state,
                actions = actions,
                onViewTrack = { path ->
                    actions.onViewTrack(path)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun TrackCard(
    state: UiState,
    actions: ScreenActions,
    onViewTrack: (String) -> Unit,
) {
    val context = LocalContext.current
    val t = state.tracking
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t.recording) {
                val transition = rememberInfiniteTransition(label = "rec")
                val recAlpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "recAlpha",
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Color(0xFFFF5252).copy(alpha = recAlpha), CircleShape)
                )
            } else {
                Icon(
                    Icons.Rounded.Route,
                    contentDescription = null,
                    tint = Color(0xFF4DD0C4),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.track_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (t.recording) {
                Text(
                    text = Fmt.duration(System.currentTimeMillis() - t.startedAtMs),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = Color(0xFFFF8A80),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (t.recording) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackMiniStat(
                    label = stringResource(R.string.track_moving_time),
                    value = Fmt.duration(t.movingTimeMs),
                    modifier = Modifier.weight(1f),
                )
                TrackMiniStat(
                    label = stringResource(R.string.track_distance),
                    value = Fmt.distance(context, t.distanceM),
                    modifier = Modifier.weight(1f),
                )
                TrackMiniStat(
                    label = stringResource(R.string.track_moving_speed),
                    value = if (t.movingTimeMs > 0) String.format(
                        java.util.Locale.getDefault(),
                        "%.1f км/ч",
                        t.distanceM / (t.movingTimeMs / 3_600_000.0),
                    ) else "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackMiniStat(
                    label = stringResource(R.string.track_stopped_time),
                    value = Fmt.duration(t.stoppedTimeMs),
                    modifier = Modifier.weight(1f),
                )
                TrackMiniStat(
                    label = stringResource(R.string.detail_ascent),
                    value = Fmt.altitude(context, t.ascentM, state.unit),
                    modifier = Modifier.weight(1f),
                )
                TrackMiniStat(
                    label = stringResource(R.string.detail_descent),
                    value = Fmt.altitude(context, t.descentM, state.unit),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.track_calories_points,
                    (t.distanceM / 1_000.0 * 50.0 + t.ascentM * 0.1).roundToInt(),
                    t.points,
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = actions.onStopTrack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8C3A33),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.track_stop))
            }
        } else {
            FilledTonalButton(
                onClick = actions.onStartTrack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.track_start))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = actions.onImportTrack,
                enabled = !state.trackImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.trackImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(
                    stringResource(
                        if (state.trackImporting) R.string.track_importing
                        else R.string.track_import
                    )
                )
            }
            state.trackImportError?.let { message ->
                Text(
                    text = message,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.track_archive, state.savedTracks.size),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.savedTracks.isEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.track_archive_empty),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.savedTracks.take(12).forEach { saved ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = saved.name,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.track_file_meta,
                                    Fmt.timeShort(context, saved.modifiedAtMs),
                                    (saved.sizeBytes + 1023L) / 1024L,
                                ),
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onViewTrack(saved.path) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = stringResource(R.string.track_view_on_map),
                                tint = Color(0xFF4DD0C4),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { actions.onShareTrack(saved.path) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.track_share),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                if (state.savedTracks.size > 12) {
                    Text(
                        text = stringResource(R.string.track_archive_more, state.savedTracks.size - 12),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.track_hint),
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Footer() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.brand_rights),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

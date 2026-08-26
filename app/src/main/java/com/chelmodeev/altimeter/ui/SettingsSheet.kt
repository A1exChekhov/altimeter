package com.chelmodeev.altimeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.localization.AppLanguage
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.CalibrationMode
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.model.TrackSamplingMode
import com.chelmodeev.altimeter.util.Fmt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(state: UiState, actions: ScreenActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var selectedMode by remember { mutableStateOf(state.calibrationMode) }
        var manualText by remember {
            mutableStateOf(
                state.altitude?.let { Fmt.toUnit(it, state.unit).roundToInt().toString() } ?: ""
            )
        }
        var qnhText by remember {
            mutableStateOf(((state.qnhHpa * 10).roundToInt() / 10.0).toString())
        }
        val context = LocalContext.current
        var selectedLanguage by remember { mutableStateOf(AppLanguage.currentTag(context)) }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val languages = listOf(
                AppLanguage.SYSTEM to R.string.language_system,
                AppLanguage.RUSSIAN to R.string.language_russian,
                AppLanguage.ENGLISH to R.string.language_english,
                AppLanguage.CHINESE_SIMPLIFIED to R.string.language_chinese_simplified,
                AppLanguage.FRENCH to R.string.language_french,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                languages.forEach { (tag, label) ->
                    FilterChip(
                        selected = selectedLanguage == tag,
                        onClick = {
                            selectedLanguage = tag
                            actions.onSetLanguage(tag)
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.language_hint),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.settings_appearance),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.darkTheme,
                    onClick = { actions.onToggleDarkTheme(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.theme_light)) }
                SegmentedButton(
                    selected = state.darkTheme,
                    onClick = { actions.onToggleDarkTheme(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.theme_dark)) }
            }

            Spacer(Modifier.height(18.dp))

            SettingSwitch(
                label = stringResource(R.string.auto_track_setting),
                checked = state.autoTrackEnabled,
                onChecked = actions.onToggleAutoTrack,
            )
            Text(
                text = stringResource(R.string.auto_track_hint),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.track_sampling_title),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val samplingModes = listOf(
                TrackSamplingMode.AUTO to R.string.track_sampling_auto,
                TrackSamplingMode.EVERY_1S to R.string.track_sampling_1s,
                TrackSamplingMode.EVERY_2S to R.string.track_sampling_2s,
                TrackSamplingMode.EVERY_4S to R.string.track_sampling_4s,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                samplingModes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.trackSamplingMode == mode,
                        onClick = { actions.onSetTrackSampling(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, samplingModes.size),
                    ) { Text(stringResource(label)) }
                }
            }
            Text(
                text = stringResource(R.string.track_sampling_hint),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            // Единицы
            Text(
                text = stringResource(R.string.settings_units),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.unit == AltUnit.METERS,
                    onClick = { actions.onSetUnit(AltUnit.METERS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.unit_m)) }
                SegmentedButton(
                    selected = state.unit == AltUnit.FEET,
                    onClick = { actions.onSetUnit(AltUnit.FEET) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.unit_ft)) }
            }

            Spacer(Modifier.height(18.dp))

            // Калибровка
            Text(
                text = stringResource(R.string.settings_calibration),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedMode == CalibrationMode.AUTO_GPS,
                    onClick = {
                        selectedMode = CalibrationMode.AUTO_GPS
                        actions.onCalibAuto()
                    },
                    label = { Text(stringResource(R.string.calib_auto)) },
                )
                FilterChip(
                    selected = selectedMode == CalibrationMode.MANUAL_ALTITUDE,
                    onClick = { selectedMode = CalibrationMode.MANUAL_ALTITUDE },
                    enabled = state.hasBarometer,
                    label = { Text(stringResource(R.string.calib_manual)) },
                )
                FilterChip(
                    selected = selectedMode == CalibrationMode.QNH,
                    onClick = { selectedMode = CalibrationMode.QNH },
                    enabled = state.hasBarometer,
                    label = { Text(stringResource(R.string.calib_qnh)) },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (!state.hasBarometer) {
                Text(
                    text = stringResource(R.string.calib_manual_need_baro),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (selectedMode) {
                    CalibrationMode.AUTO_GPS -> {
                        Text(
                            text = stringResource(R.string.calib_auto_desc),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CalibrationMode.MANUAL_ALTITUDE -> {
                        Text(
                            text = stringResource(R.string.calib_manual_desc),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = manualText,
                                onValueChange = { manualText = it },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.calib_known_altitude,
                                            Fmt.unitLabel(androidx.compose.ui.platform.LocalContext.current, state.unit),
                                        )
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { actions.onCalibManual(manualText) }) {
                                Text(stringResource(R.string.apply))
                            }
                        }
                    }
                    CalibrationMode.QNH -> {
                        Text(
                            text = stringResource(R.string.calib_qnh_desc),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = qnhText,
                                onValueChange = { qnhText = it },
                                label = { Text(stringResource(R.string.calib_qnh_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { actions.onCalibQnh(qnhText) }) {
                                Text(stringResource(R.string.apply))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))

            SettingSwitch(
                label = stringResource(R.string.settings_map_topo),
                checked = state.topoMap,
                onChecked = actions.onToggleTopo,
            )
            Text(
                text = stringResource(R.string.settings_map_offline_cache),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.offline_maps_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.offline_maps_description),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = actions.onImportOfflineMap,
                    enabled = !state.offlineMaps.importing,
                ) {
                    if (state.offlineMaps.importing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                    }
                    Text(
                        stringResource(
                            if (state.offlineMaps.importing) R.string.offline_maps_importing
                            else R.string.offline_maps_import
                        )
                    )
                }
                OutlinedButton(
                    onClick = actions.onUseOnlineMap,
                    enabled = state.offlineMaps.activePath != null,
                ) {
                    Text(stringResource(R.string.offline_maps_online))
                }
            }
            state.offlineMaps.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.offlineMaps.catalog.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.offline_maps_catalog),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                state.offlineMaps.catalog.forEach { item ->
                    val installed = state.offlineMaps.installed.any { it.id == item.fileName }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${item.description} · ${formatMapSize(context, item.sizeBytes + item.terrainSizeBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            )
                        }
                        OutlinedButton(
                            onClick = { actions.onDownloadOfflineMap(item.id) },
                            enabled = !state.offlineMaps.importing,
                        ) {
                            Text(
                                stringResource(
                                    if (installed) R.string.offline_maps_update
                                    else R.string.offline_maps_download
                                )
                            )
                        }
                    }
                }
            }
            if (state.offlineMaps.installed.isEmpty()) {
                Text(
                    text = stringResource(R.string.offline_maps_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Spacer(Modifier.height(6.dp))
                state.offlineMaps.installed.forEach { region ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(region.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = buildString {
                                    append(formatMapSize(context, region.sizeBytes))
                                    region.sha256?.let { append(" · SHA-256 ").append(it.take(8)) }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                        FilterChip(
                            selected = region.active,
                            onClick = { actions.onActivateOfflineMap(region.id) },
                            label = {
                                Text(
                                    stringResource(
                                        if (region.active) R.string.offline_maps_active
                                        else R.string.offline_maps_use
                                    )
                                )
                            },
                        )
                        TextButton(onClick = { actions.onDeleteOfflineMap(region.id) }) {
                            Text(stringResource(R.string.offline_maps_delete))
                        }
                    }
                }
            }
            SettingSwitch(
                label = stringResource(R.string.settings_keep_on),
                checked = state.keepScreenOn,
                onChecked = actions.onToggleKeepOn,
            )
            SettingSwitch(
                label = stringResource(R.string.watch_auto),
                checked = state.autoSendToWatch,
                onChecked = actions.onToggleAutoSend,
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.onResetStats) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.settings_reset))
            }
        }
    }
}

private fun formatMapSize(context: android.content.Context, bytes: Long): String =
    android.text.format.Formatter.formatShortFileSize(context, bytes)

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

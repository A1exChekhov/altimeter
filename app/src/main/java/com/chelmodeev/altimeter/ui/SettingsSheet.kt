package com.chelmodeev.altimeter.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chelmodeev.altimeter.R
import com.chelmodeev.altimeter.model.AltUnit
import com.chelmodeev.altimeter.model.CalibrationMode
import com.chelmodeev.altimeter.model.UiState
import com.chelmodeev.altimeter.util.Fmt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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

package com.chelmodeev.altimeter.health

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.chelmodeev.altimeter.model.BluetoothVitalsState
import java.util.UUID

/**
 * Читает стандартную Bluetooth Heart Rate Measurement (0x2A37).
 * Работает без HUAWEI App ID, когда на часах включена «Трансляция данных ЧСС».
 */
class BluetoothHeartRateReader(private val context: Context) {

    interface Listener {
        fun onState(state: BluetoothVitalsState, deviceName: String? = null)
        fun onHeartRate(bpm: Long, deviceName: String?)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private var listener: Listener? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null
    private val timeout = Runnable {
        if (gatt == null) {
            stopScan()
            listener?.onState(BluetoothVitalsState.NOT_FOUND)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(listener: Listener) {
        this.listener = null
        stop()
        this.listener = listener
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onState(BluetoothVitalsState.BLUETOOTH_OFF)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            listener.onState(BluetoothVitalsState.ERROR)
            return
        }
        listener.onState(BluetoothVitalsState.SCANNING)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.advertisesHeartRate()) connect(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.firstOrNull { it.advertisesHeartRate() }?.let(::connect)
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
                listener.onState(BluetoothVitalsState.ERROR)
            }
        }
        scanCallback = callback
        // Some Android Bluetooth stacks intermittently drop hardware-filtered
        // results. Scan broadly and apply the same Heart Rate Service filter in
        // the app so a previously working watch remains discoverable.
        scanner.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            callback,
        )
        handler.postDelayed(timeout, SCAN_TIMEOUT_MS)
    }

    private fun ScanResult.advertisesHeartRate(): Boolean {
        val record = scanRecord ?: return false
        return record.serviceUuids.orEmpty().any { it.uuid == HEART_RATE_SERVICE } ||
            record.serviceData.keys.any { it.uuid == HEART_RATE_SERVICE }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(timeout)
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectedDeviceName = null
    }

    @SuppressLint("MissingPermission")
    private fun connect(result: ScanResult) {
        if (gatt != null) return
        stopScan()
        handler.removeCallbacks(timeout)
        connectedDeviceName = runCatching { result.device.name }.getOrNull()
        listener?.onState(BluetoothVitalsState.CONNECTING, connectedDeviceName)
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            result.device.connectGatt(
                context,
                false,
                gattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE,
            )
        } else {
            @Suppress("DEPRECATION")
            result.device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanCallback = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (this@BluetoothHeartRateReader.gatt !== gatt) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener?.onState(BluetoothVitalsState.CONNECTING, connectedDeviceName)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    this@BluetoothHeartRateReader.gatt = null
                    listener?.onState(BluetoothVitalsState.NOT_FOUND, connectedDeviceName)
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(HEART_RATE_SERVICE)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                listener?.onState(BluetoothVitalsState.ERROR, connectedDeviceName)
                return
            }
            val notificationsEnabled = gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (!notificationsEnabled || descriptor == null) {
                listener?.onState(BluetoothVitalsState.ERROR, connectedDeviceName)
                return
            }
            val writeStarted = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (writeStarted) {
                listener?.onState(BluetoothVitalsState.CONNECTED, connectedDeviceName)
            } else {
                listener?.onState(BluetoothVitalsState.ERROR, connectedDeviceName)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            parseHeartRate(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parseHeartRate(characteristic.uuid, value)
        }
    }

    private fun parseHeartRate(uuid: UUID, value: ByteArray) {
        if (uuid != HEART_RATE_MEASUREMENT || value.size < 2) return
        val flags = value[0].toInt() and 0xFF
        val bpm = if (flags and 0x01 == 0) {
            value[1].toInt() and 0xFF
        } else {
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        }
        if (bpm in 1..255) listener?.onHeartRate(bpm.toLong(), connectedDeviceName)
    }

    companion object {
        private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_TIMEOUT_MS = 30_000L
    }
}

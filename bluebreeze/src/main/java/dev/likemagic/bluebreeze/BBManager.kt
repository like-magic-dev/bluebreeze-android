//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BBManager(
    context: Context,
) : BroadcastReceiver() {
    // region Permissions

    private val _authorizationStatus = MutableStateFlow(BBAuthorization.unknown)
    val authorizationStatus: StateFlow<BBAuthorization> get() = _authorizationStatus

    private val authorizationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    init {
        _authorizationStatus.value = authorizationCheck(context)
    }

    private fun authorizationCheck(context: Context): BBAuthorization {
        // Check if all permissions are already granted
        val granted =
            authorizationPermissions.map { ContextCompat.checkSelfPermission(context, it) }
        if (granted.all { it == PackageManager.PERMISSION_GRANTED }) {
            return BBAuthorization.authorized
        }

        // If some permissions have not been requested yet, we do not know the status
        val requested =
            authorizationPermissions.map { context.sharedPreferences.getBoolean(it, false) }
        if (requested.any { !it }) {
            return BBAuthorization.unknown
        }

        // Check if any permission has been denied once and needs a rationale
        if (authorizationPermissions
                .any {
                    (context is Activity) && ActivityCompat.shouldShowRequestPermissionRationale(
                        context,
                        it
                    )
                }
        ) {
            return BBAuthorization.showRationale
        }

        // The permissions have been fully denied
        return BBAuthorization.denied
    }

    fun authorizationRequest(context: Context) {
        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BBPermissionRequestActivity.GRANTED)
        intentFilter.addAction(BBPermissionRequestActivity.SHOW_RATIONALE)
        intentFilter.addAction(BBPermissionRequestActivity.DENIED)

        // Register a broadcast receiver
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, intentFilter)
        } else {
            context.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        }

        // Start the hidden activity to request permissions
        val intent = Intent(context, BBPermissionRequestActivity::class.java)
        intent.putExtra(BBPermissionRequestActivity.KEY, authorizationPermissions)
        context.startActivity(intent)

        // Save the requested permissions
        val editor = context.sharedPreferences.edit()
        authorizationPermissions.forEach {
            editor.putBoolean(it, true)
        }
        editor.apply()
    }

    fun authorizationOpenSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    class BBPermissionRequestActivity : Activity() {
        companion object {
            const val KEY = "BBPermissionRequestActivity.key"
            const val GRANTED = "BBPermissionRequestActivity.granted"
            const val SHOW_RATIONALE = "BBPermissionRequestActivity.showRationale"
            const val DENIED = "BBPermissionRequestActivity.denied"
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState == null) {
                ActivityCompat.requestPermissions(
                    this,
                    intent.getStringArrayExtra(KEY) ?: emptyArray(),
                    1
                )
            }
        }

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            val shouldShowRationale = permissions.map {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            sendBroadcast(
                Intent(
                    if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                        GRANTED
                    else if (shouldShowRationale.any { it })
                        SHOW_RATIONALE
                    else
                        DENIED
                ).setPackage(
                    packageName
                )
            )

            finish()
        }
    }

    // endregion

    // region State

    private val _state = MutableStateFlow(BBState.unknown)
    val state: StateFlow<BBState> get() = _state

    init {
        _state.value = stateCheck(context)
    }

    private fun stateCheck(context: Context): BBState {
        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)

        // Register a broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(this, intentFilter)
        }

        // Retrieve the current state
        return when (context.bluetoothAdapter?.isEnabled) {
            true -> BBState.poweredOn
            else -> BBState.poweredOff
        }
    }

    // endregion

    // region Devices

    private val _devices = MutableStateFlow<Map<String, BBDevice>>(mapOf())
    val devices: StateFlow<Map<String, BBDevice>> get() = _devices

    // end region

    // region Scan

    private val _scanEnabled = MutableStateFlow(false)
    val scanEnabled: StateFlow<Boolean> get() = _scanEnabled

    private val _scanResults = MutableSharedFlow<BBScanResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scanResults: SharedFlow<BBScanResult> get() = _scanResults

    private val scanTimes: MutableList<Long> = ArrayList()

    fun scanStart(
        context: Context,
        serviceUUIDs: List<BBUUID>? = null
    ) {
        if (scanEnabled.value) {
            return
        }

        /// !!! LOW POWER does not work on some devices, DO NOT CHANGE !!!
        val bluetoothScanMode = ScanSettings.SCAN_MODE_LOW_LATENCY

        val scanSettingsBuilder = ScanSettings.Builder()
        scanSettingsBuilder.setScanMode(bluetoothScanMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scanSettingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        val scanSettings = scanSettingsBuilder.build()

        val scanFilters = serviceUUIDs?.map {
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(it.toString()))
                .build()
        }

        // When scanning more than 5 times in 30 seconds, the system will block our app from scanning.
        // We need to catch this condition and prevent calling *startScan* below.
        val currentTime = System.currentTimeMillis()
        if (scanTimes.size < 5) {
            scanTimes.add(currentTime)
        } else {
            val deltaTime = (currentTime - scanTimes[0])

            // We throw an exception so that the app code can restart scanning after the specified time
            if (deltaTime < 30000) {
                val timeToWait = 30f - (deltaTime * 0.001f)
                throw BBError.scan(timeToWait)
            }

            scanTimes.removeAt(0)
            scanTimes.add(currentTime)
        }

        context.bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        _scanEnabled.value = true
    }

    fun scanStop(context: Context) {
        if (!scanEnabled.value) {
            return
        }

        context.bluetoothLeScanner?.stopScan(scanCallback)
        _scanEnabled.value = false
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        private fun parseAdvertisedData(advertisedData: ByteArray): Map<UByte, ByteArray> {
            val result: MutableMap<UByte, ByteArray> = mutableMapOf()

            val buffer = advertisedData.byteBuffer()
            while (buffer.remaining() > 2) {
                val length = buffer.get().toInt()
                if (length == 0) {
                    break
                }

                if (length > buffer.remaining()) {
                    break
                }

                val type = buffer.get().toUByte()

                val value = ByteArray(length - 1)
                for (index in 0 until length - 1) {
                    value[index] = buffer.get()
                }

                result[type] = value
            }

            return result
        }

        private fun parseAdvertisedServices(
            advertisedData: Map<UByte, ByteArray>
        ): List<BBUUID> {
            val uuids: MutableList<BBUUID> = mutableListOf()

            for (entry in advertisedData) {
                val buffer = ByteBuffer.wrap(entry.value)

                when (entry.key) {
                    BBConstants.Advertisement.UUIDS_16_BIT_INCOMPLETE,
                    BBConstants.Advertisement.UUIDS_16_BIT_COMPLETE ->
                        while (buffer.remaining() >= 2) {
                            val bytes = (0 until 2).map { buffer.get() }
                            val uuidShort =
                                bytes.reversed().joinToString(separator = "") { it.hexString }
                            uuids.add(BBUUID.fromString(uuidShort))
                        }

                    BBConstants.Advertisement.UUIDS_128_BIT_INCOMPLETE,
                    BBConstants.Advertisement.UUIDS_128_BIT_COMPLETE ->
                        while (buffer.remaining() >= 16) {
                            val bytes = (0 until 16).map { buffer.get() }
                            val uuid = listOf(
                                bytes.subList(12, 16).reversed()
                                    .joinToString(separator = "") { it.hexString },
                                bytes.subList(10, 12).reversed()
                                    .joinToString(separator = "") { it.hexString },
                                bytes.subList(8, 10).reversed()
                                    .joinToString(separator = "") { it.hexString },
                                bytes.subList(6, 8).reversed()
                                    .joinToString(separator = "") { it.hexString },
                                bytes.subList(0, 6).reversed()
                                    .joinToString(separator = "") { it.hexString },
                            ).joinToString(separator = "-")
                            uuids.add(BBUUID.fromString(uuid))
                        }
                }
            }

            return uuids
        }


        private fun processScanResult(result: ScanResult) {
            val device = devices.value[result.device.address] ?: BBDevice(context, result.device)

            // Update the devices
            if (devices.value[result.device.address] == null) {
                _devices.value = devices.value.toMutableMap().apply {
                    this[device.address] = device
                }
            }

            // Compute scan result properties
            val advertisementData = result.scanRecord?.bytes?.let {
                parseAdvertisedData(it)
            } ?: emptyMap()

            val connectable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                true
            else
                result.isConnectable

            // Send the scan result
            val scanResult = BBScanResult(
                device = device,
                rssi = result.rssi,
                advertisementData = advertisementData,
                advertisedServices = parseAdvertisedServices(advertisementData),
                connectable = connectable
            )
            _scanResults.tryEmit(scanResult)
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            processScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach {
                processScanResult(it)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _scanEnabled.value = false
        }
    }

    // endregion

    // region Broadcast receiver

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                BBPermissionRequestActivity.GRANTED -> {
                    _authorizationStatus.value = BBAuthorization.authorized
                }

                BBPermissionRequestActivity.SHOW_RATIONALE -> {
                    _authorizationStatus.value = BBAuthorization.showRationale
                }

                BBPermissionRequestActivity.DENIED -> {
                    _authorizationStatus.value = BBAuthorization.denied
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF -> {
                            _state.value = BBState.poweredOff
                        }

                        BluetoothAdapter.STATE_ON -> {
                            _state.value = BBState.poweredOn
                        }

                        BluetoothAdapter.STATE_TURNING_ON -> {}
                        BluetoothAdapter.STATE_TURNING_OFF -> {}
                    }
                }
            }
        }
    }
}

val Context.sharedPreferences: SharedPreferences
    get() = getSharedPreferences("BlueBreeze", Context.MODE_PRIVATE)

val Context.bluetoothAdapter: BluetoothAdapter?
    get() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

val Context.bluetoothLeScanner: BluetoothLeScanner?
    get() = bluetoothAdapter?.bluetoothLeScanner

fun ByteArray.byteBuffer(): ByteBuffer {
    val byteBuffer = ByteBuffer.wrap(this)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    return byteBuffer
}

val Byte.hexString: String get() = toUByte().toString(16).uppercase().padStart(2, '0')

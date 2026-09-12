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
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.likemagic.bluebreeze.flows.MutableSharedStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.content.edit
import kotlinx.coroutines.channels.BufferOverflow

/**
 * The top-level entry point to BlueBreeze: Bluetooth permissions, adapter power state, scanning,
 * and the registry of every [BBDevice] discovered so far.
 *
 * Construct one `BBManager` per app (it registers broadcast receivers for its lifetime -- call
 * [close] when you're done with it) and use it to request permissions, wait for
 * [state] to become [BBState.poweredOn], then [scanStart] and collect [scanResults].
 * ```kotlin
 * val manager = BBManager(context)
 * if (manager.authorizationStatus.value != BBAuthorization.authorized) {
 *     manager.authorizationRequest(activity)
 * }
 * manager.scanStart(context)
 * manager.scanResults.collect { result ->
 *     // result.device is the same BBDevice instance on every subsequent sighting
 * }
 * ```
 */
class BBManager(
    context: Context,
) : BroadcastReceiver() {
    private val appContext: Context = context.applicationContext

    // region Permissions

    private val _authorizationStatus = MutableSharedStateFlow(BBAuthorization.unknown)

    /** The app's current authorization status for the Bluetooth permissions BlueBreeze needs. Call [authorizationRequest] to request them if this isn't [BBAuthorization.authorized]. */
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
        _authorizationStatus.emit(authorizationCheck(context))
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
            authorizationPermissions.map { sharedPreferences(context).getBoolean(it, false) }
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

    private var authorizationReceiverRegistered = false

    // Register a broadcast receiver once
    private fun authorizationRegisterReceiver() {
        // Already registered
        if (authorizationReceiverRegistered) {
            return
        }

        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BBPermissionRequestActivity.GRANTED)
        intentFilter.addAction(BBPermissionRequestActivity.SHOW_RATIONALE)
        intentFilter.addAction(BBPermissionRequestActivity.DENIED)

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(this, intentFilter)
        } else {
            appContext.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        }

        authorizationReceiverRegistered = true
    }

    /**
     * Requests every permission BlueBreeze needs, updating [authorizationStatus] with the
     * result. Launches a hidden activity to perform the actual system permission request, so
     * this can be called from any [Context], not just an [Activity].
     */
    fun authorizationRequest(context: Context) {
        // Register a broadcast receiver
        authorizationRegisterReceiver()

        // Start the hidden activity to request permissions
        val intent = Intent(context, BBPermissionRequestActivity::class.java)
        intent.putExtra(BBPermissionRequestActivity.KEY, authorizationPermissions)

        if (context !is Activity) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        // Save the requested permissions
        sharedPreferences(context).edit {
            authorizationPermissions.forEach {
                putBoolean(it, true)
            }
        }
    }

    /**
     * Opens the app's system settings screen -- the only way to recover once a permission has
     * been permanently denied ([BBAuthorization.denied]), since the system will no longer show
     * its own request dialog for it.
     */
    fun authorizationOpenSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * A transparent, otherwise-invisible activity used to launch the system permission-request
     * dialog and broadcast its result back to [BBManager]. Declared in BlueBreeze's manifest;
     * not meant to be referenced or started directly by app code.
     */
    internal class BBPermissionRequestActivity : AppCompatActivity() {
        companion object {
            const val KEY = "BBPermissionRequestActivity.key"
            const val GRANTED = "BBPermissionRequestActivity.granted"
            const val SHOW_RATIONALE = "BBPermissionRequestActivity.showRationale"
            const val DENIED = "BBPermissionRequestActivity.denied"
        }

        private val permissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted: Map<String, Boolean> ->
            val shouldShowRationale = granted.keys.map {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            sendBroadcast(
                Intent(
                    if (granted.values.all { it })
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

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState == null) {
                permissionRequest.launch(intent.getStringArrayExtra(KEY) ?: emptyArray())
            }
        }
    }

    // endregion

    // region Capabilities

    /** Whether this device's Bluetooth adapter supports extended LE advertising (larger/longer advertisements, secondary advertising channels). Always `false` below API 26. */
    val supportsExtended: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        bluetoothAdapter(context)?.isLeExtendedAdvertisingSupported ?: false
    } else {
        false
    }

    // endregion

    // region State

    private val _state = MutableSharedStateFlow(BBState.unknown)

    /** The Bluetooth adapter's current power state. Scanning and connecting require [BBState.poweredOn]. */
    val state: StateFlow<BBState> get() = _state

    init {
        _state.emit(stateCheck(context))
    }

    private fun stateCheck(context: Context): BBState {
        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)

        // Register a broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(this, intentFilter)
        }

        // Retrieve the current state
        return when (bluetoothAdapter(context)?.isEnabled) {
            true -> BBState.poweredOn
            else -> BBState.poweredOff
        }
    }

    // Publishes the new adapter state and pushes it down to every known device
    private fun updateState(state: BBState) {
        _state.emit(state)
        devices.value.values.forEach { it.onAdapterStateChanged(state) }
    }

    // endregion

    // region Devices

    private val _devices = MutableSharedStateFlow<Map<String, BBDevice>>(mapOf())

    /**
     * Every [BBDevice] discovered by a scan so far, keyed by MAC address. A device is added here
     * the first time it's seen in a scan result and then reused for every subsequent sighting,
     * connect, and disconnect -- use this (or [BBScanResult.device]) rather than constructing
     * your own.
     */
    val devices: StateFlow<Map<String, BBDevice>> get() = _devices

    // Guards the access to the devices map
    private val devicesLock = Any()

    // end region

    // region Scan

    private val _scanEnabled = MutableSharedStateFlow(false)

    /** Whether a scan is currently active. Reflects [scanStart]/[scanStop], and flips to `false` on its own if the adapter powers off or the system stops the scan for another reason. */
    val scanEnabled: StateFlow<Boolean> get() = _scanEnabled

    private val _scanResults = MutableSharedFlow<BBScanResult>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every advertisement seen while scanning, including repeats from the same peripheral. Only delivered to collectors while they're actively collecting -- there's no replay, unlike [devices]/[state]. */
    val scanResults: SharedFlow<BBScanResult> get() = _scanResults

    private val scanTimes: MutableList<Long> = ArrayList()
    private val scanTimesLock = Any()
    private val scanWindowMillis = 30_000L
    private val scanWindowMaxStarts = 5

    // Remembers the last scan request and the optional service UUIDs
    private var scanRequested = false
    private var scanServiceUUIDs: List<BBUUID>? = null

    /**
     * Starts scanning for BLE advertisements, updating [scanEnabled] and delivering results on
     * [scanResults]. Returns immediately if already scanning.
     *
     * Automatically resumes with the same [serviceUUIDs] filter if the Bluetooth adapter is
     * power-cycled while a scan is active -- you don't need to call this again after a
     * [BBState.poweredOff]/[BBState.poweredOn] transition.
     *
     * @param serviceUUIDs restrict results to peripherals advertising at least one of these
     * service UUIDs, or `null` to see every advertisement.
     * @throws BBError.scan if called more than 5 times within a rolling 30-second window -- the
     * system's own limit on `startScan` calls. The thrown error carries how many seconds remain
     * before retrying is worthwhile.
     */
    fun scanStart(
        context: Context,
        serviceUUIDs: List<BBUUID>? = null
    ) {
        if (scanEnabled.value) {
            return
        }

        /// !!! LOW POWER does not work on some devices, DO NOT CHANGE !!!
        val bluetoothScanMode = ScanSettings.SCAN_MODE_LOW_LATENCY

        val scanSettings = ScanSettings.Builder().apply {
            setScanMode(bluetoothScanMode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            }
        }.build()

        val scanFilters = serviceUUIDs?.map {
            ScanFilter.Builder()
                .setServiceUuid(it.parcelUUID)
                .build()
        }

        // The system blocks an app that calls startScan more than [scanWindowMaxStarts] times
        // within [scanWindowMillis] milliseconds. Keep track of all recent starts and throw
        // early with BBError.scan so the caller can back off.
        // BBError.scan carries a time-to-wait value which can be used at application level.
        synchronized(scanTimesLock) {
            val currentTime = System.currentTimeMillis()
            scanTimes.removeAll { currentTime - it >= scanWindowMillis }
            if (scanTimes.size >= scanWindowMaxStarts) {
                val timeToWait = (scanWindowMillis - (currentTime - scanTimes.first())) * 0.001f
                throw BBError.scan(timeToWait)
            }

            val scanner = bluetoothLeScanner(context)
            if (scanner != null) {
                scanner.startScan(scanFilters, scanSettings, scanCallback)
                scanTimes.add(currentTime)
            }
        }

        scanRequested = true
        scanServiceUUIDs = serviceUUIDs

        _scanEnabled.emit(true)
    }

    /** Stops an active scan. Returns immediately if not currently scanning. */
    fun scanStop(context: Context) {
        scanRequested = false
        scanServiceUUIDs = null

        if (!scanEnabled.value) {
            return
        }

        bluetoothLeScanner(context)?.stopScan(scanCallback)
        _scanEnabled.emit(false)
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        // Wraps this byte array in a little-endian ByteBuffer -- BLE advertisement data is
        // little-endian throughout.
        private fun ByteArray.byteBuffer(): ByteBuffer {
            val byteBuffer = ByteBuffer.wrap(this)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            return byteBuffer
        }

        // This byte as two uppercase hex digits (e.g. 0x0A -> "0A"), used to build UUID strings
        // out of raw advertisement bytes.
        private val Byte.hexString: String get() = toUByte().toString(16).uppercase().padStart(2, '0')

        private fun parseAdvertisedData(advertisedData: ByteArray): Map<UByte, ByteArray> {
            val result: MutableMap<UByte, ByteArray> = mutableMapOf()

            val buffer = advertisedData.byteBuffer()
            while (buffer.remaining() >= 2) {
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
            // Update the devices, synchronized to prevent concurrent modifications to the map
            val device = synchronized(devicesLock) {
                devices.value[result.device.address] ?: BBDevice(context, result.device).also { newDevice ->
                    _devices.emit(devices.value + (result.device.address to newDevice))
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
            _scanEnabled.emit(false)
        }
    }

    // endregion

    // region Broadcast receiver

    // Routes every broadcast this manager registered for (adapter state changes, and the
    // permission-request result from BBPermissionRequestActivity) to the matching state update.
    // Not meant to be called directly -- BroadcastReceiver requires this override to be public.
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                BBPermissionRequestActivity.GRANTED -> {
                    _authorizationStatus.emit(BBAuthorization.authorized)
                }

                BBPermissionRequestActivity.SHOW_RATIONALE -> {
                    _authorizationStatus.emit(BBAuthorization.showRationale)
                }

                BBPermissionRequestActivity.DENIED -> {
                    _authorizationStatus.emit(BBAuthorization.denied)
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF -> {
                            updateState(BBState.poweredOff)

                            // The OS drops any active scan when the adapter powers off
                            _scanEnabled.emit(false)
                        }

                        BluetoothAdapter.STATE_ON -> {
                            updateState(BBState.poweredOn)

                            // Resume a scan that was running before the power cycle
                            if (scanRequested) {
                                runCatching { scanStart(appContext, scanServiceUUIDs) }
                            }
                        }

                        BluetoothAdapter.STATE_TURNING_ON -> {}
                        BluetoothAdapter.STATE_TURNING_OFF -> {}
                    }
                }
            }
        }
    }

    // endregion

    // region Lifecycle

    /**
     * Unregisters every broadcast receiver this manager registered. Call this when the manager
     * is no longer needed -- constructing another `BBManager` without calling this first leaks
     * the receivers, and keeps delivering their broadcasts to the dead instance.
     */
    fun close() {
        runCatching { appContext.unregisterReceiver(this) }
        authorizationReceiverRegistered = false
    }

    // region Helpers

    // BlueBreeze's own SharedPreferences file, used to remember which permissions have already
    // been requested once.
    private fun sharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("BlueBreeze", Context.MODE_PRIVATE)

    // context's BluetoothAdapter, or null if the device has no Bluetooth hardware.
    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    // context's BluetoothLeScanner, or null if there's no adapter to get one from.
    private fun bluetoothLeScanner(context: Context): BluetoothLeScanner? =
        bluetoothAdapter(context)?.bluetoothLeScanner

    // endregion
}

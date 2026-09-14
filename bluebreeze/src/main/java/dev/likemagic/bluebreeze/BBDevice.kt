//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.flows.MutableSharedStateFlow
import dev.likemagic.bluebreeze.operations.BBOperation
import dev.likemagic.bluebreeze.operations.BBOperationConnect
import dev.likemagic.bluebreeze.operations.BBOperationDisconnect
import dev.likemagic.bluebreeze.operations.BBOperationDiscoverServices
import dev.likemagic.bluebreeze.operations.BBOperationQueue
import dev.likemagic.bluebreeze.operations.BBOperationRequestMtu
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * A single BLE peripheral discovered by a [BBManager] scan.
 *
 * `BBDevice` instances are created and owned by [BBManager] -- you never construct one yourself,
 * you get one from [BBManager.devices] or [BBScanResult.device]. The same instance is reused
 * across repeated discoveries, connects, and disconnects of the same physical peripheral.
 *
 * All BLE operations ([connect], [disconnect], [discoverServices], [requestMtu], and the
 * read/write/subscribe methods on [BBCharacteristic]) are queued and executed one at a time per
 * device, in call order, each with a 5-second timeout -- you don't need to serialize calls
 * yourself, just call the suspend functions.
 *
 * Typical flow: connect, discover services, then read/write/subscribe to the characteristics
 * that appear in [services].
 * ```kotlin
 * device.connect()
 * device.discoverServices()
 *
 * for (service in device.services.value) {
 *     for (characteristic in service.characteristics) {
 *         if (BBCharacteristicProperty.read in characteristic.properties) {
 *             val data = characteristic.read()
 *         }
 *     }
 * }
 * ```
 */
class BBDevice internal constructor(
    private val context: Context,
    internal val device: BluetoothDevice,
) : BluetoothGattCallback() {
    private val operationQueue = BBOperationQueue(context, device)

    // Long-lived scope for delivering connection-state changes
    private val callbackScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            Log.w(BBConstants.LOG_TAG, "GATT callback coroutine failed", e)
        }
    )

    // region Properties

    /** The peripheral's MAC address. Stable for the lifetime of the app, but Android randomizes it per-bond for privacy on many peripherals, so don't expect it to match across un-paired re-discoveries. */
    val address: String
        get() = device.address

    /** The peripheral's name, if the OS already knows one (e.g. from a previous bond). May be `null` -- prefer [BBScanResult.name] while scanning, which also reads the advertised name. */
    val name: String?
        get() = device.name

    // endregion

    // region Services

    private val _services = MutableSharedStateFlow(emptyList<BBService>())

    /** Services discovered so far. Empty until [discoverServices] has been called and completed. */
    val services: StateFlow<List<BBService>> get() = _services

    // endregion

    // region Connection status

    private val _connectionStatus = MutableSharedStateFlow(BBDeviceConnectionStatus.disconnected)

    /**
     * The device's current connection state. Updates automatically on connect, disconnect, and
     * unexpected link loss (including the whole Bluetooth adapter powering off) -- you don't
     * need to poll it after calling [connect]/[disconnect].
     */
    val connectionStatus: StateFlow<BBDeviceConnectionStatus> get() = _connectionStatus

    // endregion

    // region MTU

    private val _mtu = MutableSharedStateFlow(BBConstants.DEFAULT_MTU)

    /** The negotiated ATT MTU in bytes -- the largest amount of data that fits in a single read/write. [BBConstants.DEFAULT_MTU] (23) until [requestMtu] is called and awaited. */
    val mtu: StateFlow<Int> get() = _mtu

    // endregion

    // region Operations

    private val maxRetriesOnGattError = 3

    /**
     * Connects to the peripheral. Updates [connectionStatus] to [BBDeviceConnectionStatus.connected]
     * on success. Returns immediately if already connected.
     *
     * Retries automatically, with increasing delay, if the connection attempt fails with GATT
     * status 133 (a transient failure Android peripherals hit occasionally) -- up to
     * [maxRetriesOnGattError] attempts before giving up. Any other error is thrown immediately
     * without retrying.
     *
     * @throws BBError if every attempt fails or times out.
     */
    suspend fun connect() {
        for (i in 0..<maxRetriesOnGattError) {
            delay((i * 1000L).milliseconds)

            try {
                return operationEnqueue(
                    BBOperationConnect(this)
                )
            } catch (e: BBErrorGatt) {
                if (i == maxRetriesOnGattError-1) {
                    // Last retry failed, throw error immediately
                    throw e
                } else if (e.code == BBErrorGatt.GATT_ERROR) {
                    // We caught GATT_ERROR (133), which occurs at times when connecting;
                    // retry the connection at increasingly large time intervals
                    continue
                } else {
                    // Other errors are thrown immediately
                    throw e
                }
            }
        }
    }

    /**
     * Disconnects from the peripheral. Updates [connectionStatus] to
     * [BBDeviceConnectionStatus.disconnected] on success. Cancels any other operation currently
     * queued or in flight on this device first.
     *
     * @throws BBError if the disconnect attempt fails or times out.
     */
    suspend fun disconnect() {
        operationQueue.cancelAll()

        return operationEnqueue(
            BBOperationDisconnect(operationQueue)
        )
    }

    /**
     * Discovers all the peripheral's services and their characteristics.
     * Requires an active connection.
     *
     * @throws BBError if discovery fails or times out.
     */
    suspend fun discoverServices() {
        return operationEnqueue(
            BBOperationDiscoverServices()
        )
    }

    /**
     * Requests a larger ATT MTU, updating [mtu] with whatever size the peripheral actually
     * negotiates (which may be smaller than requested).
     *
     * @param mtu the desired MTU size in bytes.
     * @return the negotiated MTU size.
     * @throws BBError if the request fails or times out.
     */
    suspend fun requestMtu(mtu: Int): Int {
        return operationEnqueue(
            BBOperationRequestMtu(mtu)
        )
    }

    // endregion

    // region Operation queue

    internal suspend fun <T> operationEnqueue(operation: BBOperation<T>): T =
        operationQueue.operationEnqueue(operation)

    // endregion

    // region Bluetooth callback

    /**
     * Called by [BBManager] whenever the Bluetooth adapter's power state changes.
     *
     * Forces this device's connection into [BBDeviceConnectionStatus.disconnected] whenever
     * [state] isn't [BBState.poweredOn]: the OS silently invalidates any live GATT client when
     * the adapter powers off, without a matching [onConnectionStateChange] callback, so this is
     * the only way this device otherwise learns its connection is gone.
     */
    internal fun onAdapterStateChanged(state: BBState) {
        if (state != BBState.poweredOn) {
            connectionLost()
        }
    }

    // endregion

    // region Bluetooth GATT callback

    // The overrides below mirror BluetoothGattCallback; BBManager forwards GATT callbacks for
    // this device's peripheral into these methods. Not meant to be called directly, and not part
    // of BlueBreeze's public API despite being public (Kotlin requires overrides to match their
    // superclass's visibility).

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        gatt ?: return

        callbackScope.launch {
            services.value.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    characteristic.onConnectionStateChange(gatt, status, newState)
                }
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                _connectionStatus.emit(BBDeviceConnectionStatus.connected)
            }

            operationQueue.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                connectionLost()
            }
        }
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt?,
        status: Int
    ) {
        gatt ?: return

        _services.emit(
            gatt.services.map {
                BBService(
                    uuid = BBUUID(uuid = it.uuid),
                    characteristics = it.characteristics.map {
                        BBCharacteristic(
                            characteristic = it,
                            operationQueue = operationQueue,
                        )
                    })
            })

        operationQueue.onServicesDiscovered(gatt, status)
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt?,
        mtu: Int,
        status: Int
    ) {
        gatt ?: return

        operationQueue.onMtuChanged(gatt, mtu, status)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: return
        descriptor ?: return

        characteristic(descriptor.characteristic.uuid)?.onDescriptorRead(gatt, descriptor, status)

        operationQueue.onDescriptorRead(gatt, descriptor, status)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        characteristic(descriptor.characteristic.uuid)?.onDescriptorRead(
            gatt, descriptor, status, value
        )

        operationQueue.onDescriptorRead(gatt, descriptor, status, value)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: return
        descriptor ?: return

        characteristic(descriptor.characteristic.uuid)?.onDescriptorWrite(gatt, descriptor, status)

        operationQueue.onDescriptorWrite(gatt, descriptor, status)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        characteristic(characteristic.uuid)?.onCharacteristicRead(gatt, characteristic, status)

        operationQueue.onCharacteristicRead(gatt, characteristic, status)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        characteristic(characteristic.uuid)?.onCharacteristicRead(
            gatt,
            characteristic,
            value,
            status
        )

        operationQueue.onCharacteristicRead(gatt, characteristic, value, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        characteristic(characteristic.uuid)?.onCharacteristicWrite(gatt, characteristic, status)

        operationQueue.onCharacteristicWrite(gatt, characteristic, status)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return

        characteristic(characteristic.uuid)?.onCharacteristicChanged(gatt, characteristic)

        operationQueue.onCharacteristicChanged(gatt, characteristic)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        characteristic(characteristic.uuid)?.onCharacteristicChanged(gatt, characteristic, value)

        operationQueue.onCharacteristicChanged(gatt, characteristic, value)
    }

    // endregion

    // region Connection state handling

    // Forces this device into the disconnected state and tears down its GATT client
    private fun connectionLost() {
        _connectionStatus.emit(BBDeviceConnectionStatus.disconnected)
        _mtu.emit(BBConstants.DEFAULT_MTU)
        _services.emit(emptyList())

        operationQueue.reset()
    }

    // endregion

    // region Lookup

    /** Finds a discovered characteristic by UUID across every service in [services], or `null` if none matches. */
    fun characteristic(uuid: UUID): BBCharacteristic? {
        services.value.forEach { service ->
            service.characteristics.forEach { characteristic ->
                if (characteristic.uuid.equals(uuid)) {
                    return characteristic
                }
            }
        }

        return null
    }

    // endregion
}

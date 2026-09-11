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

    val address: String
        get() = device.address

    val name: String?
        get() = device.name

    // endregion

    // region Services

    private val _services = MutableSharedStateFlow(emptyList<BBService>())
    val services: StateFlow<List<BBService>> get() = _services

    // endregion

    // region Connection status

    private val _connectionStatus = MutableSharedStateFlow(BBDeviceConnectionStatus.disconnected)
    val connectionStatus: StateFlow<BBDeviceConnectionStatus> get() = _connectionStatus

    // endregion

    // region MTU

    private val _mtu = MutableSharedStateFlow(BBConstants.DEFAULT_MTU)
    val mtu: StateFlow<Int> get() = _mtu

    // endregion

    // region Operations

    private val maxRetriesOnGattError = 3

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

    suspend fun disconnect() {
        operationQueue.cancelAll()

        return operationEnqueue(
            BBOperationDisconnect()
        )
    }

    suspend fun discoverServices() {
        return operationEnqueue(
            BBOperationDiscoverServices()
        )
    }

    suspend fun requestMTU(mtu: Int): Int {
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

    internal fun onAdapterStateChanged(state: BBState) {
        if (state != BBState.poweredOn) {
            connectionLost()
        }
    }

    // endregion

    // region Bluetooth GATT callback

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

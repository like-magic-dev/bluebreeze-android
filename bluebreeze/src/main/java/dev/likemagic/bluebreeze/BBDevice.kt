//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import BBUUID
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.operations.BBOperationDiscoverServices
import dev.likemagic.bluebreeze.operations.BBOperationRequestMtu
import dev.likemagic.bluebreeze.operations.BBOperationConnect
import dev.likemagic.bluebreeze.operations.BBOperationDisconnect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.Charset
import java.util.Timer
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.suspendCoroutine
import kotlin.concurrent.schedule

class BBDevice(
    val context: Context,
    val device: BluetoothDevice,
): BluetoothGattCallback(), BBOperationQueue {
    private var gatt: BluetoothGatt? = null

    // region Properties

    val address: String
        get() = device.address

    val name: String?
        get() = device.name ?:
            advertisementData[BBConstants.Advertisement.LOCAL_NAME]?.toString(Charset.defaultCharset()) ?:
            advertisementData[BBConstants.Advertisement.LOCAL_NAME_SHORTENED]?.toString(Charset.defaultCharset()) ?:
            advertisementData[BBConstants.Advertisement.BROADCAST_NAME]?.toString(Charset.defaultCharset())

    var rssi: Int = 0

    var advertisementData: Map<UByte, ByteArray> = emptyMap()

    var advertisedServices: List<BBUUID> = emptyList()

    var isConnectable: Boolean = false

    val manufacturerData: ByteArray?
        get() = advertisementData[BBConstants.Advertisement.MANUFACTURER]

    val manufacturerId: Int?
        get() {
            val manufacturerData = manufacturerData ?: return null
            return (manufacturerData[1].toUByte().toInt() shl 8) or (manufacturerData[0].toUByte().toInt())
        }

    val manufacturerName: String?
        get() {
            val manufacturerId = manufacturerId ?: return null
            return BBConstants.Manufacturer.knownIds[manufacturerId]
        }

    // endregion

    // region Services

    private val _services = MutableStateFlow<List<BBService>>(emptyList())
    val services: StateFlow<List<BBService>> get() = _services

    // endregion

    // region Connection status

    private val _connectionStatus = MutableStateFlow(BBDeviceConnectionStatus.disconnected)
    val connectionStatus: StateFlow<BBDeviceConnectionStatus> get() = _connectionStatus

    // endregion

    // region MTU

    private val _mtu = MutableStateFlow(BBConstants.DEFAULT_MTU)
    val mtu: StateFlow<Int> get() = _mtu

    // endregion

    // region Operations

    suspend fun connect() {
        return operationEnqueue(
            BBOperationConnect(this)
        )
    }

    suspend fun disconnect() {
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

    private val operationQueue = LinkedBlockingQueue<BBOperation<*>>()
    private var operationCurrent: BBOperation<*>? = null

    override suspend fun <T> operationEnqueue(operation: BBOperation<T>): T =
        suspendCoroutine { continuation ->
            operation.continuation = continuation

            operationQueue.add(operation)
            operationCheck()
        }

    private fun operationCheck() {
        if (operationCurrent?.isComplete == false) {
            return
        }

        operationCurrent = operationQueue.poll()
        operationCurrent?.let { operation ->
            operation.execute(
                context,
                device,
                gatt,
            )

            Timer().schedule((operation.timeout * 1000).toLong()) {
                if (!operation.isComplete) {
                    operation.cancel()
                }
            }
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

        services.value.forEach { service ->
            service.characteristics.forEach { characteristic ->
                characteristic.onConnectionStateChange(gatt, status, newState)
            }
        }

        operationCurrent?.onConnectionStateChange(gatt, status, newState)

        when (newState) {
            BluetoothGatt.STATE_CONNECTED -> {
                this@BBDevice.gatt = gatt
                _connectionStatus.value = BBDeviceConnectionStatus.connected
            }

            BluetoothGatt.STATE_DISCONNECTED -> {
                _connectionStatus.value = BBDeviceConnectionStatus.disconnected
                _mtu.value = BBConstants.DEFAULT_MTU
                _services.value = emptyList()
            }
        }

        operationCheck()
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt?,
        status: Int
    ) {
        gatt ?: return

        _services.value = gatt.services.map {
            BBService(
                uuid = BBUUID(uuid = it.uuid),
                characteristics = it.characteristics.map {
                    BBCharacteristic(
                        characteristic = it,
                        operationQueue = this,
                    )
                }
            )
        }

        operationCurrent?.onServicesDiscovered(gatt, status)
        operationCheck()
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt?,
        mtu: Int,
        status: Int
    ) {
        gatt ?: return

        operationCurrent?.onMtuChanged(gatt, mtu, status)
        operationCheck()
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: return
        descriptor ?: return

        characteristic(descriptor.characteristic.uuid)?.onDescriptorWrite(gatt, descriptor, status)

        operationCurrent?.onDescriptorWrite(gatt, descriptor, status)
        operationCheck()
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

        operationCurrent?.onCharacteristicRead(gatt, characteristic, status)
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        characteristic(characteristic.uuid)?.onCharacteristicRead(gatt, characteristic, value, status)

        operationCurrent?.onCharacteristicRead(gatt, characteristic, value, status)
        operationCheck()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        characteristic(characteristic.uuid)?.onCharacteristicWrite(gatt, characteristic, status)

        operationCurrent?.onCharacteristicWrite(gatt, characteristic, status)
        operationCheck()
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

        operationCurrent?.onCharacteristicChanged(gatt, characteristic)
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        characteristic(characteristic.uuid)?.onCharacteristicChanged(gatt, characteristic, value)

        operationCurrent?.onCharacteristicChanged(gatt, characteristic, value)
        operationCheck()
    }

    // endregion
}

fun BBDevice.characteristic(uuid: UUID): BBCharacteristic? {
    services.value.forEach { service ->
        service.characteristics.forEach { characteristic ->
            if (characteristic.uuid.equals(uuid)) {
                return characteristic
            }
        }
    }

    return null
}

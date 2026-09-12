//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.flows.MutableSharedStateFlow
import dev.likemagic.bluebreeze.operations.BBOperationQueue
import dev.likemagic.bluebreeze.operations.BBOperationRead
import dev.likemagic.bluebreeze.operations.BBOperationSubscribe
import dev.likemagic.bluebreeze.operations.BBOperationUnsubscribe
import dev.likemagic.bluebreeze.operations.BBOperationWrite
import kotlinx.coroutines.flow.StateFlow

/**
 * A GATT characteristic belonging to one of a [BBDevice]'s discovered [BBService]s. You never
 * construct one yourself -- you get one from a [BBService]'s `characteristics` list. Read/write/
 * subscribe calls are queued onto the owning device's operation queue, so they're serialized
 * against every other operation on that device -- you don't need to serialize calls yourself.
 */
class BBCharacteristic internal constructor(
    internal val characteristic: BluetoothGattCharacteristic,
    internal val operationQueue: BBOperationQueue,
): BluetoothGattCallback() {
    // region Computed properties

    val uuid: BBUUID
        get() = BBUUID(uuid = characteristic.uuid)

    /** The characteristic's human-readable name, if [uuid] is a Bluetooth SIG-assigned characteristic UUID. */
    val name: String?
        get() = BBAssignedNumbers.Characteristic.knownUUIDs[uuid]

    /** The operations ([read], [write], [subscribe]) this characteristic supports, decoded from its native GATT properties bitmask. */
    val properties: Set<BBCharacteristicProperty>
        get() {
            val result = mutableSetOf<BBCharacteristicProperty>()
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                result.add(BBCharacteristicProperty.read)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                result.add(BBCharacteristicProperty.writeWithResponse)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                result.add(BBCharacteristicProperty.writeWithoutResponse)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                result.add(BBCharacteristicProperty.notify)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                result.add(BBCharacteristicProperty.notify)
            }
            return result
        }

    // endregion

    // region Observable properties

    /** The characteristic's most recently read or notified value. Empty until [read] or [subscribe] has been called and returned/notified at least once. */
    private val _data = MutableSharedStateFlow(byteArrayOf())
    val data: StateFlow<ByteArray> get() = _data

    /** Whether [subscribe] has been called without a matching [unsubscribe] since. */
    private val _isNotifying = MutableSharedStateFlow(false)
    val isNotifying: StateFlow<Boolean> get() = _isNotifying

    // endregion

    // region Operations

    /**
     * Reads the characteristic's current value, updating [data].
     *
     * @throws BBError if the read fails or times out. Requires [BBCharacteristicProperty.read].
     */
    suspend fun read(): ByteArray {
        return operationQueue.operationEnqueue(
            BBOperationRead(characteristic)
        )
    }

    /**
     * Writes [data] to the characteristic.
     *
     * @param withResponse whether to wait for the peripheral's acknowledgement
     * ([BBCharacteristicProperty.writeWithResponse]) or fire-and-forget
     * ([BBCharacteristicProperty.writeWithoutResponse]).
     * @throws BBError if the write fails or times out.
     */
    suspend fun write(data: ByteArray, withResponse: Boolean) {
        return operationQueue.operationEnqueue(
            BBOperationWrite(characteristic, data, withResponse)
        )
    }

    /**
     * Enables notifications/indications for this characteristic, updating [isNotifying] and
     * then [data] as values arrive. Requires [BBCharacteristicProperty.notify].
     *
     * @throws BBError if enabling notifications fails or times out.
     */
    suspend fun subscribe() {
        operationQueue.operationEnqueue(
            BBOperationSubscribe(characteristic)
        )

        _isNotifying.emit(true)
    }

    /**
     * Disables notifications/indications previously enabled with [subscribe].
     *
     * @throws BBError if disabling notifications fails or times out.
     */
    suspend fun unsubscribe() {
        operationQueue.operationEnqueue(
            BBOperationUnsubscribe(characteristic)
        )

        _isNotifying.emit(false)
    }

    // endregion

    // region Bluetooth GATT callback

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return
        if (status != BluetoothGatt.GATT_SUCCESS) return

        _data.emit(characteristic.value ?: byteArrayOf())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) return

        _data.emit(value)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return

        _data.emit(characteristic.value ?: byteArrayOf())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        _data.emit(value)
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            _data.emit(byteArrayOf())
            _isNotifying.emit(false)
        }
    }

    // endregion
}

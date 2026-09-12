//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import dev.likemagic.bluebreeze.BBError

/**
 * Reads a characteristic's value, backing [dev.likemagic.bluebreeze.BBCharacteristic.read].
 *
 * Implements both the pre-API-33 callback (deprecated, reads the value back off the mutable
 * [BluetoothGattCharacteristic]) and the API 33+ callback (which passes the value directly) --
 * exactly one of the two fires on a given device, depending on OS version.
 */
internal class BBOperationRead(
    private val characteristic: BluetoothGattCharacteristic
) : BBOperation<ByteArray>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        if (!gatt.readCharacteristic(characteristic)) {
            setError(BBError.gattError())
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (characteristic !== this.characteristic) return

        if (status == BluetoothGatt.GATT_SUCCESS) {
            val value = characteristic.value
            setSuccess(value ?: byteArrayOf())
        } else {
            setError(BBError.gattError(status))
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (characteristic !== this.characteristic) return

        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(value)
        } else {
            setError(BBError.gattError(status))
        }
    }
}

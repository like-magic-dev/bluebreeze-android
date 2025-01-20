//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import dev.likemagic.bluebreeze.BBConstants
import dev.likemagic.bluebreeze.BBOperation

class BBOperationUnsubscribe(
    private val characteristic: BluetoothGattCharacteristic
) : BBOperation<Unit>() {
    private val writeValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        val descriptor = characteristic.getDescriptor(BBConstants.UUID.cccd) ?: run {
            setError(BBError.gattError())
            return
        }

        if (!gatt.setCharacteristicNotification(characteristic, false)) {
            setError(BBError.gattError())
            return
        }

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            descriptor.value = writeValue
            if (!gatt.writeDescriptor(descriptor)) {
                setError(BBError.gattError())
            }
        } else {
            if (gatt.writeDescriptor(descriptor, writeValue) != BluetoothStatusCodes.SUCCESS) {
                setError(BBError.gattError())
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        descriptor ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        if (descriptor.uuid != BBConstants.UUID.cccd) {
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(Unit)
        } else {
            setError(BBError.gattError(status))
        }
    }
}

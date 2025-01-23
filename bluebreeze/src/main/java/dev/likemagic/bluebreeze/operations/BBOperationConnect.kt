//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import dev.likemagic.bluebreeze.BBError
import dev.likemagic.bluebreeze.BBOperation

class BBOperationConnect(
    private val gattCallback: BluetoothGattCallback
) : BBOperation<Unit>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        device.connectGatt(context, false, gattCallback)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> setSuccess(Unit)
                BluetoothGatt.STATE_DISCONNECTED -> setError(BBError.gattDisconnected())
            }
        } else {
            setError(BBError.gattError(status))
        }
    }
}

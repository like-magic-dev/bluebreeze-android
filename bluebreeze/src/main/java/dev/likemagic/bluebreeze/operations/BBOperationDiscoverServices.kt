//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.BBError

/** Discovers a peripheral's services and characteristics, backing [dev.likemagic.bluebreeze.BBDevice.discoverServices]. */
internal class BBOperationDiscoverServices : BBOperation<Unit>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        if (!gatt.discoverServices()) {
            setError(BBError.gattError())
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(Unit)
        } else {
            setError(BBError.gattError(status))
        }
    }
}

//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.BBError

/** Disconnects from a peripheral, backing [dev.likemagic.bluebreeze.BBDevice.disconnect]. */
internal class BBOperationDisconnect(
    private val operationQueue: BBOperationQueue,
) : BBOperation<Unit>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            // If there's no GATT to disconnect (already disconnected), resolve immediately with success
            setSuccess(Unit)
            return
        }

        gatt.disconnect()
    }

    override fun cancel() {
        // Force close the GATT if the native client never confirmed the disconnect (peripheral unresponsive)
        operationQueue.closeGatt()
        super.cancel()
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            when (newState) {
                BluetoothGatt.STATE_DISCONNECTED -> setSuccess(Unit)
            }
        } else {
            setError(BBError.gattError(status))
        }
    }
}

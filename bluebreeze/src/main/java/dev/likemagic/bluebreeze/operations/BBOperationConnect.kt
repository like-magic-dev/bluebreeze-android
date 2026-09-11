//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.os.Build
import dev.likemagic.bluebreeze.BBError

/**
 * Opens (or confirms) a GATT connection to a peripheral, backing [dev.likemagic.bluebreeze.BBDevice.connect].
 *
 * If a `gatt` is already passed into [execute] (the device is already connected), resolves
 * immediately without calling `connectGatt` again -- CoreBluetooth-equivalent platforms don't
 * re-invoke a connection callback for an already-connected peripheral, so retrying here would
 * just time out. Otherwise opens a new [BluetoothGatt] client and owns it until this operation
 * resolves: [cancel] and every error path close it, so a timed-out, superseded, or failed
 * attempt never leaks a native GATT client slot (Android caps these per process).
 */
internal class BBOperationConnect(
    private val gattCallback: BluetoothGattCallback
) : BBOperation<Unit>() {
    private var gatt: BluetoothGatt? = null

    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        if (gatt != null) {
            // Already connected, nothing to do
            setSuccess(Unit)
            return
        }

        this.gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    override fun cancel() {
        // Do not leak the native GATT client this attempt opened.
        closeGatt()
        super.cancel()
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> setSuccess(Unit)
                BluetoothGatt.STATE_DISCONNECTED -> {
                    closeGatt()
                    setError(BBError.gattDisconnected())
                }
            }
        } else {
            closeGatt()
            setError(BBError.gattError(status))
        }
    }

    private fun closeGatt() {
        // Try to close an open GATT object to avoid leaking it
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT was revoked; nothing more we can do
        }
        gatt = null
    }
}

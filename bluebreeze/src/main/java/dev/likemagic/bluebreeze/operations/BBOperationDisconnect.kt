package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.BBOperation

class BBOperationDisconnect : BBOperation<Unit>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        gatt.disconnect()
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
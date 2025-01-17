package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.BBOperation

class BBOperationRequestMtu(private val mtu: Int) : BBOperation<Int>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        if (!gatt.requestMtu(mtu)) {
            setError(BBError.gattError(-1))
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(mtu)
        } else {
            setError(BBError.gattError(status))
        }
    }
}
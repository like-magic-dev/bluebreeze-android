package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import dev.likemagic.bluebreeze.BBOperation

class BBOperationRead(
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

        gatt.readCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val value = characteristic?.value
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
        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(value)
        } else {
            setError(BBError.gattError(status))
        }
    }
}
package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Build
import dev.likemagic.bluebreeze.BBOperation

class BBOperationWrite(
    private val characteristic: BluetoothGattCharacteristic,
    private val data: ByteArray,
    private val withResponse: Boolean
) : BBOperation<Unit>() {
    override fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    ) {
        gatt ?: run {
            setError(BBError.gattDisconnected())
            return
        }

        val writeType = if (withResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                data,
                writeType
            )
        } else {
            characteristic.value = data
            characteristic.writeType = writeType
            gatt.writeCharacteristic(characteristic)
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(Unit)
        } else {
            setError(BBError.gattError(status))
        }
    }
}
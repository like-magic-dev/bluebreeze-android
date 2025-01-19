package dev.likemagic.bluebreeze.operations

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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

        val value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (gatt.writeDescriptor(descriptor, value) != BluetoothStatusCodes.SUCCESS) {
                setError(BBError.gattError())
            }
        } else {
            descriptor.value = value
            if (!gatt.writeDescriptor(descriptor)) {
                setError(BBError.gattError())
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(Unit)
        } else {
            setError(BBError.gattError(status))
        }
    }
}
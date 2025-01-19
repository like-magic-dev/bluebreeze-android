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

class BBOperationSubscribe(
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

        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            setError(BBError.gattError())
            return
        }

        val value = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

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
        if (descriptor?.uuid != BBConstants.UUID.cccd) {
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            setSuccess(Unit)
        } else {
            setError(BBError.gattError(status))
        }
    }
}
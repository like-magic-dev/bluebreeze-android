package dev.likemagic.bluebreeze

import BBUUID
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.operations.BBOperationRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BBCharacteristic(
    val characteristic: BluetoothGattCharacteristic,
    val operationQueue: BBOperationQueue,
): BluetoothGattCallback() {
    // region Computed properties

    val uuid: BBUUID
        get() = characteristic.uuid

    val properties: Set<BBCharacteristicProperty>
        get() {
            val result = mutableSetOf<BBCharacteristicProperty>()
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                result.add(BBCharacteristicProperty.read)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                result.add(BBCharacteristicProperty.writeWithResponse)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                result.add(BBCharacteristicProperty.writeWithoutResponse)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                result.add(BBCharacteristicProperty.notify)
            }
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                result.add(BBCharacteristicProperty.notify)
            }
            return result
        }

    // endregion

    // region Data

    private val _data = MutableStateFlow<ByteArray>(byteArrayOf())
    val data: StateFlow<ByteArray> get() = _data

    // endregion

    // region Operations

    suspend fun read(): ByteArray {
        return operationQueue.operationEnqueue(
            BBOperationRead(characteristic)
        )
    }

    // endregion

    // region Bluetooth GATT callback

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        gatt ?: return

        when (newState) {
            BluetoothGatt.STATE_DISCONNECTED -> {
                _data.value = byteArrayOf()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        _data.value = characteristic.value
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        _data.value = value
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        _data.value = characteristic.value
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return

        _data.value = characteristic.value
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        _data.value = value
    }

    // endregion
}

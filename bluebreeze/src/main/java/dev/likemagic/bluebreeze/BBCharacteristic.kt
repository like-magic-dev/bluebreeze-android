package dev.likemagic.bluebreeze

import BBUUID
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.operations.BBOperationRead
import dev.likemagic.bluebreeze.operations.BBOperationSubscribe
import dev.likemagic.bluebreeze.operations.BBOperationUnsubscribe
import dev.likemagic.bluebreeze.operations.BBOperationWrite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BBCharacteristic(
    val characteristic: BluetoothGattCharacteristic,
    val operationQueue: BBOperationQueue,
): BluetoothGattCallback() {
    // region Computed properties

    val uuid: BBUUID
        get() = BBUUID(uuid = characteristic.uuid)

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

    // region Observable properties

    private val _data = MutableStateFlow(byteArrayOf())
    val data: StateFlow<ByteArray> get() = _data

    private val _isNotifying = MutableStateFlow<Boolean>(false)
    val isNotifying: StateFlow<Boolean> get() = _isNotifying

    // endregion

    // region Operations

    suspend fun read(): ByteArray {
        return operationQueue.operationEnqueue(
            BBOperationRead(characteristic)
        )
    }

    suspend fun write(data: ByteArray, withResponse: Boolean) {
        return operationQueue.operationEnqueue(
            BBOperationWrite(characteristic, data, withResponse)
        )
    }

    suspend fun subscribe() {
        operationQueue.operationEnqueue(
            BBOperationSubscribe(characteristic)
        )

        _isNotifying.value = true
    }

    suspend fun unsubscribe() {
        operationQueue.operationEnqueue(
            BBOperationUnsubscribe(characteristic)
        )

        _isNotifying.value = false
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

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        _data.value = characteristic.value ?: byteArrayOf()
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
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return

        _data.value = characteristic.value ?: byteArrayOf()
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

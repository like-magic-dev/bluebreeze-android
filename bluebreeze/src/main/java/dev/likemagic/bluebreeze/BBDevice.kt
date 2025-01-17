package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue

class BBDevice(
    val device: BluetoothDevice,
): BluetoothGattCallback() {
    var gatt: BluetoothGatt? = null

    // region Dynamic properties

    var rssi: Int = 0
    var advertiseData: Map<UByte, ByteArray> = emptyMap()

    // endregion

    // region Computed public properties

    val address: String
        get() = device.address

    val name: String?
        get() = device.name ?: advertiseData[0x09u]?.toString(Charset.defaultCharset())

    // endregion

    // region Connection status

    private val _connectionStatus = MutableStateFlow(BBDeviceConnectionStatus.disconnected)
    val connectionStatus: StateFlow<BBDeviceConnectionStatus> get() = _connectionStatus

    // region MTU

    private val _mtu = MutableStateFlow(23)
    val mtu: StateFlow<Int> get() = _mtu

    // endregion

    // region Operation queue

    private val operationQueue = LinkedBlockingQueue<BBOperation<*>>()
    private var operationCurrent: BBOperation<*>? = null

    fun <T> operationEnqueue(operation: BBOperation<T>): Flow<Result<T>> {
        return callbackFlow {
            operation.sendChannel = channel

            operationQueue.add(operation)
            operationCheck()

            awaitClose { channel.close() }
        }
    }

    private fun operationCheck() {
        if (operationCurrent?.isComplete == false) {
            return
        }

        operationCurrent = operationQueue.poll()
        operationCurrent?.execute(device)
    }

    // endregion

    // region Bluetooth GATT callback

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        gatt ?: return

        operationCurrent?.onConnectionStateChange(gatt, status, newState)

        when (newState) {
            BluetoothGatt.STATE_CONNECTED -> {
                this@BBDevice.gatt = gatt
                _connectionStatus.value = BBDeviceConnectionStatus.connected
            }

            BluetoothGatt.STATE_DISCONNECTED -> {
                _connectionStatus.value = BBDeviceConnectionStatus.disconnected
            }
        }

        operationCheck()
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt?,
        status: Int
    ) {
        gatt ?: return

        operationCurrent?.onServicesDiscovered(gatt, status)
        operationCheck()
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt?,
        mtu: Int,
        status: Int
    ) {
        gatt ?: return

        operationCurrent?.onMtuChanged(gatt, mtu, status)
        operationCheck()
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        gatt ?: return
        descriptor ?: return

        operationCurrent?.onDescriptorWrite(gatt, descriptor, status)
        operationCheck()
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        operationCurrent?.onCharacteristicRead(gatt, characteristic, status)
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        operationCurrent?.onCharacteristicRead(gatt, characteristic, value, status)
        operationCheck()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        gatt ?: return
        characteristic ?: return

        operationCurrent?.onCharacteristicWrite(gatt, characteristic, status)
        operationCheck()
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        gatt ?: return
        characteristic ?: return

//            listener?.bleControllerDeviceDidUpdateValue(gatt.device, characteristic, characteristic.value)

        operationCurrent?.onCharacteristicChanged(gatt, characteristic)
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
//            listener?.bleControllerDeviceDidUpdateValue(gatt.device, characteristic, value)

        operationCurrent?.onCharacteristicChanged(gatt, characteristic, value)
        operationCheck()
    }

    // endregion
}

package dev.likemagic.bluebreeze

import BBUUID
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.operations.BBOperationDiscoverServices
import dev.likemagic.bluebreeze.operations.BBOperationRequestMtu
import dev.likemagic.bluebreeze.operations.BBOperationConnect
import dev.likemagic.bluebreeze.operations.BBOperationDisconnect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.Charset
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.suspendCoroutine

class BBDevice(
    val context: Context,
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

    // region Services

    private val _services = MutableStateFlow<List<BBUUID>>(emptyList())
    val services: StateFlow<List<BBUUID>> get() = _services

    // endregion

    // region Connection status

    private val _connectionStatus = MutableStateFlow(BBDeviceConnectionStatus.disconnected)
    val connectionStatus: StateFlow<BBDeviceConnectionStatus> get() = _connectionStatus

    // endregion

    // region MTU

    private val _mtu = MutableStateFlow(23)
    val mtu: StateFlow<Int> get() = _mtu

    // endregion

    // region Operations

    suspend fun connect() {
        return operationEnqueue(
            BBOperationConnect(this)
        )
    }

    suspend fun disconnect() {
        return operationEnqueue(
            BBOperationDisconnect()
        )
    }

    suspend fun discoverServices() {
        return operationEnqueue(
            BBOperationDiscoverServices()
        )
    }

    suspend fun requestMTU(mtu: Int): Int {
        return operationEnqueue(
            BBOperationRequestMtu(mtu)
        )
    }

    // endregion

    // region Operation queue

    private val operationQueue = LinkedBlockingQueue<BBOperation<*>>()
    private var operationCurrent: BBOperation<*>? = null

    suspend fun <T> operationEnqueue(operation: BBOperation<T>): T =
        suspendCoroutine { continuation ->
            operation.continuation = continuation

            operationQueue.add(operation)
            operationCheck()
        }

    private fun operationCheck() {
        if (operationCurrent?.isComplete == false) {
            return
        }

        operationCurrent = operationQueue.poll()
        operationCurrent?.execute(
            context,
            device,
            gatt,
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

        _services.value = gatt.services.map { it.uuid }

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

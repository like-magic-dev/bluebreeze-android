package dev.likemagic.bluebreeze

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.channels.SendChannel
import java.util.UUID

abstract class BBOperation<T> : BluetoothGattCallback() {
    // region Execution

    abstract fun execute(device: BluetoothDevice)

    // endregion

    // region Completion

    var sendChannel: SendChannel<Result<T>>? = null
    var isComplete = false

    fun setSuccess(value: T) {
        sendChannel?.trySend(Result.success(value))
        isComplete = true
    }

    fun setError(error: Throwable) {
        sendChannel?.trySend(Result.failure(error))
        isComplete = true
    }

    fun cancel() {
        sendChannel?.trySend(Result.failure(BBError.operationCancelled()))
        isComplete = true
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
                cancel()
            }
        }
    }

    // endregion
}

fun BluetoothGatt.getCharacteristic(uuid: UUID?): BluetoothGattCharacteristic? {
    services.forEach { service ->
        service.getCharacteristic(uuid)?.let { characteristic ->
            return characteristic
        }
    }

    return null
}
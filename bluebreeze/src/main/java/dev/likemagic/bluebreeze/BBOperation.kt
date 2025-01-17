package dev.likemagic.bluebreeze

import BBError
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import java.util.UUID
import kotlin.coroutines.Continuation

abstract class BBOperation<T> : BluetoothGattCallback() {
    // region Execution

    abstract fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    )

    // endregion

    // region Completion

    var continuation: Continuation<T>? = null
    var isComplete = false

    fun setSuccess(value: T) {
        continuation?.resumeWith(Result.success(value))
        isComplete = true
    }

    fun setError(error: Throwable) {
        continuation?.resumeWith(Result.failure(error))
        isComplete = true
    }

    fun cancel() {
        continuation?.resumeWith(Result.failure(BBError.operationCancelled()))
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
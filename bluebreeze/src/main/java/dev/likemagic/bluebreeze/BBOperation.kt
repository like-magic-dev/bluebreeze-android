//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
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
        try {
            continuation?.resumeWith(Result.success(value))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    fun setError(error: Throwable) {
        try {
            continuation?.resumeWith(Result.failure(error))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    fun cancel() {
        try {
            continuation?.resumeWith(Result.failure(BBError.operationCancelled()))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    // endregion

    // region Timeout

    val timeout: Float
        get() = 5.0f

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

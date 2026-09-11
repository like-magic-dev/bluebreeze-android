//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import dev.likemagic.bluebreeze.BBError
import kotlin.coroutines.Continuation

/**
 * A single queued GATT action -- one attempt at `connect`, `disconnect`, a characteristic read,
 * and so on. Queued and run one at a time by [dev.likemagic.bluebreeze.BBDevice], each with a [timeout]; see the concrete
 * subclasses under `operations/` for what each one actually does.
 *
 * A subclass implements [execute] to perform its native GATT call, then resolves itself by
 * calling [setSuccess] or [setError] from whichever [BluetoothGattCallback] override the
 * resulting native callback arrives on. [dev.likemagic.bluebreeze.BBDevice] calls [cancel] on its behalf if it times out
 * or the connection drops first.
 */
internal abstract class BBOperation<T> : BluetoothGattCallback() {
    // region Execution

    /**
     * Performs this operation's native GATT call. Runs on whatever thread called
     * [BBOperationQueue.operationEnqueue] (or, for a subsequently queued operation, the thread
     * that completed the previous one) -- not necessarily the calling coroutine's dispatcher.
     * Must eventually call [setSuccess] or [setError], either synchronously (if the result is
     * already known) or from a later GATT callback.
     */
    abstract fun execute(
        context: Context,
        device: BluetoothDevice,
        gatt: BluetoothGatt?,
    )

    // endregion

    // region Completion

    /** The suspended caller's continuation, resumed exactly once by [setSuccess], [setError], or [cancel]. */
    var continuation: Continuation<T>? = null

    // Read from the operation-queue thread, the GATT callback threads and the timeout Timer
    // thread, sometimes outside operationLock (e.g. right after execute()), so it must be
    // volatile to avoid a stale read that double-cancels or re-arms a timeout.
    /** Whether this operation has resolved (successfully, with an error, or cancelled). Once `true`, this operation is done and won't be touched again. */
    @Volatile
    var isComplete = false

    /** Resolves this operation successfully with [value]. Safe to call after the operation has already completed (e.g. a late/duplicate callback) -- it's a no-op in that case. */
    fun setSuccess(value: T) {
        try {
            continuation?.resumeWith(Result.success(value))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    /** Resolves this operation with [error]. Safe to call after the operation has already completed. */
    fun setError(error: Throwable) {
        try {
            continuation?.resumeWith(Result.failure(error))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    /**
     * Resolves this operation with [dev.likemagic.bluebreeze.BBError.Companion.operationCancelled] -- called by [dev.likemagic.bluebreeze.BBDevice] when a
     * timeout fires, a disconnect races an in-flight operation, or the operation is still queued
     * when the device is torn down. A subclass that opened a native resource in [execute]
     * (e.g. `BBOperationConnect`'s `BluetoothGatt`) should override this to release it, since a
     * cancelled operation otherwise never gets a chance to clean up.
     */
    open fun cancel() {
        try {
            continuation?.resumeWith(Result.failure(BBError.operationCancelled()))
        } catch (e: IllegalStateException) {
            // This can happen if an operation has already completed
        }

        isComplete = true
    }

    // endregion

    // region Timeout

    /** Seconds [dev.likemagic.bluebreeze.BBDevice] waits for this operation to complete before calling [cancel]. Fixed at 5 seconds for every operation. */
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

//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dev.likemagic.bluebreeze.BBConstants
import java.util.Timer
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.schedule
import kotlin.coroutines.suspendCoroutine

class BBOperationQueue(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    @Volatile
    var gatt: BluetoothGatt? = null
        private set

    private val operationLock = Any()
    private val operationQueue = LinkedBlockingQueue<BBOperation<*>>()
    private var operationCurrent: BBOperation<*>? = null

    // A shared Timer used to schedule every operation's timeout
    private val operationTimer = Timer()

    // operationCurrent/operationQueue are touched from several threads, so every access must go through this lock
    private fun <R> withOperationLock(block: () -> R): R = synchronized(operationLock, block)

    suspend fun <T> operationEnqueue(operation: BBOperation<T>): T =
        suspendCoroutine { continuation ->
            operation.continuation = continuation

            withOperationLock {
                operationQueue.add(operation)
            }
            operationCheck()
        }

    private fun operationCheck() {
        val operation = withOperationLock {
            if (operationCurrent?.isComplete == false) {
                return@withOperationLock null
            }
            operationQueue.poll().also { operationCurrent = it }
        } ?: return

        operation.execute(context, device, gatt)

        // execute() may have completed the operation synchronously
        if (operation.isComplete) {
            operationCheck()
            return
        }

        operationTimer.schedule((operation.timeout * 1000).toLong()) {
            try {
                if (!operation.isComplete) {
                    operation.cancel()
                    operationCheck()
                }
            } catch (e: Throwable) {
                // Swallow all exceptions so the timer keeps running.
                // The timer runs every scheduled task on a single background thread.
                // If an exception escaped this task it would kill the timer thread.
                Log.w(BBConstants.LOG_TAG, "Operation timeout handler failed", e)
            }
        }
    }

    fun cancelAll() {
        withOperationLock {
            operationCurrent?.cancel()
            operationQueue.forEach { it.cancel() }
            operationQueue.clear()
        }
    }

    fun reset() {
        gatt?.close()
        gatt = null

        withOperationLock {
            operationCurrent?.cancel()
            operationCurrent = null

            operationQueue.forEach { it.cancel() }
            operationQueue.clear()
        }
        operationCheck()
    }

    // region Bluetooth GATT callback forwarding

    // BBDevice forwards its GATT callbacks into the methods below, which route them to the
    // current operation and then check whether the next queued operation can start.

    fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            this.gatt = gatt
        }

        withOperationLock {
            operationCurrent?.onConnectionStateChange(gatt, status, newState)
        }
        operationCheck()
    }

    fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        withOperationLock {
            operationCurrent?.onServicesDiscovered(gatt, status)
        }
        operationCheck()
    }

    fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        withOperationLock {
            operationCurrent?.onMtuChanged(gatt, mtu, status)
        }
        operationCheck()
    }

    @Suppress("DEPRECATION")
    fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        withOperationLock {
            operationCurrent?.onDescriptorRead(gatt, descriptor, status)
        }
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
        withOperationLock {
            operationCurrent?.onDescriptorRead(gatt, descriptor, status, value)
        }
        operationCheck()
    }

    fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        withOperationLock {
            operationCurrent?.onDescriptorWrite(gatt, descriptor, status)
        }
        operationCheck()
    }

    @Suppress("DEPRECATION")
    fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        withOperationLock {
            operationCurrent?.onCharacteristicRead(gatt, characteristic, status)
        }
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        withOperationLock {
            operationCurrent?.onCharacteristicRead(gatt, characteristic, value, status)
        }
        operationCheck()
    }

    fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        withOperationLock {
            operationCurrent?.onCharacteristicWrite(gatt, characteristic, status)
        }
        operationCheck()
    }

    @Suppress("DEPRECATION")
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        withOperationLock {
            operationCurrent?.onCharacteristicChanged(gatt, characteristic)
        }
        operationCheck()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        withOperationLock {
            operationCurrent?.onCharacteristicChanged(gatt, characteristic, value)
        }
        operationCheck()
    }

    // endregion
}

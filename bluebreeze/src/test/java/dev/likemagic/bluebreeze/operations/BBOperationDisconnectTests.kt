//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class BBOperationDisconnectTests {
    private val context = mock<Context>()
    private val device = mock<BluetoothDevice>()
    private val operationQueue = mock<BBOperationQueue>()

    @Test
    fun `execute disconnects the GATT client when connected`() {
        val operation = BBOperationDisconnect(operationQueue)
        val gatt = mock<BluetoothGatt>()

        operation.execute(context, device, gatt)

        verify(gatt).disconnect()
        assertTrue(!operation.isComplete)
    }

    @Test
    fun `execute resolves immediately without an error when already disconnected`() {
        val operation = BBOperationDisconnect(operationQueue)

        operation.execute(context, device, null)

        assertTrue(operation.isComplete)
        verifyNoInteractions(device)
    }

    @Test
    fun `onConnectionStateChange succeeds on STATE_DISCONNECTED`() {
        val operation = BBOperationDisconnect(operationQueue)

        operation.onConnectionStateChange(mock(), BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue(operation.isComplete)
    }

    @Test
    fun `onConnectionStateChange fails on a GATT error status`() {
        val operation = BBOperationDisconnect(operationQueue)

        operation.onConnectionStateChange(mock(), /* status = */ 133, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue(operation.isComplete)
    }

    @Test
    fun `cancel force-closes the shared GATT client so a wedged peripheral can't leak it`() {
        val operation = BBOperationDisconnect(operationQueue)
        val gatt = mock<BluetoothGatt>()
        operation.execute(context, device, gatt)

        operation.cancel()

        verify(operationQueue).closeGatt()
        assertTrue(operation.isComplete)
    }
}

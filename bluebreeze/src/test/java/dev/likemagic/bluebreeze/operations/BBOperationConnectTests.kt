//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

// Build.VERSION.SDK_INT reads as 0 in a plain JVM unit test (no Robolectric), so execute()
// always takes its <API-23 branch and calls the 3-argument connectGatt overload below.
class BBOperationConnectTests {
    private val context = mock<Context>()
    private val device = mock<BluetoothDevice>()
    private val gattCallback = mock<BluetoothGattCallback>()

    @Test
    fun `execute resolves immediately without reconnecting when already connected`() {
        val operation = BBOperationConnect(gattCallback)
        val existingGatt = mock<BluetoothGatt>()

        operation.execute(context, device, existingGatt)

        assertTrue(operation.isComplete)
        verifyNoInteractions(device)
    }

    @Test
    fun `execute opens a new GATT connection when not already connected`() {
        val operation = BBOperationConnect(gattCallback)
        val newGatt = mock<BluetoothGatt>()
        whenever(device.connectGatt(context, false, gattCallback)).thenReturn(newGatt)

        operation.execute(context, device, null)

        assertTrue(!operation.isComplete)
        verify(device).connectGatt(context, false, gattCallback)
    }

    @Test
    fun `onConnectionStateChange succeeds on STATE_CONNECTED`() {
        val operation = BBOperationConnect(gattCallback)

        operation.onConnectionStateChange(mock(), BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        assertTrue(operation.isComplete)
    }

    @Test
    fun `onConnectionStateChange closes the GATT client on an unexpected disconnect`() {
        val operation = BBOperationConnect(gattCallback)
        val newGatt = mock<BluetoothGatt>()
        whenever(device.connectGatt(context, false, gattCallback)).thenReturn(newGatt)
        operation.execute(context, device, null)

        operation.onConnectionStateChange(newGatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue(operation.isComplete)
        verify(newGatt).close()
    }

    @Test
    fun `onConnectionStateChange closes the GATT client on a GATT error status`() {
        val operation = BBOperationConnect(gattCallback)
        val newGatt = mock<BluetoothGatt>()
        whenever(device.connectGatt(context, false, gattCallback)).thenReturn(newGatt)
        operation.execute(context, device, null)

        operation.onConnectionStateChange(newGatt, /* status = */ 133, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue(operation.isComplete)
        verify(newGatt).close()
    }

    @Test
    fun `cancel closes the GATT client opened by execute`() {
        val operation = BBOperationConnect(gattCallback)
        val newGatt = mock<BluetoothGatt>()
        whenever(device.connectGatt(context, false, gattCallback)).thenReturn(newGatt)
        operation.execute(context, device, null)

        operation.cancel()

        verify(newGatt).close()
        assertTrue(operation.isComplete)
    }

    @Test
    fun `cancel is a no-op if execute never opened a GATT client`() {
        val operation = BBOperationConnect(gattCallback)

        // Must not throw even though execute() (and therefore connectGatt) was never called.
        operation.cancel()

        assertTrue(operation.isComplete)
    }

    @Test
    fun `closeGatt swallows a SecurityException from a revoked BLUETOOTH_CONNECT`() {
        val operation = BBOperationConnect(gattCallback)
        val newGatt = mock<BluetoothGatt>()
        whenever(newGatt.close()).thenThrow(SecurityException())
        whenever(device.connectGatt(context, false, gattCallback)).thenReturn(newGatt)
        operation.execute(context, device, null)

        // Must not propagate the SecurityException.
        operation.cancel()

        assertTrue(operation.isComplete)
    }
}

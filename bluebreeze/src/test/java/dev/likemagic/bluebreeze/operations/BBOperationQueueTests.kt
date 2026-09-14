//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.operations

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

// An operation that never completes on its own, so tests can drive cancellation manually --
// mirrors how a real operation would sit waiting for a native callback or its timeout.
private class NeverCompletingOperation : BBOperation<Unit>() {
    var executeCalled = false
    override fun execute(context: Context, device: BluetoothDevice, gatt: BluetoothGatt?) {
        executeCalled = true
    }
}

class BBOperationQueueTests {
    private val context = mock<Context>()
    private val device = mock<BluetoothDevice>()
    private val queue = BBOperationQueue(context, device)

    @Test
    fun `onConnectionStateChange sets gatt on STATE_CONNECTED`() {
        val gatt = mock<BluetoothGatt>()

        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        assertEquals(gatt, queue.gatt)
    }

    @Test
    fun `onConnectionStateChange closes and clears gatt on a clean STATE_DISCONNECTED`() {
        val gatt = mock<BluetoothGatt>()
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)

        assertNull(queue.gatt)
        verify(gatt).close()
    }

    @Test
    fun `onConnectionStateChange closes and clears gatt on STATE_DISCONNECTED even with an error status`() {
        // A real connect/disconnect race can end with a non-success status; the client must be
        // released either way, so a subsequent connect() doesn't mistake it for still being live.
        val gatt = mock<BluetoothGatt>()
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        queue.onConnectionStateChange(gatt, /* status = */ 8, BluetoothGatt.STATE_DISCONNECTED)

        assertNull(queue.gatt)
        verify(gatt).close()
    }

    @Test
    fun `a disconnect confirmation resolves the queued disconnect operation with gatt already cleared`() = runTest {
        val gatt = mock<BluetoothGatt>()
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
        val disconnect = BBOperationDisconnect(queue)
        val result = async { runCatching { queue.operationEnqueue(disconnect) } }
        advanceUntilIdle()

        // The real native callback that confirms a clean disconnect.
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)
        advanceUntilIdle()

        // gatt must already be null by the time the operation resolves -- a connect() enqueued
        // right after must never see a stale, torn-down client (the bug this queue-level ordering fixes).
        assertNull(queue.gatt)
        assertTrue(result.await().isSuccess)
    }

    @Test
    fun `closeGatt is safe to call when gatt is already null`() {
        // Must not throw even though nothing has ever connected.
        queue.closeGatt()

        assertNull(queue.gatt)
    }

    @Test
    fun `cancelAll cancels the current and queued operations without touching gatt`() = runTest {
        val gatt = mock<BluetoothGatt>()
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        val current = NeverCompletingOperation()
        val queued = NeverCompletingOperation()
        val currentResult = async { runCatching { queue.operationEnqueue(current) } }
        advanceUntilIdle()
        val queuedResult = async { runCatching { queue.operationEnqueue(queued) } }
        advanceUntilIdle()
        assertTrue(current.executeCalled)
        assertTrue(!queued.executeCalled)

        queue.cancelAll()

        assertTrue(current.isComplete)
        assertTrue(queued.isComplete)
        assertTrue(currentResult.await().isFailure)
        assertTrue(queuedResult.await().isFailure)
        assertEquals(gatt, queue.gatt)
        verify(gatt, never()).close()
    }

    @Test
    fun `reset closes gatt and cancels the current and queued operations`() = runTest {
        val gatt = mock<BluetoothGatt>()
        queue.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        val current = NeverCompletingOperation()
        val queued = NeverCompletingOperation()
        val currentResult = async { runCatching { queue.operationEnqueue(current) } }
        advanceUntilIdle()
        val queuedResult = async { runCatching { queue.operationEnqueue(queued) } }
        advanceUntilIdle()

        queue.reset()

        assertNull(queue.gatt)
        verify(gatt).close()
        assertTrue(current.isComplete)
        assertTrue(queued.isComplete)
        assertTrue(currentResult.await().isFailure)
        assertTrue(queuedResult.await().isFailure)
    }
}

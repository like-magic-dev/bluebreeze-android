//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.operations.BBOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class BBDeviceTests {
    // onConnectionStateChange delivers its work via a coroutine scope on Dispatchers.Main;
    // route it through the same virtual-time scheduler runTest uses so advanceUntilIdle() drives it.
    private val testDispatcher = StandardTestDispatcher()

    private val context = mock<Context>()
    private val nativeDevice = mock<BluetoothDevice>()
    private lateinit var device: BBDevice

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        device = BBDevice(context, nativeDevice)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun recordingOperation(label: String, order: MutableList<String>) =
        object : BBOperation<Unit>() {
            override fun execute(context: Context, device: BluetoothDevice, gatt: BluetoothGatt?) {
                order.add(label)
                setSuccess(Unit)
            }
        }

    @Test
    fun `operations enqueued on a device run in enqueue order`() = runTest(testDispatcher) {
        val order = mutableListOf<String>()

        device.operationEnqueue(recordingOperation("first", order))
        device.operationEnqueue(recordingOperation("second", order))
        device.operationEnqueue(recordingOperation("third", order))

        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `an operation that completes synchronously does not block the next queued operation`() = runTest(testDispatcher) {
        val order = mutableListOf<String>()

        device.operationEnqueue(recordingOperation("op1", order))
        device.operationEnqueue(recordingOperation("op2", order))

        assertEquals(listOf("op1", "op2"), order)
    }

    @Test
    fun `connect retries on GATT_ERROR 133 up to 3 times then rethrows`() = runTest(testDispatcher) {
        val gatt = mock<BluetoothGatt>()
        whenever(nativeDevice.connectGatt(any(), any(), any())).thenReturn(gatt)

        val result = async { runCatching { device.connect() } }

        repeat(3) {
            advanceUntilIdle()
            device.onConnectionStateChange(gatt, /* status = */ 133, BluetoothGatt.STATE_DISCONNECTED)
            advanceUntilIdle()
        }

        val error = result.await().exceptionOrNull()
        assertTrue(error is BBErrorGatt)
        assertEquals(133, (error as BBErrorGatt).code)
        verify(nativeDevice, times(3)).connectGatt(any(), any(), any())
    }

    @Test
    fun `connect rethrows a non-133 GATT error immediately without retrying`() = runTest(testDispatcher) {
        val gatt = mock<BluetoothGatt>()
        whenever(nativeDevice.connectGatt(any(), any(), any())).thenReturn(gatt)

        val result = async { runCatching { device.connect() } }

        advanceUntilIdle()
        device.onConnectionStateChange(gatt, /* status = */ 8, BluetoothGatt.STATE_DISCONNECTED)
        advanceUntilIdle()

        val error = result.await().exceptionOrNull()
        assertTrue(error is BBErrorGatt)
        assertEquals(8, (error as BBErrorGatt).code)
        verify(nativeDevice, times(1)).connectGatt(any(), any(), any())
    }

    @Test
    fun `disconnect then connect again opens a real new GATT connection, not the already-connected fast path`() = runTest(testDispatcher) {
        val firstGatt = mock<BluetoothGatt>()
        whenever(nativeDevice.connectGatt(any(), any(), any())).thenReturn(firstGatt)

        val connectResult = async { device.connect() }
        advanceUntilIdle()
        device.onConnectionStateChange(firstGatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
        advanceUntilIdle()
        connectResult.await()

        val disconnectResult = async { device.disconnect() }
        advanceUntilIdle()
        verify(firstGatt).disconnect()
        device.onConnectionStateChange(firstGatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)
        advanceUntilIdle()
        disconnectResult.await()
        assertEquals(BBDeviceConnectionStatus.disconnected, device.connectionStatus.value)

        val secondGatt = mock<BluetoothGatt>()
        whenever(nativeDevice.connectGatt(any(), any(), any())).thenReturn(secondGatt)

        val secondConnectResult = async { device.connect() }
        advanceUntilIdle()
        device.onConnectionStateChange(secondGatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
        advanceUntilIdle()
        secondConnectResult.await()

        // A real second connectGatt() call -- not the "already connected" fast path a leaked,
        // stale gatt reference would otherwise trigger.
        verify(nativeDevice, times(2)).connectGatt(any(), any(), any())
        assertEquals(BBDeviceConnectionStatus.connected, device.connectionStatus.value)
    }

    @Test
    fun `disconnect succeeds immediately when the device is already disconnected`() = runTest(testDispatcher) {
        val result = async { runCatching { device.disconnect() } }
        advanceUntilIdle()

        assertTrue(result.await().isSuccess)
        verifyNoInteractions(nativeDevice)
    }

    @Test
    fun `onAdapterStateChanged forces disconnected even without a prior GATT callback`() = runTest(testDispatcher) {
        device.onAdapterStateChanged(BBState.poweredOff)

        assertEquals(BBDeviceConnectionStatus.disconnected, device.connectionStatus.value)
        assertEquals(BBConstants.DEFAULT_MTU, device.mtu.value)
        assertTrue(device.services.value.isEmpty())
    }

    @Test
    fun `onAdapterStateChanged does nothing when the adapter is powered on`() = runTest(testDispatcher) {
        // Should not throw or otherwise disturb a device that was never connected.
        device.onAdapterStateChanged(BBState.poweredOn)

        assertEquals(BBDeviceConnectionStatus.disconnected, device.connectionStatus.value)
    }
}

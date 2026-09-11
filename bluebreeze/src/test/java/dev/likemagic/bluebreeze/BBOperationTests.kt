//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import dev.likemagic.bluebreeze.operations.BBOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.coroutines.suspendCoroutine

private class TestOperation : BBOperation<String>() {
    // Tests drive completion directly via setSuccess/setError/cancel; execute() is never called.
    override fun execute(context: Context, device: BluetoothDevice, gatt: BluetoothGatt?) {}
}

class BBOperationTests {
    @Test
    fun `setSuccess resumes the continuation with the value`() = runTest {
        val operation = TestOperation()

        val result = suspendCoroutine<String> { continuation ->
            operation.continuation = continuation
            operation.setSuccess("done")
        }

        assertEquals("done", result)
        assertTrue(operation.isComplete)
    }

    @Test
    fun `setError resumes the continuation with the error`() = runTest {
        val operation = TestOperation()
        val error = BBError.gattDisconnected()

        val thrown = runCatching {
            suspendCoroutine<String> { continuation ->
                operation.continuation = continuation
                operation.setError(error)
            }
        }.exceptionOrNull()

        assertEquals(error, thrown)
        assertTrue(operation.isComplete)
    }

    @Test
    fun `cancel resumes the continuation with operationCancelled`() = runTest {
        val operation = TestOperation()

        val thrown = runCatching {
            suspendCoroutine<String> { continuation ->
                operation.continuation = continuation
                operation.cancel()
            }
        }.exceptionOrNull()

        assertTrue(thrown is BBError)
        assertEquals("Operation cancelled", thrown?.message)
        assertTrue(operation.isComplete)
    }

    @Test
    fun `setSuccess is safe to call again after the continuation already resumed`() = runTest {
        val operation = TestOperation()

        suspendCoroutine<String> { continuation ->
            operation.continuation = continuation
            operation.setSuccess("first")
        }

        // Must not throw even though the continuation was already resumed above.
        operation.setSuccess("second")

        assertTrue(operation.isComplete)
    }

    @Test
    fun `base onConnectionStateChange cancels the operation on STATE_DISCONNECTED`() = runTest {
        val operation = TestOperation()
        val gatt = mock<BluetoothGatt>()

        val thrown = runCatching {
            suspendCoroutine<String> { continuation ->
                operation.continuation = continuation
                operation.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)
            }
        }.exceptionOrNull()

        assertTrue(thrown is BBError)
        assertTrue(operation.isComplete)
    }

    @Test
    fun `base onConnectionStateChange ignores a null gatt`() {
        val operation = TestOperation()

        operation.onConnectionStateChange(null, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue(!operation.isComplete)
    }

    @Test
    fun `timeout is 5 seconds for every operation`() {
        assertEquals(5.0f, TestOperation().timeout)
    }
}

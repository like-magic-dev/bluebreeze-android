//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.flows

import app.cash.turbine.test
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MutableSharedStateFlowTests {
    @Test
    fun `value reflects the latest emitted value`() {
        val flow = MutableSharedStateFlow(0)

        flow.emit(1)
        flow.emit(2)

        assertEquals(2, flow.value)
    }

    @Test
    fun `a late subscriber immediately receives the most recently emitted value`() = runTest {
        val flow = MutableSharedStateFlow("initial")

        flow.emit("first")
        flow.emit("second")

        flow.test {
            assertEquals("second", awaitItem())
        }
    }

    @Test
    fun `an active subscriber sees every value emitted after it starts collecting`() = runTest {
        val flow = MutableSharedStateFlow(0)
        val collected = mutableListOf<Int>()

        val job = launch { flow.toList(collected) }
        runCurrent()

        flow.emit(1)
        runCurrent()

        flow.emit(2)
        runCurrent()

        job.cancel()

        assertEquals(listOf(0, 1, 2), collected)
    }

    @Test
    fun `replayCache always contains exactly the current value`() {
        val flow = MutableSharedStateFlow(0)

        flow.emit(42)

        assertEquals(listOf(42), flow.replayCache)
    }
}

//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.flows

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/// This class extends the MutableSharedFlow giving it state persistence similar to
/// the MutableStateFlow interface.
/// In contrast to the MutableStateFlow this class is meant to prevent dropping of
/// values and uses a generous buffer for queuing values.

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MutableSharedStateFlow<T>(
    initialValue: T
) : StateFlow<T> {
    private val _flow = MutableSharedFlow<T>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var _value: T = initialValue

    val flow: SharedFlow<T> get() = _flow
    override val value: T get() = _value

    fun emit(value: T) {
        _value = value

        // We use a non-suspending try emit as the strategy is DROP_OLDEST
        // so this will always succeed (in the worst case, it will drop an
        // older value to make room for the new value)
        _flow.tryEmit(value)
    }

    override val replayCache: List<T>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing = flow.collect(collector)
}

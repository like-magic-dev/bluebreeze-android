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

/**
 * A [StateFlow] built on [MutableSharedFlow] instead of `MutableStateFlow`, used throughout
 * BlueBreeze for every publicly observable property (`BBDevice.connectionStatus`,
 * `BBManager.state`, and so on).
 *
 * `MutableStateFlow` collapses rapid updates: a collector that's slow, or briefly not collecting,
 * only ever sees the *latest* value, silently skipping any it emitted in between -- fine for UI
 * state, but wrong here, since BlueBreeze's GATT callbacks can fire updates faster than a
 * collector processes them and every one matters (e.g. a device transiently reporting
 * `connected` then `disconnected` within the same event burst). This class instead buffers up
 * to 16 pending values and only drops the *oldest* one once that buffer is genuinely full,
 * rather than dropping down to a single latest value on every emission.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class MutableSharedStateFlow<T>(
    initialValue: T
) : StateFlow<T> {
    private val _flow = MutableSharedFlow<T>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Written from many threads (GATT callback threads, scan callbacks, callers), so @Volatile
    // for readers of .value and @Synchronized on emit() to keep it paired with the replay cache.
    @Volatile
    private var _value: T = initialValue

    init {
        // Seed _flow's own replay buffer with the initial value. Without this, a collector that
        // subscribes before the first emit() call gets nothing until then.
        _flow.tryEmit(initialValue)
    }

    /** The underlying [SharedFlow], for callers that specifically want shared-flow (rather than state-flow) semantics. */
    val flow: SharedFlow<T> get() = _flow

    override val value: T get() = _value

    /** Publishes [value] as the new current value and to every active collector. */
    @Synchronized
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

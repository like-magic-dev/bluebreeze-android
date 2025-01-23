//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBDevice
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBScanResult
import dev.likemagic.bluebreeze.BBState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    context: Context,
) : ViewModel() {
    internal val manager = BBManager(context)

    val authorizationStatus = manager.authorizationStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BBAuthorization.unknown,
    )

    val state = manager.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BBState.unknown,
    )

    val scanEnabled = manager.scanEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    private val _scanResults = MutableStateFlow<Map<String, BBScanResult>>(emptyMap())
    val scanResults: StateFlow<Map<String, BBScanResult>> get() = _scanResults

    init {
        viewModelScope.launch {
            manager.scanResults
                .collect { scanResult ->
                    _scanResults.value = scanResults.value.toMutableMap().apply {
                        this[scanResult.address] = scanResult
                    }
                }
        }
    }
}

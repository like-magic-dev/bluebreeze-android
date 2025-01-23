//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBDevice
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState

@Composable
fun HomeView(
    navController: NavController,
    viewModel: MainViewModel,
) {
    val authorizationStatus = viewModel.authorizationStatus.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()

    if (authorizationStatus.value != BBAuthorization.authorized) {
        PermissionsView(
            navController = navController,
            viewModel = viewModel,
        )
    } else if (state.value != BBState.poweredOn) {
        OfflineView()
    } else {
        ScanningView(
            navController = navController,
            viewModel = viewModel,
        )
    }
}

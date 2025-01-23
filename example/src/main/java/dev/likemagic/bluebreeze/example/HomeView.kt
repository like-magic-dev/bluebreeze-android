//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBAuthorization
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
        ScanView(
            navController = navController,
            viewModel = viewModel,
        )
    }
}

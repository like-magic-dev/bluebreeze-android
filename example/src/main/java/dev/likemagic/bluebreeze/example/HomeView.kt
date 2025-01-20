//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState

@Composable
fun HomeView(
    navController: NavController,
    manager: BBManager,
) {
    val authorizationStatus = manager.authorizationStatus.collectAsStateWithLifecycle()
    val state = manager.state.collectAsStateWithLifecycle()

    if (authorizationStatus.value != BBAuthorization.authorized) {
        PermissionsView(
            navController = navController,
            manager = manager,
        )
    } else if (state.value != BBState.poweredOn) {
        OfflineView()
    } else {
        ScanningView(
            navController = navController,
            manager = manager,
        )
    }
}

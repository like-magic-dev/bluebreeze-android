package dev.likemagic.bluebreeze.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState

@Composable
fun HomeView(
    context: Context,
    manager: BBManager,
) {
    val authorizationStatus = manager.authorizationStatus.collectAsStateWithLifecycle()
    val state = manager.state.collectAsStateWithLifecycle()

    if (authorizationStatus.value != BBAuthorization.authorized) {
        PermissionsView(
            context = context,
            manager = manager,
        )
    } else if (state.value != BBState.poweredOn) {
        OfflineView()
    } else {
        ScanningView(
            context = context,
            manager = manager,
        )
    }
}
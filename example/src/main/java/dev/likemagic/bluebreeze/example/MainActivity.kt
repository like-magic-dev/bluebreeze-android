package dev.likemagic.bluebreeze.example

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState
import dev.likemagic.bluebreeze.example.ui.theme.BluebreezeTheme

class MainActivity : ComponentActivity() {
    private lateinit var manager: BBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = BBManager(this)

        enableEdgeToEdge()
        setContent {
            BluebreezeTheme {
                HomeView(
                    context = this,
                    manager = manager,
                )
            }
        }
    }
}

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

@Composable
fun PermissionsView(
    context: Context,
    manager: BBManager,
) {
    val authorizationStatus = manager.authorizationStatus.collectAsStateWithLifecycle()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.the_app_is_not_authorized))

            when (authorizationStatus.value) {
                BBAuthorization.unknown -> {
                    Button({
                        manager.authorizationRequest(context)
                    }) {
                        Text(stringResource(R.string.show_authorization_popup))
                    }
                }
                BBAuthorization.showRationale -> {
                    Button({
                        AlertDialog.Builder(context)
                            .setMessage(context.getString(R.string.this_is_a_rationale_for_the_permissions))
                            .setNegativeButton(context.getString(R.string.cancel)) { _, _ -> }
                            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                                manager.authorizationRequest(context)
                            }
                            .show()
                    }) {
                        Text(stringResource(R.string.show_rationale_popup))
                    }
                }
                else -> {
                    Button({
                        manager.authorizationOpenSettings(context)
                    }) {
                        Text(stringResource(R.string.open_app_settings))
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineView() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.bluetooth_offline))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanningView(
    context: Context,
    manager: BBManager,
) {
    val scanningEnabled = manager.scanningEnabled.collectAsStateWithLifecycle()
    val devices = manager.devices.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("BLE Scanning")
                },
                actions = {
                    if (scanningEnabled.value) {
                        Button({
                            manager.scanningStop(context)
                        }) {
                            Text("Stop scanning")
                        }
                    } else {
                        Button({
                            manager.scanningStart(context)
                        }) {
                            Text("Start scanning")
                        }
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            devices.value.values.forEach {
                Text(it.name ?: it.address)
            }
        }
    }
}

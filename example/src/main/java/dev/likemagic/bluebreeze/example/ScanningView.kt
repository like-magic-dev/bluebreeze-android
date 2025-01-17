package dev.likemagic.bluebreeze.example

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.likemagic.bluebreeze.BBManager

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
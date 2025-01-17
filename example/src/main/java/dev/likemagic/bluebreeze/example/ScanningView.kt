package dev.likemagic.bluebreeze.example

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            items(
                devices.value.values.toList(),
                key = { device -> device.address }
            ) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 4.dp,
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                device.name ?: device.address,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(device.address)
                        }
                        Text(device.rssi.toString())
                    }
                }
            }
        }
    }
}
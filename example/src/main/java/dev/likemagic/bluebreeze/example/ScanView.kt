//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanView(
    navController: NavController,
    viewModel: MainViewModel,
) {
    val context = navController.context

    val scanEnabled = viewModel.scanEnabled.collectAsStateWithLifecycle()
    val scanResults = viewModel.scanResults.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.ble_scan))
                },
                actions = {
                    if (scanEnabled.value) {
                        TextButton({
                            viewModel.manager.scanStop(context)
                        }) {
                            Text(stringResource(R.string.stop_scan).uppercase())
                        }
                    } else {
                        TextButton({
                            viewModel.manager.scanStart(context)
                        }) {
                            Text(stringResource(R.string.start_scan).uppercase())
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
                scanResults.value.values.toList(),
                key = { device -> device.hashCode() }
            ) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 4.dp,
                        ),
                    onClick = {
                        navController.navigate(route = Route.Device.route + "?deviceAddress=${device.address}")
                    },
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
                                style = MaterialTheme.typography.bodyLarge.merge(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                device.manufacturerName ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (device.advertisedServices.isNotEmpty()) {
                                Text(
                                    device.advertisedServices.joinToString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(
                            device.rssi.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
        }
    }
}

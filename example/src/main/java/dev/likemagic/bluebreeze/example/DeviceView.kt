//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBCharacteristic
import dev.likemagic.bluebreeze.BBCharacteristicProperty
import dev.likemagic.bluebreeze.BBDevice
import dev.likemagic.bluebreeze.BBDeviceConnectionStatus
import dev.likemagic.bluebreeze.BBError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceView(
    navController: NavController,
    device: BBDevice,
) {
    val connectionStatus = device.connectionStatus.collectAsStateWithLifecycle()
    var connectionOperation = remember { mutableStateOf(false) }

    val services = device.services.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(device.name ?: device.address)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (connectionOperation.value) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(52.dp)
                                .height(20.dp)
                                .padding(horizontal = 16.dp)
                        )
                    } else {
                        when (connectionStatus.value) {
                            BBDeviceConnectionStatus.connected -> {
                                TextButton({
                                    connectionOperation.value = true

                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            device.disconnect()
                                        } catch (e: BBError) {
                                            // Ignore error
                                        }

                                        CoroutineScope(Dispatchers.Main).launch {
                                            connectionOperation.value = false
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.disconnect).uppercase())
                                }
                            }
                            BBDeviceConnectionStatus.disconnected -> {
                                TextButton({
                                    connectionOperation.value = true

                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            device.connect()
                                            device.discoverServices()
                                            device.requestMTU(255)
                                        } catch (e: BBError) {
                                            // Ignore error
                                        }

                                        CoroutineScope(Dispatchers.Main).launch {
                                            connectionOperation.value = false
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.connect).uppercase())
                                }
                            }
                        }
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (connectionStatus.value == BBDeviceConnectionStatus.connected) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                items(
                    services.value.toList(),
                    key = { service -> service.uuid.toString() }
                ) { service ->
                    Column(
                        modifier = Modifier
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp,
                            )
                    ) {
                        Text(
                            service.name ?: service.uuid.toString(),
                            style = MaterialTheme.typography.bodySmall
                                .merge(fontWeight = FontWeight.Bold)
                        )
                        service.characteristics.forEach { characteristic ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical = 4.dp,
                                    )
                            ) {
                                CharacteristicView(
                                    navController = navController,
                                    characteristic = characteristic,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacteristicView(
    navController: NavController,
    characteristic: BBCharacteristic,
) {
    val data = characteristic.data.collectAsStateWithLifecycle()
    val isNotifying = characteristic.isNotifying.collectAsStateWithLifecycle()

    val canRead = characteristic.properties.contains(BBCharacteristicProperty.read)
    val canWriteWithResponse = characteristic.properties.contains(BBCharacteristicProperty.writeWithResponse)
    val canWriteWithoutResponse = characteristic.properties.contains(BBCharacteristicProperty.writeWithoutResponse)
    val canNotify = characteristic.properties.contains(BBCharacteristicProperty.notify)

    val openWriteDialog = remember { mutableStateOf(false) }
    if (openWriteDialog.value) {
        CharacteristicWriteDialog(
            characteristic = characteristic,
            onDismiss = {
                openWriteDialog.value = false
            }
        )
    }

    Card {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text(
                    characteristic.name ?: characteristic.uuid.toString(),
                    style = MaterialTheme.typography.bodyLarge
                        .merge(fontWeight = FontWeight.Bold)
                )
                Text(
                    if (data.value.isEmpty()) "-" else data.value.hexString,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row {
                    if (canRead) {
                        TextButton({
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    characteristic.read()
                                } catch (e: BBError) {
                                    // Ignore error
                                }
                            }
                        }) {
                            Text("READ")
                        }
                    }
                    if (canWriteWithResponse or canWriteWithoutResponse) {
                        TextButton({
                            openWriteDialog.value = true
                        }) {
                            Text("WRITE")
                        }
                    }
                    if (canNotify) {
                        if (isNotifying.value) {
                            TextButton({
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        characteristic.unsubscribe()
                                    } catch (e: BBError) {
                                        // Ignore error
                                    }
                                }
                            }) {
                                Text("UNSUBSCRIBE")
                            }
                        } else {
                            TextButton({
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        characteristic.subscribe()
                                    } catch (e: BBError) {
                                        // Ignore error
                                    }
                                }
                            }) {
                                Text("SUBSCRIBE")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CharacteristicWriteDialog(
    characteristic: BBCharacteristic,
    onDismiss: () -> Unit,
) {
    val writeValue = remember { mutableStateOf("") }
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.write_characteristic))
        },
        text = {
            TextField(
                value = writeValue.value,
                onValueChange = {
                    val VALID = "0123456789ABCDEF".toCharArray().toSet()
                    writeValue.value = it.uppercase().filter { it in VALID }
                },
                placeholder = { Text(text = stringResource(R.string.enter_value)) },
            )
        },
        onDismissRequest = {
            onDismiss()
        },
        confirmButton = {
            TextButton(
                enabled = (writeValue.value.byteArray != null),
                onClick = {
                    val writeWithResponse = characteristic.properties.contains(
                        BBCharacteristicProperty.writeWithResponse
                    )

                    writeValue.value.byteArray?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                characteristic.write(
                                    it,
                                    withResponse = writeWithResponse,
                                )
                            } catch (e: BBError) {
                                // Ignore error
                            }
                        }
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

val String.byteArray: ByteArray?
    get() {
        check((length % 2) == 0) {
            return null
        }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

val ByteArray.hexString: String
    get() = joinToString("") { it.toUByte().toString(16).uppercase().padStart(2, '0') }

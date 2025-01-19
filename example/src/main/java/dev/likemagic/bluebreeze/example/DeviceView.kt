package dev.likemagic.bluebreeze.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBCharacteristic
import dev.likemagic.bluebreeze.BBCharacteristicProperty
import dev.likemagic.bluebreeze.BBDevice
import dev.likemagic.bluebreeze.BBDeviceConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceView(
    navController: NavController,
    device: BBDevice,
) {
    val context = navController.context

    val connectionStatus = device.connectionStatus.collectAsStateWithLifecycle()
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
                    when (connectionStatus.value) {
                        BBDeviceConnectionStatus.connected -> {
                            TextButton({
                                CoroutineScope(Dispatchers.IO).launch {
                                    device.disconnect()
                                }
                            }) {
                                Text(stringResource(R.string.disconnect).uppercase())
                            }
                        }
                        BBDeviceConnectionStatus.disconnected -> {
                            TextButton({
                                CoroutineScope(Dispatchers.IO).launch {
                                    device.connect()
                                    device.discoverServices()
                                    device.requestMTU(255)
                                }
                            }) {
                                Text(stringResource(R.string.connect).uppercase())
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
                    key = { service -> service.uuid }
                ) { service ->
                    Column(
                        modifier = Modifier
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp,
                            )
                    ) {
                        Text(service.uuid.toString(), style = MaterialTheme.typography.bodySmall)
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
    val context = navController.context

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

    Card() {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Column {
                Text(
                    characteristic.uuid.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    data.value.hexString,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                if (canRead) {
                    TextButton({
                        CoroutineScope(Dispatchers.IO).launch {
                            characteristic.read()
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
                                characteristic.unsubscribe()
                            }
                        }) {
                            Text("UNSUBSCRIBE")
                        }
                    } else {
                        TextButton({
                            CoroutineScope(Dispatchers.IO).launch {
                                characteristic.subscribe()
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
                    writeValue.value.byteArray?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            val withResponse = characteristic.properties.contains(
                                BBCharacteristicProperty.writeWithResponse
                            )
                            characteristic.write(
                                it,
                                withResponse = withResponse,
                            )
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
    get() = joinToString("") { "%02x".format(it) }


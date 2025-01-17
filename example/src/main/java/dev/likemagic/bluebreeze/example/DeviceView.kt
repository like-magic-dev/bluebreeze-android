package dev.likemagic.bluebreeze.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            items(
                services.value.toList(),
                key = { service -> service }
            ) { service ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 4.dp,
                        ),
                ) {
                    Text(service.toString())
                }
            }
        }
    }
}
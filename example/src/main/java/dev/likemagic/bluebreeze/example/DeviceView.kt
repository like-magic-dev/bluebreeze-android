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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.likemagic.bluebreeze.BBDevice
import dev.likemagic.bluebreeze.BBDeviceConnectionStatus
import dev.likemagic.bluebreeze.BBManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceView(
    navController: NavController,
    device: BBDevice,
) {
    val context = navController.context

    val connectionStatus = device.connectionStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(device.name ?: device.address)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    when (connectionStatus.value) {
                        BBDeviceConnectionStatus.connected -> {
                            TextButton({
                            }) {
                                Text(stringResource(R.string.disconnect).uppercase())
                            }
                        }
                        BBDeviceConnectionStatus.disconnected -> {
                            TextButton({
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
//            items(
//                devices.value.values.toList(),
//                key = { device -> device.address }
//            ) { device ->
//                Card(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(
//                            horizontal = 16.dp,
//                            vertical = 4.dp,
//                        ),
//                    onClick = {
//                        print("WTF")
//                    },
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(
//                                horizontal = 16.dp,
//                                vertical = 8.dp,
//                            ),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        Column {
//                            Text(
//                                device.name ?: device.address,
//                                style = MaterialTheme.typography.bodyLarge,
//                            )
//                            Text(
//                                device.address,
//                                style = MaterialTheme.typography.bodySmall,
//                            )
//                        }
//                        Text(
//                            device.rssi.toString(),
//                            style = MaterialTheme.typography.headlineSmall,
//                        )
//                    }
//                }
//            }
        }
    }
}
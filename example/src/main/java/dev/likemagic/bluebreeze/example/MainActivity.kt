//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.likemagic.bluebreeze.example.ui.theme.BluebreezeTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = MainViewModel(this)

        enableEdgeToEdge()
        setContent {
            BluebreezeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationStack(
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

sealed class Route(val route: String) {
    object Home : Route("home")
    object Device : Route("device")
}

@Composable
fun NavigationStack(
    viewModel: MainViewModel,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
    ) {
        composable(route = Route.Home.route) {
            HomeView(
                navController = navController,
                viewModel = viewModel,
            )
        }
        composable(
            route = Route.Device.route + "?deviceAddress={deviceAddress}",
            arguments = listOf(
                navArgument("deviceAddress") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            val deviceAddress = it.arguments?.getString("deviceAddress") ?: return@composable
            val device = viewModel.manager.devices.value[deviceAddress] ?: return@composable

            DeviceView(
                navController = navController,
                device = device,
            )
        }
    }
}

package dev.likemagic.bluebreeze.example

import android.content.Context
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
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.example.ui.theme.BluebreezeTheme

class MainActivity : ComponentActivity() {
    private lateinit var manager: BBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = BBManager(this)

        enableEdgeToEdge()
        setContent {
            BluebreezeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                )  {
                    NavigationStack(
                        context = this,
                        manager = manager,
                    )
                }
            }
        }
    }
}

sealed class Route(val route: String) {
    object Home: Route("home")
    object Device: Route("device")
}

@Composable
fun NavigationStack(
    context: Context,
    manager: BBManager,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
    ) {
        composable(route = Route.Home.route) {
            HomeView(
                navController = navController,
                manager = manager,
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
            val device = manager.devices.value[deviceAddress] ?: return@composable

            DeviceView(
                navController = navController,
                device = device,
            )
        }
    }
}
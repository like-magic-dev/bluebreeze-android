package dev.likemagic.bluebreeze.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                HomeView(
                    context = this,
                    manager = manager,
                )
            }
        }
    }
}


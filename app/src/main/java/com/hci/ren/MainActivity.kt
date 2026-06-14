package com.hci.ren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hci.ren.feature.home.presentation.HomeRoute
import com.hci.ren.ui.theme.RenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RenTheme(dynamicColor = false) {
                HomeRoute()
            }
        }
    }
}

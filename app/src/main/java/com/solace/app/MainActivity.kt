package com.solace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.solace.app.ui.SolaceApp
import com.solace.app.ui.theme.SolaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolaceTheme {
                SolaceApp()
            }
        }
    }
}

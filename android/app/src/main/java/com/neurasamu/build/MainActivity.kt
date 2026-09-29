package com.neurasamu.build

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neurasamu.build.ui.SamuRoot
import com.neurasamu.build.ui.theme.SamuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamuTheme {
                SamuRoot()
            }
        }
    }
}

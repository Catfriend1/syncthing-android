package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

class LicenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LicenseScreen()
        }
    }
}

@Composable
fun LicenseScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibrariesContainer(modifier = Modifier.fillMaxSize())
        }
    }
}

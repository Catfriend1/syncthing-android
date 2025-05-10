package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.IconButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.nutomic.syncthingandroid.R

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
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(id = R.string.open_source_licenses_title)) },
                        navigationIcon = {
                            IconButton(onClick = { backDispatcher?.onBackPressed()  }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                            }
                        }
                    )
                }
            ) { paddingValues ->
                LibrariesContainer(modifier = Modifier.fillMaxSize().padding(paddingValues))
            }
        }
    }
}

package com.nutomic.syncthingandroid.activities

import android.content.Context
import android.os.Bundle
import android.util.TypedValue

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

import com.mikepenz.aboutlibraries.ui.compose.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

import com.nutomic.syncthingandroid.R

class LicenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opt-in to edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SyncthingTheme {
                LicenseScreen()
            }
        }
    }
}

@Composable
fun SyncthingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(ContextCompat.getColor(context, R.color.primary)),
            onPrimary = Color.White,
            primaryContainer = Color(ContextCompat.getColor(context, R.color.primary_dark)),
            onPrimaryContainer = Color.White,
            secondary = Color(ContextCompat.getColor(context, R.color.accent)),
            onSecondary = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = Color(ContextCompat.getColor(context, R.color.primary)),
            onPrimary = Color.White,
            primaryContainer = Color(ContextCompat.getColor(context, R.color.primary_dark)),
            onPrimaryContainer = Color.White,
            secondary = Color(ContextCompat.getColor(context, R.color.accent)),
            onSecondary = Color.Black,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val resources = LocalResources.current
        
        // Read the libraries content outside of rememberLibraries to avoid the lint warning
        val librariesContent = remember {
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        }
        val libraries by rememberLibraries(librariesContent)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.open_source_licenses_title)) },
                    navigationIcon = {
                        IconButton(onClick = { backDispatcher?.onBackPressed()  }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            LibrariesContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                libraries = libraries
            )
        }
    }
}

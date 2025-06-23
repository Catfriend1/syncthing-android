package com.nutomic.syncthingandroid.activities

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import com.mikepenz.aboutlibraries.ui.compose.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

import com.nutomic.syncthingandroid.R

class LicenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LicenseScreen()
        }
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val contentView = findViewById<ViewGroup>(android.R.id.content)
            if (contentView.childCount > 0) {
                val topLevel = contentView.getChildAt(0)
                when (topLevel) {
                    is ViewGroup -> addSpacerIfNeeded(topLevel)
                }
            }
        }
    }

    private fun addSpacerIfNeeded(parent: ViewGroup) {
        val statusBarSpacer = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getStatusBarHeight()
            )
            setBackgroundColor(ContextCompat.getColor(this@LicenseActivity, R.color.primary))
        }
        parent.addView(statusBarSpacer, 0)
    }

    private fun getStatusBarHeight(): Int {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(
                typedValue.data, resources.displayMetrics
            )
        } else {
            0
        }
    }
}

@Composable
fun LicenseScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val context = LocalContext.current
            val libraries by rememberLibraries {
                val inputStream = context.resources.openRawResource(R.raw.aboutlibraries)
                inputStream.bufferedReader().use { it.readText() }
            }

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
}

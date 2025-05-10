package com.nutomic.syncthingandroid.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.activity.compose.setContent
import com.mikepenz.aboutlibraries.LibsBuilder
import com.nutomic.syncthingandroid.R

class LicenseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LibsBuilder()
            .withActivityTitle("Open Source Lizenzen")
            .start(this)
    }
}
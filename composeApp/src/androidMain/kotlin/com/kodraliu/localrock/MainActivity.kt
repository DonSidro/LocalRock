package com.kodraliu.localrock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.SharedPreferencesSettings
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.protocol.initVacLocalAndroidContext

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initVacLocalAndroidContext(applicationContext)
        val prefs = applicationContext.getSharedPreferences("vaclocal", MODE_PRIVATE)
        container = AppContainer(SharedPreferencesSettings(prefs))

        setContent { App(container) }
    }
}

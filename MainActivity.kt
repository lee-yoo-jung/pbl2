package com.example.pbl2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedLang = loadLanguage(this)
        setLocale(this, savedLang)

        setContent {
            MaterialTheme {
                SettingsScreen()
            }
        }
    }
}
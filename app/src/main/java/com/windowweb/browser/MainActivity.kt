package com.windowweb.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.windowweb.browser.data.AppDatabase
import com.windowweb.browser.data.SessionRepository
import com.windowweb.browser.ui.BrowserScreen
import com.windowweb.browser.ui.BrowserViewModel

class MainActivity : ComponentActivity() {
    private val repository: SessionRepository by lazy {
        SessionRepository(AppDatabase.get(applicationContext))
    }

    private val viewModel: BrowserViewModel by viewModels {
        BrowserViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF5D45A7),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE8DDFF),
                    onPrimaryContainer = Color(0xFF24114F),
                    secondaryContainer = Color(0xFFF0ECF8),
                    onSecondaryContainer = Color(0xFF24212B),
                    surface = Color(0xFFFFFBFF),
                    surfaceVariant = Color(0xFFECE6F0),
                    background = Color(0xFFFFFBFF),
                )
            ) {
                BrowserScreen(viewModel = viewModel)
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.clearMemoryPressureFlag()
    }

    override fun onStop() {
        viewModel.checkpointWorkspace(cleanExit = true)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            viewModel.handleMemoryPressure()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val url = intent?.dataString ?: return
        viewModel.loadIncomingUrl(url)
    }
}

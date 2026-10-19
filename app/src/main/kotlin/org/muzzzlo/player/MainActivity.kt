package org.muzzzlo.player

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.LocalCoverLoader
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.feature.settings.LocaleContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playerController: PlayerController

    @Inject lateinit var coverLoader: AppCoverLoader

    @Inject lateinit var settings: SettingsRepository

    private val accentViewModel: AccentViewModel by viewModels()

    private var appliedLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = LocaleContext.savedLanguage(newBase)
        super.attachBaseContext(LocaleContext.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerController.connect()
        lifecycleScope.launch {
            // The language applies to the whole activity, so a change restarts it.
            settings.settings.map { it.language }.distinctUntilChanged().collect {
                if (it != appliedLanguage) recreate()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            // The playback notification needs this permission; playback works without it.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            val accent by accentViewModel.accent.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalCoverLoader provides coverLoader) {
                MuzzzloTheme(accent) { MuzzzloNavHost() }
            }
        }
    }
}

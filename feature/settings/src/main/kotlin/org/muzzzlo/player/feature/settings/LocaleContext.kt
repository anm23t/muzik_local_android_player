package org.muzzzlo.player.feature.settings

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.muzzzlo.player.core.model.AppLanguage
import java.util.Locale

/**
 * Applies the interface language chosen in the settings, independent of the system language.
 * `MainActivity` wraps its base context with [wrap] and recreates itself when the language changes.
 */
object LocaleContext {
    /**
     * The language saved in the settings. Read synchronously: it runs once per activity start, before Hilt is ready,
     * and the file is a few bytes.
     */
    fun savedLanguage(context: Context): AppLanguage =
        runBlocking { languageOf(context.settingsDataStore.data.first()) }

    /** [base] with the locale of [language]; [base] itself for [AppLanguage.SYSTEM]. */
    fun wrap(
        base: Context,
        language: AppLanguage,
    ): Context {
        val tag = language.tag ?: return base
        val configuration = Configuration(base.resources.configuration)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun languageOf(preferences: Preferences): AppLanguage =
        DataStoreSettingsRepository.toSettings(preferences).language
}

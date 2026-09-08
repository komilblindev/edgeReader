package uz.komil.mediapro.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app UI language (uz / ru / en; empty = follow system).
 *
 * MediaPro uses plain ComponentActivity (not AppCompat), so the standard
 * AppCompatDelegate locale switching does not apply. Instead [tag] is read at
 * activity attach time and the base context is re-created with an overridden
 * locale configuration. Changing the language saves the tag, sets [tag] and
 * calls Activity.recreate() — the fresh activity picks up the new resources.
 */
object Lang {

    @Volatile var tag: String = "" // "uz" | "ru" | "en" | ""

    /** Wrap the activity base context so its resources render in [tag]. */
    fun wrap(base: Context): Context {
        val t = tag
        if (t.isBlank()) return base
        val locale = Locale.forLanguageTag(t)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun displayName(tag: String): String = when (tag) {
        "uz" -> "O'zbekcha"
        "ru" -> "Русский"
        "en" -> "English"
        else -> "System"
    }

    /** Full tag accepted by Locale.forLanguageTag (uz/ru/en → uz-UZ/ru-RU/en). */
    fun localeTag(tag: String): String = when (tag) {
        "uz" -> "uz-UZ"
        "ru" -> "ru-RU"
        "en" -> "en-US"
        else -> ""
    }
}

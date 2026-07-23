package com.example.adsdemo.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.adsmodule.core.language.LocaleApplyResult
import com.example.adsmodule.core.language.LocaleApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCompatLocaleApplier : LocaleApplier {
    override suspend fun apply(languageTag: String): LocaleApplyResult =
        withContext(Dispatchers.Main) {
            try {
                val locales = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(locales)
                LocaleApplyResult.Success
            } catch (error: Throwable) {
                LocaleApplyResult.Failure(error.message ?: "locale apply failed")
            }
        }
}

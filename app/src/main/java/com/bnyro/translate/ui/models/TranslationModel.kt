package com.bnyro.translate.ui.models

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bnyro.translate.DatabaseHolder.Companion.Db
import com.bnyro.translate.R
import com.bnyro.translate.const.TranslationEngines
import com.bnyro.translate.db.obj.HistoryItem
import com.bnyro.translate.db.obj.Language
import com.bnyro.translate.ext.awaitQuery
import com.bnyro.translate.ext.query
import com.bnyro.translate.obj.Translation
import com.bnyro.translate.util.JsonHelper
import com.bnyro.translate.util.Preferences
import com.bnyro.translate.util.TessHelper
import com.bnyro.translate.util.TranslationEngine
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString

class TranslationModel : ViewModel() {
    var engine: TranslationEngine = getCurrentEngine()

    var simTranslationEnabled by mutableStateOf(
        Preferences.get(Preferences.simultaneousTranslationKey, false)
    )
    var enabledSimEngines = getEnabledEngines()

    var availableLanguages: List<Language> by mutableStateOf(
        emptyList()
    )

    var sourceLanguage: Language by mutableStateOf(
        getLanguageByPrefKey(Preferences.sourceLanguage) ?: Language("", "Auto")
    )

    var targetLanguage: Language by mutableStateOf(
        getLanguageByPrefKey(Preferences.targetLanguage) ?: Language("en", "English")
    )

    var insertedText: String by mutableStateOf(
        ""
    )

    var translation: Translation by mutableStateOf(
        Translation("")
    )

    var translatedTexts: MutableMap<String, Translation> =
        TranslationEngines.engines
            .associate { it.name to Translation("") }
            .toMutableMap()

    var bookmarkedLanguages by mutableStateOf(listOf<Language>())

    var translating by mutableStateOf(false)

    var extractionProgess by mutableStateOf(0)

    var extracting by mutableStateOf(false)

    var extractionJob: Job = Job()
    private fun getLanguageByPrefKey(key: String): Language? {
        return try {
            JsonHelper.json.decodeFromString<Language>(Preferences.get(key, ""))
        } catch (e: Exception) {
            null
        }
    }

    fun enqueueTranslation() {
        if (!Preferences.get(Preferences.translateAutomatically, true)) return

        val insertedTextTemp = insertedText
        Handler(
            Looper.getMainLooper()
        ).postDelayed(
            {
                if (insertedTextTemp == insertedText) translateNow()
            },
            Preferences.get(
                Preferences.fetchDelay,
                500f
            ).toLong()
        )
    }

    fun translateNow() {
        if (insertedText == "" || targetLanguage == sourceLanguage) {
            translation = Translation("")
            return
        }

        translating = true
        extracting = false

        translatedTexts = TranslationEngines.engines
            .associate { it.name to Translation("") }
            .toMutableMap()

        CoroutineScope(Dispatchers.IO).launch {
            val translation = try {
                engine.translate(
                    insertedText,
                    sourceLanguage.code,
                    targetLanguage.code
                )
            } catch (e: Exception) {
                Log.e("error", e.message.toString())
                return@launch
            }

            translating = false

            if (insertedText != "") {
                this@TranslationModel.translation = translation
                translatedTexts[engine.name] = translation
                saveToHistory()
            }
        }

        if (simTranslationEnabled) simTranslation()
    }

    private fun simTranslation() {
        enabledSimEngines.forEach {
            if (it != engine) {
                CoroutineScope(Dispatchers.IO).launch {
                    val translation = try {
                        it.translate(
                            insertedText,
                            sourceLanguage.code,
                            targetLanguage.code
                        )
                    } catch (e: Exception) {
                        return@launch
                    }
                    translatedTexts[it.name] = translation
                }
            }
        }
    }

    private fun saveToHistory() {
        if (!Preferences.get(
                Preferences.historyEnabledKey,
                true
            )
        ) {
            return
        }
        query {
            Db.historyDao().insertAll(
                HistoryItem(
                    sourceLanguageCode = sourceLanguage.code,
                    sourceLanguageName = sourceLanguage.name,
                    targetLanguageCode = targetLanguage.code,
                    targetLanguageName = targetLanguage.name,
                    insertedText = insertedText,
                    translatedText = translation.translatedText
                )
            )
        }
    }

    fun clearTranslation() {
        extracting = false
        insertedText = ""
        translation = Translation("")
        Log.w("TranslateYou", "Extraction cancelled")
        if(extractionJob.isActive){
            TessHelper.stopProcessing();
        }
        extractionJob.cancel();
        extractionProgess = 0

    }

    private fun fetchLanguages(onError: (Exception) -> Unit = {}) {
        viewModelScope.launch {
            val languages = try {
                Log.e("engine", engine.name)
                engine.getLanguages()
            } catch (e: Exception) {
                Log.e("Fetching languages", e.toString())
                onError.invoke(e)
                return@launch
            }
            this@TranslationModel.availableLanguages = languages
        }
    }

    private fun getCurrentEngine() = TranslationEngines.engines[
            Preferences.get(Preferences.apiTypeKey, 0)
    ]

    private fun getEnabledEngines() = TranslationEngines.engines.filter {
        it.isSimultaneousTranslationEnabled()
    }

    fun refresh(context: Context) {
        engine = getCurrentEngine()
        enabledSimEngines = getEnabledEngines()
        simTranslationEnabled = Preferences.get(Preferences.simultaneousTranslationKey, false)

        fetchLanguages {
            Toast.makeText(context, R.string.server_error, Toast.LENGTH_LONG).show()
        }

        fetchBookmarkedLanguages()
    }

    private fun fetchBookmarkedLanguages() {
        bookmarkedLanguages = awaitQuery {
            Db.languageBookmarksDao().getAll()
        }
    }

    fun processImage(context: Context, uri: Uri?) {
        if (!TessHelper.areLanguagesAvailable(context)) {
            Toast.makeText(context, R.string.init_tess_first, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, R.string.extracting_text, Toast.LENGTH_SHORT).show()
        extracting = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                withTimeout(60000L) {
                    var text : String? = ""
                    extractionJob = GlobalScope.launch {
                        text = TessHelper.getText(context, uri,
                             { extractionProgess = it.percent })
                    }
                    extractionJob.join()
                    if (text != null && !extractionJob.isCancelled) {
                        insertedText = text as String;
                        translateNow()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.no_text_found, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                withContext(Dispatchers.Main){
                    Toast.makeText(context, R.string.cannot_extract_text, Toast.LENGTH_LONG).show();
                }
            } catch(e: java.lang.Exception) {
                withContext(Dispatchers.Main){
                    Toast.makeText(context, R.string.unknown_error, Toast.LENGTH_LONG).show();
                }
            }
            finally {
                extracting = false
                extractionProgess = 0
            }
        }
    }
}
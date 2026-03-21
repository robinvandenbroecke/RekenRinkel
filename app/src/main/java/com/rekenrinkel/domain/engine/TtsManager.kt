package com.rekenrinkel.domain.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS Manager for reading questions and feedback aloud in Dutch.
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isEnabled = true

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("nl", "NL"))
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isInitialized) {
                    // Fallback to generic Dutch
                    val fallback = tts?.setLanguage(Locale("nl"))
                    isInitialized = fallback != TextToSpeech.LANG_MISSING_DATA &&
                            fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                // Slightly slower for kids
                tts?.setSpeechRate(0.85f)
            }
        }
    }

    fun speak(text: String) {
        if (!isEnabled || !isInitialized) return
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}


package com.myassistant

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import android.content.pm.PackageManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var apiKey: EditText
    private lateinit var command: EditText
    private lateinit var result: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale("hi", "IN")
                tts.setPitch(1.12f)
                tts.setSpeechRate(0.90f)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 24)
        }

        val title = TextView(this).apply {
            text = "🌸 POOJA AI"
            textSize = 30f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "आपकी अपनी Personal AI Assistant"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        apiKey = EditText(this).apply {
            hint = "Gemini API Key"
            inputType = 129
        }

        command = EditText(this).apply {
            hint = "पूजा से कुछ पूछिए..."
            textSize = 17f
        }

        val send = Button(this).apply {
            text = "Send"
            setOnClickListener {
                handleCommand(command.text.toString())
            }
        }

        val mic = Button(this).apply {
            text = "🎤 बोलकर बताएं"
            setOnClickListener {
                startVoice()
            }
        }

        result = TextView(this).apply {
            text = "नमस्ते! मैं पूजा हूँ। बताइए, मैं आपकी क्या मदद करूँ?"
            textSize = 18f
            setPadding(12, 25, 12, 25)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(apiKey)
        layout.addView(command)
        layout.addView(send)
        layout.addView(mic)
        layout.addView(result)

        setContentView(layout)
    }

    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            result.text = "Voice recognition उपलब्ध नहीं है।"
            return
        }

        if (checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                10
            )
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onResults(data: Bundle?) {
                    val words = data?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val text = words?.firstOrNull() ?: return
                    command.setText(text)
                    handleCommand(text)
                }

                override fun onError(error: Int) {
                    result.text = "माफ कीजिए, दोबारा बोलिए।"
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )

        recognizer?.startListening(intent)
    }

    private fun handleCommand(text: String) {
        if (text.isBlank()) return

        val lower = text.lowercase()

        if (lower.contains("youtube") ||
            lower.contains("गाना") ||
            lower.contains("song")) {

            val query = text
                .replace("youtube", "", ignoreCase = true)
                .replace("गाना चलाओ", "", ignoreCase = true)
                .replace("गाना", "", ignoreCase = true)
                .replace("song", "", ignoreCase = true)
                .trim()

            val song = if (query.isBlank()) {
                "Rajasthani songs"
            } else query

            val url = "https://www.youtube.com/results?search_query=" +
                    Uri.encode(song)

            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )

            speak("YouTube पर गाना खोज रही हूँ।")
            return
        }

        if (lower.contains("call") ||
            lower.contains("कॉल") ||
            lower.contains("फोन लगाओ")) {

            val number = Regex("""\d{10}""")
                .find(text)?.value

            if (number != null) {
                startActivity(
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:$number")
                    )
                )
                speak("डायलर खोल रही हूँ।")
            } else {
                speak("कृपया दस अंकों का नंबर बताइए।")
            }
            return
        }

        askGemini(text)
    }

    private fun askGemini(question: String) {
        val key = apiKey.text.toString().trim()

        if (key.isEmpty()) {
            speak("पहले Gemini API Key डालिए।")
            return
        }

        result.text = "पूजा सोच रही है..."

        thread {
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/" +
                    "models/gemini-3.8-flash:generateContent"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty(
                    "x-goog-api-key", key
                )
                connection.setRequestProperty(
                    "Content-Type", "application/json"
                )
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                val prompt =
                    "तुम पूजा नाम की हिंदी महिला AI Assistant हो। " +
                    "हमेशा सरल हिंदी में जवाब दो। " +
                    "यूजर का सवाल: $question"

                val part = JSONObject().put("text", prompt)

                val body = JSONObject().put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(part)
                        )
                    )
                )

                connection.outputStream.use {
                    it.write(body.toString().toByteArray())
                }

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream.bufferedReader()
                    .use { it.readText() }

                if (status !in 200..299) {
                    throw Exception("API Error $status: $response")
                }

                val json = JSONObject(response)

                val answer = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                connection.disconnect()

                runOnUiThread {
                    result.text = answer
                    speak(answer)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    result.text = "समस्या: ${e.message}"
                }
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "pooja_reply"
        )
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

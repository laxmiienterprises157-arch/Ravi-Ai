package com.rdx.ravia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                RaviAIApp(
                    onSpeak = { speak(it) },
                    onListen = { callback -> startListening(callback) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "RAVI_AI")
    }

    private fun startListening(callback: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callback("Speech recognition is not available.")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                recognizer.destroy()
                callback("")
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                recognizer.destroy()
                callback(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            recognizer.destroy()
            callback("")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

data class ChatMessage(
    val role: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaviAIApp(
    onSpeak: (String) -> Unit,
    onListen: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("ravi_ai_settings", Context.MODE_PRIVATE) 
    }

    var apiKey by remember { 
        mutableStateOf(prefs.getString("saved_api_key", "") ?: "") 
    }
    var model by remember { 
        mutableStateOf(prefs.getString("saved_model", "deepseek/deepseek-chat:free") ?: "deepseek/deepseek-chat:free") 
    }
    var selectedMode by remember { 
        mutableStateOf(prefs.getString("saved_mode", "Rudra") ?: "Rudra") 
    }

    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "assistant",
                "Namaste! Main Ravi AI hoon. Aap mujhse Hindi ya Hinglish mein baat kar sakte hain."
            )
        )
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onListen { spokenText ->
                if (spokenText.isNotBlank()) {
                    input = spokenText
                }
            }
        } else {
            error = "Voice use karne ke liye microphone permission chahiye."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RAVI AI")
                        Text("Your Personal AI Assistant", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Text("AI Mode", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Rudra", "Jena").forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { 
                            selectedMode = mode
                            prefs.edit().putString("saved_mode", mode).apply()
                        },
                        label = { Text(mode) }
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it 
                    prefs.edit().putString("saved_api_key", it.trim()).apply()
                },
                label = { Text("OpenRouter API Key (Auto Saved)") },
                placeholder = { Text("Paste your API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = model,
                onValueChange = { 
                    model = it 
                    prefs.edit().putString("saved_model", it.trim()).apply()
                },
                label = { Text("AI Model ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (message.role == "user") "You" else selectedMode,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(message.text)
                            if (message.role == "assistant") {
                                TextButton(onClick = { onSpeak(message.text) }) {
                                    Text("🔊 Speak")
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Message Ravi AI...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val permission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )
                        if (permission == PackageManager.PERMISSION_GRANTED) {
                            onListen { spokenText ->
                                if (spokenText.isNotBlank()) {
                                    input = spokenText
                                }
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎙 Voice")
                }

                Button(
                    enabled = !loading && input.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
                    onClick = {
                        val question = input.trim()
                        input = ""
                        error = ""
                        messages.add(ChatMessage("user", question))

                        scope.launch {
                            loading = true
                            try {
                                val reply = askAI(
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    mode = selectedMode,
                                    history = messages.toList()
                                )
                                messages.add(ChatMessage("assistant", reply))
                            } catch (e: Exception) {
                                error = e.message ?: "AI request failed."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send ➤")
                }
            }
        }
    }
}

suspend fun askAI(
    apiKey: String,
    model: String,
    mode: String,
    history: List<ChatMessage>
): String = withContext(Dispatchers.IO) {
    val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection)

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.doOutput = true

        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("HTTP-Referer", "https://github.com/rdx/ravia")
        connection.setRequestProperty("X-Title", "Ravi AI")

        val systemPrompt = when (mode) {
            "Jena" -> "You are Jena, a friendly AI assistant. Reply naturally in Hindi or Hinglish."
            else -> "You are Rudra, a helpful personal AI assistant. Reply naturally in Hindi or Hinglish. Be respectful, clear and practical."
        }

        val apiMessages = JSONArray()
        apiMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))

        history.takeLast(20).forEach { message ->
            apiMessages.put(
                JSONObject()
                    .put("role", if (message.role == "assistant") "assistant" else "user")
                    .put("content", message.text)
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", apiMessages)
            .put("temperature", 0.7)
            .put("max_tokens", 1000)

        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            val detail = try {
                JSONObject(response).optJSONObject("error")?.optString("message") ?: response
            } catch (_: Exception) {
                response
            }
            throw Exception("API error $responseCode: $detail")
        }

        val json = JSONObject(response)
        json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    } finally {
        connection.disconnect()
    }
}

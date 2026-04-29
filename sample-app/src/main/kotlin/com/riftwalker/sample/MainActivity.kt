package com.riftwalker.sample

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.riftwalker.aidebug.annotations.AiAction
import com.riftwalker.aidebug.annotations.AiProbe
import com.riftwalker.aidebug.annotations.AiState
import com.riftwalker.aidebug.runtime.AiDebug
import com.riftwalker.aidebug.runtime.AiDebugRuntime
import com.riftwalker.aidebug.runtime.network.AiDebugNetwork
import com.google.mlkit.vision.common.InputImage
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var resultView: TextView? = null
    private var stateView: TextView? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AiDebugNetwork.interceptor())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val port = AiDebugRuntime.start(this)
        ensureSampleStorage()
        registerDebugCapabilities()
        val status = TextView(this).apply {
            text = "Riftwalker sample debug runtime listening on 127.0.0.1:$port"
            textSize = 16f
            setPadding(32, 32, 32, 16)
        }
        val state = TextView(this).apply {
            text = renderLocalState()
            textSize = 14f
            setPadding(32, 16, 32, 16)
        }
        stateView = state
        val result = TextView(this).apply {
            text = "Profile result will appear here."
            textSize = 14f
            setPadding(32, 16, 32, 32)
        }
        resultView = result
        val button = Button(this).apply {
            text = "Fetch profile"
            setOnClickListener {
                result.text = "Loading profile..."
                fetchProfile(result)
            }
        }
        val stateButton = Button(this).apply {
            text = "Render local state"
            setOnClickListener {
                refreshLocalState()
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(state)
                addView(stateButton)
                addView(button)
                addView(result)
            },
        )
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun fetchProfile(result: TextView) {
        thread(name = "sample-profile-fetch") {
            val request = Request.Builder()
                .url("https://example.com/api/profile")
                .get()
                .build()
            val text = runCatching {
                client.newCall(request).execute().use { response ->
                    "HTTP ${response.code}: ${response.body?.string().orEmpty()}"
                }
            }.getOrElse { error ->
                "Request failed: ${error.message ?: error::class.java.simpleName}"
            }
            runOnUiThread { result.text = text }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("fetchProfile", false) == true) {
            resultView?.let { result ->
                result.text = "Loading profile..."
                fetchProfile(result)
            }
        }
        if (intent?.getBooleanExtra("renderLocalState", false) == true) {
            refreshLocalState()
        }
        val renderMediaState = intent?.getBooleanExtra("renderMediaDogfoodState", false) == true
        if (intent?.getBooleanExtra("runAudioMediaDogfood", false) == true) {
            runAudioMediaDogfood(renderMediaState)
        }
        if (intent?.getBooleanExtra("runCameraMediaDogfood", false) == true) {
            runCameraMediaDogfood(renderMediaState)
        }
    }

    private fun registerDebugCapabilities() {
        AiDebug.booleanState(
            path = "user.isVip",
            description = "Sample user VIP entitlement used by local branch rendering",
            tags = listOf("sample", "user", "branch"),
            read = { SampleState.isVip },
            write = { SampleState.isVip = it },
            reset = { SampleState.isVip = false },
        )
        AiDebug.booleanState(
            path = "feature.newCheckout",
            description = "Sample checkout feature flag backed by SharedPreferences and override store",
            tags = listOf("sample", "feature-flag"),
            read = { isNewCheckoutEnabled() },
            write = { enabled ->
                getSharedPreferences("sample_flags", MODE_PRIVATE)
                    .edit()
                    .putBoolean("newCheckout", enabled)
                    .apply()
            },
            reset = {
                getSharedPreferences("sample_flags", MODE_PRIVATE)
                    .edit()
                    .putBoolean("newCheckout", false)
                    .apply()
            },
        )
        AiDebug.action(
            path = "sample.refreshLocalState",
            description = "Refresh the sample UI from current state and overrides",
            tags = listOf("sample", "ui"),
        ) {
            runOnUiThread { refreshLocalState() }
            null
        }
        AiDebug.trackObject("sample.session", SampleState)
    }

    private fun ensureSampleStorage() {
        val flags = getSharedPreferences("sample_flags", MODE_PRIVATE)
        if (!flags.contains("newCheckout")) {
            flags.edit().putBoolean("newCheckout", false).apply()
        }
        openOrCreateDatabase("sample.db", MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS user_profile (id TEXT PRIMARY KEY, vip INTEGER NOT NULL, name TEXT NOT NULL)")
            db.execSQL("INSERT OR IGNORE INTO user_profile(id, vip, name) VALUES('current', 0, 'Sample User')")
        }
    }

    private fun isNewCheckoutEnabled(): Boolean {
        return AiDebug.overrides().featureFlag("newCheckout") {
            getSharedPreferences("sample_flags", MODE_PRIVATE).getBoolean("newCheckout", false)
        }
    }

    private fun renderLocalState(): String {
        val dbVip = openOrCreateDatabase("sample.db", MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT vip FROM user_profile WHERE id = ?", arrayOf("current")).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) == 1 else false
            }
        }
        return "Local state: user.isVip=${SampleState.isVip}, feature.newCheckout=${isNewCheckoutEnabled()}, db.vip=$dbVip"
    }

    private fun refreshLocalState() {
        stateView?.text = renderLocalState()
    }

    private fun runAudioMediaDogfood(renderMediaState: Boolean) {
        resultView?.text = "Running audio media dogfood..."
        thread(name = "sample-audio-media-dogfood") {
            val text = runCatching { executeAudioMediaDogfood() }
                .getOrElse { error -> "Audio media dogfood failed: ${error.message ?: error::class.java.simpleName}" }
            runOnUiThread {
                resultView?.text = text
                if (renderMediaState) refreshLocalState()
            }
        }
    }

    private fun runCameraMediaDogfood(renderMediaState: Boolean) {
        resultView?.text = "Running camera media dogfood..."
        thread(name = "sample-camera-media-dogfood") {
            val text = runCatching { executeCameraMediaDogfood() }
                .getOrElse { error -> "Camera media dogfood failed: ${error.message ?: error::class.java.simpleName}" }
            runOnUiThread {
                resultView?.text = text
                if (renderMediaState) refreshLocalState()
            }
        }
    }

    private fun executeAudioMediaDogfood(): String {
        val sampleRate = 8_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).takeIf { it > 0 } ?: 512
        val buffer = ByteArray(512)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer.coerceAtLeast(buffer.size),
        )
        return try {
            val read = sampleAudioRecordReadForMediaHook(record, buffer)
            val checksum = buffer.copyOf(read.coerceAtLeast(0).coerceAtMost(buffer.size))
                .fold(0) { acc, byte -> (acc + (byte.toInt() and 0xff)) % 65_535 }
            "Audio media dogfood: read=$read checksum=$checksum"
        } finally {
            runCatching { record.release() }
        }
    }

    private fun executeCameraMediaDogfood(): String {
        val width = 4
        val height = 4
        val nv21 = ByteArray(width * height * 3 / 2) { index ->
            if (index < width * height) (32 + index).toByte() else 128.toByte()
        }
        val image = sampleInputImageFactoryForMediaHook(nv21, width, height)
        return "Camera media dogfood: ${image.summary()}"
    }

    @Suppress("unused")
    private fun sampleAudioRecordReadForMediaHook(record: AudioRecord, buffer: ByteArray): Int {
        runCatching { record.startRecording() }
        val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
        runCatching { record.stop() }
        return read
    }

    @Suppress("unused")
    private fun sampleInputImageFactoryForMediaHook(bytes: ByteArray, width: Int, height: Int): InputImage {
        return InputImage.fromByteArray(
            bytes,
            width,
            height,
            0,
            InputImage.IMAGE_FORMAT_NV21,
        )
    }
}

private val SampleState = SampleSession()

object AnnotatedDebugCapabilities {
    @AiState(
        path = "sample.annotatedVip",
        type = "boolean",
        description = "Generated sample VIP state",
    )
    var annotatedVip: Boolean = false

    @AiAction(
        path = "sample.generatedAction",
        description = "Generated no-op sample action",
    )
    fun generatedAction() = Unit
}

class SampleSession {
    @AiProbe(id = "sample.session.isVip", description = "Searchable VIP field")
    @Volatile
    var isVip: Boolean = false

    override fun toString(): String {
        return "SampleSession(isVip=$isVip)"
    }
}

package com.beykus.beygram.data.media

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Status keys saved in a report. The settings screen turns them into localized labels. */
internal object VideoNoteStatus {
    const val QUEUED = "queued"
    const val PREVIEW = "preview"
    const val READY = "ready"
    const val PROCESSING_FAILED = "processing_failed"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val SEND_FAILED = "send_failed"
    const val CANCELLED = "cancelled"
}

/**
 * On-device, opt-in video-note diagnostics. Never store media, message contents, chat IDs,
 * phone numbers or file paths. Reports live in noBackupFilesDir and are shared only by user action.
 */
data class VideoNoteDiagnosticReport(
    val id: String,
    val startedAt: Long,
    val status: String,
    val targetFps: Int,
    val entries: List<String>,
    val rawFps: Double? = null,
    val outputFps: Double? = null
) {
    fun asText(): String = buildString {
        appendLine("BeyGram — диагностика видеосообщения")
        appendLine("Сессия: $id")
        appendLine("Время: ${Date(startedAt)}")
        appendLine("Состояние: $status")
        appendLine("Целевой FPS: $targetFps")
        appendLine("RAW FPS: ${rawFps?.let { String.format(Locale.US, "%.2f", it) } ?: "не измерен"}")
        appendLine("Итоговый FPS: ${outputFps?.let { String.format(Locale.US, "%.2f", it) } ?: "не измерен"}")
        appendLine()
        entries.forEach { appendLine(it) }
    }
}

object VideoNoteDiagnostics {
    private const val TAG = "VideoNoteDiagnostics"
    private const val MAX_REPORTS = 20
    private const val MAX_ENTRIES = 70
    private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    private const val FILE_NAME = "video-note-diagnostics.json"

    private val mutableReports = MutableStateFlow<List<VideoNoteDiagnosticReport>>(emptyList())
    val reports: StateFlow<List<VideoNoteDiagnosticReport>> = mutableReports.asStateFlow()
    private var file: AtomicFile? = null

    @Synchronized
    fun initialize(context: Context) {
        if (file != null) return
        file = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE_NAME))
        val saved = runCatching {
            val storedFile = file ?: return@runCatching emptyList()
            if (!storedFile.baseFile.exists()) return@runCatching emptyList()
            val list = JSONArray(storedFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            (0 until list.length()).mapNotNull { i ->
                val json = list.optJSONObject(i) ?: return@mapNotNull null
                val rows = json.optJSONArray("entries") ?: JSONArray()
                VideoNoteDiagnosticReport(
                    id = json.getString("id"),
                    startedAt = json.getLong("startedAt"),
                    status = json.optString("status", "unknown"),
                    targetFps = json.optInt("targetFps", 30),
                    entries = (0 until rows.length()).map { rows.optString(it) },
                    rawFps = if (json.has("rawFps")) json.getDouble("rawFps") else null,
                    outputFps = if (json.has("outputFps")) json.getDouble("outputFps") else null
                )
            }
        }.onFailure { Log.w(TAG, "Cannot read stored video diagnostics: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
        val retained = trim(saved)
        mutableReports.value = retained
        // Expired reports are removed from disk as soon as diagnostics are opened again.
        if (retained.size != saved.size) persist(retained)
    }

    @Synchronized
    fun begin(
        context: Context,
        sessionId: String,
        settings: VideoMessageSettings,
        lens: String,
        selectedRange: String,
        supportedRanges: String,
        captureBitrate: Int
    ) {
        if (!settings.diagnosticsEnabled) return
        initialize(context)
        val now = System.currentTimeMillis()
        val start = listOf(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android SDK ${Build.VERSION.SDK_INT}",
            "Preset: ${settings.preset}, requestedFPS: ${settings.requestedFrameRateRange()}",
            "Lens: $lens, selectedCameraRange: $selectedRange",
            "AdvertisedCameraRanges: $supportedRanges",
            "Capture target bitrate: $captureBitrate bit/s, mirrorFront: ${settings.mirrorFrontCamera}",
            "Start recording requested"
        ).map { stamp(it) }
        val report = VideoNoteDiagnosticReport(
            id = sessionId,
            startedAt = now,
            status = "Запись",
            targetFps = settings.requestedFrameRateRange().last,
            entries = start
        )
        persist(listOf(report) + mutableReports.value.filterNot { it.id == sessionId })
    }

    @Synchronized
    fun hasReport(id: String): Boolean = mutableReports.value.any { it.id == id }

    @Synchronized
    fun append(id: String, message: String, status: String? = null) {
        if (!hasReport(id)) return
        modify(id) { old ->
            old.copy(status = status ?: old.status, entries = (old.entries + stamp(message)).takeLast(MAX_ENTRIES))
        }
    }

    suspend fun inspect(id: String, stage: String, file: File, output: Boolean = false) {
        if (!hasReport(id)) return
        // Run after the camera/Transformer has finalized the MP4; only encoded sample PTS are read.
        val result = withContext(Dispatchers.IO) { runCatching { analyzeVideoNoteFrameSamples(file) } }
        result.fold(
            onSuccess = { data ->
                modify(id) { old ->
                    val raw = if (output) old.rawFps else data.measuredFps
                    val final = if (output) data.measuredFps else old.outputFps
                    val finding = if (output) videoNoteFpsFinding(old.targetFps, raw, final) else null
                    old.copy(
                        rawFps = raw,
                        outputFps = final,
                        entries = (old.entries + stamp("$stage: ${data.summary()}") +
                            listOfNotNull(finding?.let { stamp("FPS finding: $it") })).takeLast(MAX_ENTRIES)
                    )
                }
            },
            onFailure = { append(id, "$stage: не удалось измерить FPS (${it.javaClass.simpleName})") }
        )
    }

    @Synchronized
    fun clear() {
        file?.delete()
        mutableReports.value = emptyList()
    }

    @Synchronized
    private fun modify(id: String, change: (VideoNoteDiagnosticReport) -> VideoNoteDiagnosticReport) {
        val current = mutableReports.value
        if (current.none { it.id == id }) return
        persist(current.map { if (it.id == id) change(it) else it })
    }

    @Synchronized
    private fun persist(current: List<VideoNoteDiagnosticReport>) {
        val bounded = trim(current)
        mutableReports.value = bounded
        val target = file ?: return
        runCatching {
            val array = JSONArray()
            bounded.forEach { report ->
                array.put(JSONObject().apply {
                    put("id", report.id)
                    put("startedAt", report.startedAt)
                    put("status", report.status)
                    put("targetFps", report.targetFps)
                    put("entries", JSONArray(report.entries))
                    report.rawFps?.let { put("rawFps", it) }
                    report.outputFps?.let { put("outputFps", it) }
                })
            }
            val bytes = array.toString().toByteArray(Charsets.UTF_8)
            val stream = target.startWrite()
            try {
                stream.write(bytes)
                target.finishWrite(stream)
            } catch (e: Exception) {
                target.failWrite(stream)
                throw e
            }
        }.onFailure { Log.w(TAG, "Cannot save video diagnostics: ${it.javaClass.simpleName}") }
    }

    private fun trim(reports: List<VideoNoteDiagnosticReport>): List<VideoNoteDiagnosticReport> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return reports.filter { it.startedAt >= cutoff }.sortedByDescending { it.startedAt }.take(MAX_REPORTS)
    }

    private fun stamp(message: String): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        return "[$time] $message"
    }
}

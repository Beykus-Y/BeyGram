package com.beykus.beygram.data.vpn

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.beykus.beygram.domain.model.MihomoConnection
import com.beykus.beygram.domain.model.MihomoProbeResult
import com.beykus.beygram.domain.model.VpnSettings
import com.beykus.beygram.BuildConfig
import io.github.oviron.libmihomo.Clash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/** In-process JNI bridge to the pinned Mihomo core. No external controller socket is opened. */
internal class MihomoCoreBridge(context: Context) {
    private val appContext = context.applicationContext
    private val home = File(appContext.noBackupFilesDir, "mihomo").apply { mkdirs() }
    private val profileFile = File(home, "config.yaml")
    private val profile = AtomicFile(profileFile)
    private val normalizer = MihomoProfileNormalizer()

    suspend fun importSubscription(configuration: String): String = coreMutex.withLock { withContext(Dispatchers.IO) {
        loadNativeCore()
        val normalized = normalizer.normalize(configuration)
        val pendingFile = File(home, "config-validation.yaml")
        val pending = AtomicFile(pendingFile)
        writeAtomically(pending, normalized.yaml.toByteArray(Charsets.UTF_8))
        try {
            val validation = invokeAction("validateConfig", pendingFile.absolutePath) as? String
            if (!validation.isNullOrBlank()) throw SubscriptionException("Mihomo rejected the subscription: $validation")

            val previous = runCatching { profile.readFully() }.getOrNull()
            writeAtomically(profile, normalized.yaml.toByteArray(Charsets.UTF_8))
            try {
                ensureInitializedUnlocked()
            } catch (error: Exception) {
                if (previous != null) {
                    writeAtomically(profile, previous)
                    runCatching { reloadProfile() }
                } else {
                    profileFile.delete()
                }
                throw error
            }
            normalized.connectionGroup
        } finally {
            pendingFile.delete()
        }
    } }

    suspend fun ensureInitialized() = coreMutex.withLock { withContext(Dispatchers.IO) { ensureInitializedUnlocked() } }

    private suspend fun ensureInitializedUnlocked() {
        loadNativeCore()
        normalizeStoredProfileForCurrentRuntime()
        val initialized = invokeAction("getIsInit") as? Boolean ?: false
        if (initialized) {
            reloadProfile()
        } else {
            val init = JSONObject()
                .put("home-dir", home.absolutePath)
                .put("version", Build.VERSION.SDK_INT)
            val result = quickSetup(init.toString(), "{\"selected-map\":{}}")
            if (result.isNotBlank()) throw MihomoCoreException("Mihomo could not load the profile: $result")
        }
        startNativeDiagnostics()
    }

    /** Re-normalize a profile saved by an older TUN build before applying it to proxy mode. */
    private fun normalizeStoredProfileForCurrentRuntime() {
        if (!profileExists()) return
        val previous = profile.readFully()
        val normalized = normalizer.normalize(previous.toString(Charsets.UTF_8)).yaml.toByteArray(Charsets.UTF_8)
        if (previous.contentEquals(normalized)) return
        writeAtomically(profile, normalized)
        MihomoRuntimeDiagnostics.event("profile", "stored profile migrated to app-local SOCKS configuration")
    }

    suspend fun listConnections(groupName: String): List<MihomoConnection> = withContext(Dispatchers.IO) {
        val root = JSONObject(invokeAction("getProxies") as? String ?: throw MihomoCoreException("Mihomo returned an empty proxy list"))
        val proxies = root.optJSONObject("proxies") ?: throw MihomoCoreException("Mihomo returned an invalid proxy list")
        val group = proxies.optJSONObject(groupName) ?: throw MihomoCoreException("Configured Mihomo group was not found")
        val groupType = group.optString("type").lowercase()
        if (groupType !in SELECTABLE_GROUP_TYPES) throw MihomoCoreException("Configured Mihomo item is not a selectable group")

        val names = group.optJSONArray("all") ?: JSONArray()
        val selectedName = group.optString("now")
        buildList {
            for (index in 0 until names.length()) {
                val name = names.optString(index).takeIf(String::isNotBlank) ?: continue
                val proxy = proxies.optJSONObject(name) ?: continue
                val type = proxy.optString("type", "Unknown")
                if (type.lowercase() in NON_CONNECTION_TYPES || type.lowercase() in SELECTABLE_GROUP_TYPES) continue
                val history = proxy.optJSONArray("history")
                val delay = history?.takeIf { it.length() > 0 }?.optJSONObject(history.length() - 1)
                    ?.optLong("delay")?.takeIf { it > 0L }
                add(MihomoConnection(name, type, delay, selected = name == selectedName))
            }
        }
    }

    suspend fun checkTelegram(connectionName: String, timeoutMillis: Int = DEFAULT_PROBE_TIMEOUT_MS): MihomoProbeResult =
        withContext(Dispatchers.IO) {
            require(connectionName.isNotBlank())
            require(timeoutMillis in MIN_PROBE_TIMEOUT_MS..MAX_PROBE_TIMEOUT_MS)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            MihomoRuntimeDiagnostics.event("probe", "started node=${safeNodeLabel(connectionName)} timeoutMs=$timeoutMillis")
            val data = JSONObject()
                .put("proxy-name", connectionName)
                .put("test-url", VpnSettings.TELEGRAM_HEALTH_CHECK_URL)
                .put("timeout", timeoutMillis)
            try {
                // libmihomo-android returns testDelay's result as JSON text, not a Number.
                val raw = invokeAction("testDelay", data.toString())
                val delay = when (raw) {
                    is Number -> raw.toLong() // retain compatibility with wrappers returning a numeric value
                    is String -> JSONObject(raw).let { result -> result.optLong("value", result.optLong("Value", -1L)) }
                    else -> -1L
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                MihomoRuntimeDiagnostics.event(
                    "probe",
                    "completed node=${safeNodeLabel(connectionName)} result=${if (delay >= 0) "reachable" else "unreachable"} delayMs=${delay.takeIf { it >= 0 } ?: -1} elapsedMs=$elapsed"
                )
                MihomoProbeResult(
                    connectionName,
                    telegramReachable = delay >= 0,
                    delayMillis = delay.takeIf { it >= 0 },
                    checkedAtMillis = System.currentTimeMillis(),
                    error = if (delay < 0) "Telegram test did not complete" else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                MihomoRuntimeDiagnostics.event(
                    "probe",
                    "failed node=${safeNodeLabel(connectionName)} error=${error.javaClass.simpleName} elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
                )
                MihomoProbeResult(connectionName, false, null, System.currentTimeMillis(), error.message ?: "Probe failed")
            }
        }

    private suspend fun startNativeDiagnostics() {
        if (!BuildConfig.DEBUG || BuildConfig.FLAVOR != "dev") return
        runCatching {
            Clash.setEventListener { raw -> MihomoCoreLogCapture.onMessage(raw.orEmpty()) }
            invokeAction("startLog")
            MihomoRuntimeDiagnostics.event("core log", "warning/error stream enabled")
        }.onFailure { error ->
            MihomoRuntimeDiagnostics.event("core log", "stream unavailable error=${error.javaClass.simpleName}")
        }
    }

    private fun safeNodeLabel(value: String): String = value
        .replace(Regex("(?i)https?://\\S+"), "[url]")
        .replace(Regex("(?i)(?:\\b(?:\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-f:]+]|[a-z0-9.-]+\\.[a-z]{2,})(?::\\d{1,5})?"), "[endpoint]")
        .replace(Regex("(?i)\\b(password|passwd|token|secret|uuid)\\s*[:=]\\s*[^\\s,;]+"), "$1=[hidden]")
        .filterNot(Char::isISOControl)
        .take(48)
        .ifBlank { "unnamed" }

    suspend fun selectConnection(groupName: String, connectionName: String) = withContext(Dispatchers.IO) {
        val data = JSONObject().put("group-name", groupName).put("proxy-name", connectionName).toString()
        invokeAction("changeProxy", data)
        invokeAction("resetConnections")
        Unit
    }

    fun profileExists(): Boolean = profileFile.isFile && profileFile.length() > 0L

    suspend fun startProxyListener() = withContext(Dispatchers.IO) {
        val started = invokeAction("startListener") as? Boolean ?: false
        MihomoRuntimeDiagnostics.event("listener", "startListener returned=$started")
        if (!started) throw MihomoCoreException("Mihomo did not start its local SOCKS listener")
        MihomoProxyClient.awaitReady()
        MihomoRuntimeDiagnostics.event("listener", "SOCKS port is accepting loopback connections")
    }

    suspend fun stopProxyListener() = withContext(Dispatchers.IO) {
        if (Clash.isLoaded()) invokeAction("stopListener")
        Unit
    }

    private suspend fun reloadProfile() {
        check(profileExists()) { "No local Mihomo profile is installed" }
        val params = JSONObject().put("selected-map", JSONObject()).toString()
        val result = invokeAction("setupConfig", params) as? String
        if (!result.isNullOrBlank()) throw MihomoCoreException("Mihomo could not apply the profile: $result")
    }

    private fun loadNativeCore() {
        if (!Clash.isLoaded()) Clash.load(appContext.applicationInfo.nativeLibraryDir)
        val abi = Clash.bridgeABI()
        check(abi == Clash.EXPECTED_BRIDGE_ABI) {
            "Mihomo bridge ABI mismatch: expected ${Clash.EXPECTED_BRIDGE_ABI}, received $abi"
        }
    }

    private suspend fun quickSetup(initJson: String, setupJson: String): String =
        suspendCancellableCoroutine { continuation ->
            try {
                Clash.quickSetup(initJson, setupJson) { result ->
                    if (continuation.isActive) continuation.resume(result.orEmpty())
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resume("${error.javaClass.simpleName}: ${error.message.orEmpty()}")
            }
        }

    private suspend fun invokeAction(method: String, data: Any? = null): Any? =
        suspendCancellableCoroutine { continuation ->
            val action = JSONObject().put("id", UUID.randomUUID().toString()).put("method", method)
            if (data != null) action.put("data", data)
            try {
                Clash.invokeAction(action.toString()) callback@{ raw ->
                    if (!continuation.isActive) return@callback
                    try {
                        val result = JSONObject(raw.orEmpty())
                        val code = result.optInt("code", -1)
                        val value = result.opt("data")
                        if (code != 0) {
                            continuation.resumeWith(Result.failure(MihomoCoreException(value?.toString() ?: "Mihomo action failed")))
                        } else {
                            continuation.resume(value)
                        }
                    } catch (error: Throwable) {
                        continuation.resumeWith(Result.failure(MihomoCoreException("Mihomo returned an invalid action response")))
                    }
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }

    private fun writeAtomically(file: AtomicFile, bytes: ByteArray) {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(file::failWrite)
            throw error
        }
    }

    companion object {
        private const val DEFAULT_PROBE_TIMEOUT_MS = 5_000
        private const val MIN_PROBE_TIMEOUT_MS = 1_000
        private const val MAX_PROBE_TIMEOUT_MS = 15_000
        private val SELECTABLE_GROUP_TYPES = setOf("select", "selector", "urltest", "fallback", "loadbalance", "relay")
        private val NON_CONNECTION_TYPES = setOf("direct", "reject", "reject-drop", "pass", "compatible", "global", "block")

        private val coreMutex = Mutex()
    }
}

class MihomoCoreException(message: String) : Exception(message)

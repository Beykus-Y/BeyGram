package com.beykus.beygram.data.update

import android.content.Context
import org.json.JSONObject

/** A verified APK file name of the channel folder, with its release. */
internal data class StoredApk(val release: StableRelease, val name: String)

private const val PREFS = "beygram_updates"
private const val KEY_CHECKED_AT = "lastCheckedAt"
private const val KEY_KNOWN = "knownRelease"
private const val KEY_READY = "ready"
private const val KEY_HANDOFF = "handoff"
private const val KEY_PARTIAL = "partial"

/**
 * Small persisted state of the updater. A value that cannot be read is treated as absent, so a damaged entry
 * never produces a trusted file. File names are relative to the channel folder.
 */
internal class UpdateStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheckedAt(): Long? = if (prefs.contains(KEY_CHECKED_AT)) prefs.getLong(KEY_CHECKED_AT, 0L) else null

    fun saveLastCheckedAt(millis: Long) {
        prefs.edit().putLong(KEY_CHECKED_AT, millis).apply()
    }

    fun knownRelease(): StableRelease? = parse(KEY_KNOWN) { releaseFrom(it) }

    fun saveKnownRelease(release: StableRelease?) = save(KEY_KNOWN, release?.let { releaseJson(it) })

    fun ready(): StoredApk? = parse(KEY_READY) { storedFrom(it) }

    fun saveReady(apk: StoredApk?) = save(KEY_READY, apk?.let { storedJson(it) })

    /** The APK that the Android installer was given. It is kept on disk until it is replaced by another handoff. */
    fun handoff(): StoredApk? = parse(KEY_HANDOFF) { storedFrom(it) }

    fun saveHandoff(apk: StoredApk?) = save(KEY_HANDOFF, apk?.let { storedJson(it) })

    fun partial(): PartialDownload? = parse(KEY_PARTIAL) { partialFrom(it) }

    fun savePartial(partial: PartialDownload?) = save(KEY_PARTIAL, partial?.let { partialJson(it) })

    private fun <T> parse(key: String, read: (JSONObject) -> T): T? = try {
        prefs.getString(key, null)?.let { read(JSONObject(it)) }
    } catch (error: Exception) {
        null
    }

    private fun save(key: String, json: JSONObject?) {
        val editor = prefs.edit()
        if (json == null) editor.remove(key) else editor.putString(key, json.toString())
        editor.apply()
    }
}

private fun releaseJson(release: StableRelease): JSONObject = JSONObject()
    .put("tag", release.tag)
    .put("versionName", release.versionName)
    .put("versionCode", release.versionCode)
    .put("apkName", release.apkName)
    .put("size", release.size)
    .put("sha256", release.sha256)
    .put("notes", release.notes)
    .put("publishedAt", release.publishedAt)

private fun releaseFrom(json: JSONObject): StableRelease = StableRelease(
    tag = json.getString("tag"),
    versionName = json.getString("versionName"),
    versionCode = json.getLong("versionCode"),
    apkName = json.getString("apkName"),
    size = json.getLong("size"),
    sha256 = json.getString("sha256"),
    notes = json.optString("notes", ""),
    publishedAt = json.optString("publishedAt", ""),
)

private fun storedJson(apk: StoredApk): JSONObject = JSONObject()
    .put("release", releaseJson(apk.release))
    .put("name", apk.name)

private fun storedFrom(json: JSONObject): StoredApk {
    val name = json.getString("name")
    require(name.isNotEmpty() && !name.contains('/') && !name.contains('\\')) { "Недопустимое имя файла." }
    return StoredApk(releaseFrom(json.getJSONObject("release")), name)
}

private fun partialJson(partial: PartialDownload): JSONObject = JSONObject()
    .put("release", releaseJson(partial.release))
    .put("validator", partial.validator)

private fun partialFrom(json: JSONObject): PartialDownload =
    PartialDownload(releaseFrom(json.getJSONObject("release")), json.getString("validator"))

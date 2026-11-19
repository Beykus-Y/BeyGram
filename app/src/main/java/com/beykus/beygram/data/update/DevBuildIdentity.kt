package com.beykus.beygram.data.update

import org.json.JSONObject

/*
 * Pure DEV build identity and cumulative changelog. No Android types: everything here is unit-tested on the JVM.
 * Display metadata made here never decides an update; the update decision stays on versionCode in the repository.
 */

private const val REPO_URL = "https://github.com/Beykus-Y/BeyGram"
private const val MAX_BUILD_NUMBER = 10_000_000

private val devTagRegex = Regex("^dev-[0-9]+$")
private val fullShaRegex = Regex("^[0-9a-f]{40}$")
private val hexShaRegex = Regex("^[0-9a-f]{7,40}$")
private val devVersionNameRegex = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+-dev$")

/** One line of the range marker that CI writes at the end of every DEV release body. */
private const val RANGE_MARKER_PATTERN =
    "(?m)^<!-- beygram-dev-range from=(none|[0-9a-f]{40}) to=([0-9a-f]{40}) complete=(true|false) -->[ \\t\\r]*$"
private val rangeMarkerRegex = Regex(RANGE_MARKER_PATTERN)
private val rangeMarkerLineRegex = Regex(RANGE_MARKER_PATTERN + "\\n?")

/** What a DEV release says about itself. Every field is optional: null means unknown, never an error. */
data class DevReleaseInfo(
    val buildNumber: Int?,
    val commit: String?,
    val versionName: String?,
    val changes: DevChangeRange,
)

/** The DEV builds between the installed one and the offered one, newest first. */
data class DevChangeRange(
    val entries: List<DevChangeEntry>,
    val complete: Boolean,
    val truncated: Boolean,
    val compareUrl: String?,
)

data class DevChangeEntry(val buildNumber: Int, val tag: String, val notes: String)

/** The identity of the running build. Only [Ci] carries a build number that can be compared with releases. */
sealed interface InstalledBuild {
    data class Ci(val number: Int, val sha: String) : InstalledBuild
    data class UnknownNumber(val sha: String?) : InstalledBuild
    data object Local : InstalledBuild
}

/** Parsed `<!-- beygram-dev-range ... -->` marker. [from] is null when the previous build had no commit. */
data class RangeMarker(val from: String?, val to: String, val complete: Boolean)

/** Optional manifest fields after validation. Each invalid field is null. */
data class DevManifestExtras(val buildNumber: Int?, val versionName: String?, val commit: String?)

fun installedBuild(buildNumber: Int, gitSha: String): InstalledBuild {
    val validSha = gitSha.takeIf { hexShaRegex.matches(it) }
    if (buildNumber > 0 && validSha != null) return InstalledBuild.Ci(buildNumber, validSha)
    if (buildNumber <= 0 && (gitSha.isBlank() || gitSha == "local")) return InstalledBuild.Local
    return InstalledBuild.UnknownNumber(validSha)
}

/** The short commit of the installed build, or null when it is not known. */
fun InstalledBuild.knownSha(): String? = when (this) {
    is InstalledBuild.Ci -> sha.takeIf { hexShaRegex.matches(it) }
    is InstalledBuild.UnknownNumber -> sha?.takeIf { hexShaRegex.matches(it) }
    InstalledBuild.Local -> null
}

/** The number of a `dev-N` tag, or null for any other tag. */
internal fun devTagNumber(tag: String): Int? =
    if (devTagRegex.matches(tag)) tag.removePrefix("dev-").toIntOrNull() else null

internal fun isFullCommitSha(value: String?): Boolean = value != null && fullShaRegex.matches(value)

/**
 * Optional fields of manifest.json. buildNumber must equal N of the tag, versionName must look like a DEV name,
 * commit must be a full SHA. Anything else is dropped; a missing field is simply null.
 */
fun parseDevManifestExtras(manifest: JSONObject, tag: String): DevManifestExtras {
    val tagNumber = devTagNumber(tag)
    val buildNumber = (manifest.opt("buildNumber") as? Int)
        ?.takeIf { tagNumber != null && it in 1..MAX_BUILD_NUMBER && it == tagNumber }
    val versionName = manifest.optString("versionName", "").takeIf { devVersionNameRegex.matches(it) }
    val commit = manifest.optString("commit", "").takeIf { fullShaRegex.matches(it) }
    return DevManifestExtras(buildNumber, versionName, commit)
}

/** The range marker of a release body, or null when there is no marker or more than one. */
fun parseRangeMarker(body: String): RangeMarker? {
    val matches = rangeMarkerRegex.findAll(body).toList()
    if (matches.size != 1) return null
    val match = matches.single()
    return RangeMarker(
        from = match.groupValues[1].takeIf { it != "none" },
        to = match.groupValues[2],
        complete = match.groupValues[3] == "true",
    )
}

/**
 * The changes between [installed] and the release [targetTag], built from the fetched releases.
 *
 * The list is complete only when the installed build is a CI build, every included release carries a complete
 * marker, each marker's `from` continues the chain (the oldest one starts at the installed commit, the others at the
 * commit of the previous published DEV build), and the oldest fetched DEV release is not above the installed build.
 */
fun buildCumulativeChanges(
    releases: List<JSONObject>,
    installed: InstalledBuild,
    targetTag: String,
    maxEntries: Int = 30,
    maxChars: Int = 15_000,
): DevChangeRange {
    val targetNumber = devTagNumber(targetTag)
    // Published DEV prereleases, one per tag, oldest first.
    val published = releases.filter(::isDevPrerelease)
        .mapNotNull { json -> devTagNumber(json.optString("tag_name"))?.let { number -> number to json } }
        .distinctBy { it.first }
        .sortedBy { it.first }
    val target = published.firstOrNull { it.first == targetNumber }?.second
    if (targetNumber == null || target == null) {
        return DevChangeRange(entries = emptyList(), complete = false, truncated = false, compareUrl = null)
    }
    val targetCommit = commitOrNull(target.optString("target_commitish"))
        ?: parseRangeMarker(target.optString("body", ""))?.to

    val ci = installed as? InstalledBuild.Ci
    val included: List<Pair<Int, JSONObject>> = if (ci == null) {
        listOf(targetNumber to target)
    } else {
        published.filter { (number, _) -> number > ci.number && number <= targetNumber }
    }
    val continues = ci != null && chainHolds(published, included, ci)

    // Newest first; the budget keeps the newest entries and cuts the notes of the last one that does not fit.
    val entries = ArrayList<DevChangeEntry>()
    var used = 0
    var truncated = false
    for ((number, json) in included.asReversed()) {
        if (entries.size >= maxEntries) {
            truncated = true
            break
        }
        val notes = withoutRangeMarker(json.optString("body", ""))
        val room = maxChars - used
        if (room <= 0) {
            truncated = true
            break
        }
        val fitted = if (notes.length > room) {
            truncated = true
            notes.take(room).let { if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it }
        } else {
            notes
        }
        entries += DevChangeEntry(number, json.optString("tag_name"), fitted)
        used += fitted.length
        if (truncated) break
    }

    val installedSha = ci?.sha ?: (installed as? InstalledBuild.UnknownNumber)?.sha
    val compareUrl = installedSha?.takeIf { hexShaRegex.matches(it) }?.let { base ->
        targetCommit?.takeIf { isFullCommitSha(it) }?.let { head -> "$REPO_URL/compare/$base...$head" }
    }
    return DevChangeRange(
        entries = entries,
        complete = continues && !truncated,
        truncated = truncated,
        compareUrl = compareUrl,
    )
}

/** True when the markers of [included] (oldest first) form an unbroken chain starting at the installed build. */
private fun chainHolds(
    published: List<Pair<Int, JSONObject>>,
    included: List<Pair<Int, JSONObject>>,
    installed: InstalledBuild.Ci,
): Boolean {
    if (included.isEmpty()) return false
    // Releases above the installed build may be missing from the fetched pages when the oldest fetched one is higher.
    if (published.first().first > installed.number) return false
    included.forEachIndexed { index, (number, json) ->
        val marker = parseRangeMarker(json.optString("body", "")) ?: return false
        if (!marker.complete) return false
        val from = marker.from ?: return false
        if (index == 0) {
            if (!from.startsWith(installed.sha)) return false
        } else {
            val previous = published.lastOrNull { it.first < number }?.second ?: return false
            if (from != commitOrNull(previous.optString("target_commitish"))) return false
        }
    }
    return true
}

private fun commitOrNull(value: String): String? = value.takeIf { fullShaRegex.matches(it) }

private fun withoutRangeMarker(body: String): String = rangeMarkerLineRegex.replace(body, "").trim()

/** Plain text for the clipboard in bug reports. Technical keys on purpose; no secrets and no personal data. */
fun devBuildInfoText(
    packageName: String,
    versionName: String,
    versionCode: Long,
    installed: InstalledBuild,
    availableBuild: Int? = null,
    availableCommit: String? = null,
): String {
    val build = when (installed) {
        is InstalledBuild.Ci -> installed.number.toString()
        is InstalledBuild.UnknownNumber -> "unknown"
        InstalledBuild.Local -> "local"
    }
    return buildString {
        appendLine("BeyGram DEV")
        appendLine("package: $packageName")
        appendLine("versionName: $versionName")
        appendLine("versionCode: $versionCode")
        appendLine("build: $build")
        appendLine("commit: ${installed.knownSha() ?: "unknown"}")
        availableBuild?.let { appendLine("available build: $it") }
        availableCommit?.let { appendLine("available commit: $it") }
    }.trimEnd()
}

/** A GitHub commit page for a hex SHA (7 to 40 digits), or null. */
fun commitUrl(sha: String?): String? =
    sha?.takeIf { hexShaRegex.matches(it) }?.let { "$REPO_URL/commit/$it" }

/** The GitHub page of a DEV prerelease, or null for any tag that is not `dev-N`. */
fun devReleaseUrl(tag: String): String? =
    tag.takeIf { devTagRegex.matches(it) }?.let { "$REPO_URL/releases/tag/$it" }

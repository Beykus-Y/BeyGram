package com.beykus.beygram.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class DevBuildIdentityTest {
    private val repo = "https://github.com/Beykus-Y/BeyGram"

    /** A fake but well-formed 40-hex commit for build [n]. */
    private fun shaOf(n: Int): String =
        MessageDigest.getInstance("SHA-1").digest("build-$n".toByteArray()).joinToString("") { "%02x".format(it) }

    /** A published DEV prerelease dev-[n]. [from] is the marker's from value; null means no marker at all. */
    private fun release(
        n: Int,
        from: String? = "none",
        complete: Boolean = true,
        draft: Boolean = false,
        prerelease: Boolean = true,
        commit: String = shaOf(n),
    ): JSONObject {
        val marker = from?.let {
            "\n<!-- beygram-dev-range from=$it to=$commit complete=$complete -->"
        }.orEmpty()
        return JSONObject()
            .put("tag_name", "dev-$n")
            .put("prerelease", prerelease)
            .put("draft", draft)
            .put("target_commitish", commit)
            .put("body", "Build $n.\n\n- change $n$marker")
    }

    /** dev-[n] whose marker continues the chain from dev-[previous]. */
    private fun link(n: Int, previous: Int): JSONObject = release(n, from = shaOf(previous))

    private val installed10 = InstalledBuild.Ci(10, shaOf(10).take(12))

    @Test
    fun sameBaseVersionWithDifferentRunNumbersAreDifferentBuilds() {
        val sha = shaOf(10).take(12)
        assertEquals(InstalledBuild.Ci(10, sha), installedBuild(10, sha))
        assertNotEquals(installedBuild(10, sha), installedBuild(11, sha))
    }

    @Test
    fun installedBuildKinds() {
        assertEquals(InstalledBuild.Ci(45, "abc123def456"), installedBuild(45, "abc123def456"))
        assertEquals(InstalledBuild.Local, installedBuild(-1, "local"))
        assertEquals(InstalledBuild.Local, installedBuild(-1, ""))
        assertEquals(InstalledBuild.UnknownNumber("abc1234"), installedBuild(0, "abc1234"))
        assertEquals(InstalledBuild.UnknownNumber(null), installedBuild(-1, "XYZ"))
        assertEquals(InstalledBuild.UnknownNumber(null), installedBuild(45, "local"))
    }

    @Test
    fun manifestExtrasAcceptValidFields() {
        val manifest = JSONObject()
            .put("buildNumber", 12)
            .put("versionName", "0.0.6.1-dev")
            .put("commit", shaOf(12))
        assertEquals(DevManifestExtras(12, "0.0.6.1-dev", shaOf(12)), parseDevManifestExtras(manifest, "dev-12"))
    }

    @Test
    fun manifestExtrasDropInvalidFieldsWithoutFailing() {
        val manifest = JSONObject()
            .put("buildNumber", 12)
            .put("versionName", "0.0.6.1-dev")
            .put("commit", shaOf(12))
        // A build number that does not match the tag is dropped; the other fields are kept.
        assertEquals(DevManifestExtras(null, "0.0.6.1-dev", shaOf(12)), parseDevManifestExtras(manifest, "dev-13"))
        assertEquals(DevManifestExtras(null, null, null), parseDevManifestExtras(JSONObject(), "dev-12"))
        assertEquals(
            DevManifestExtras(null, null, null),
            parseDevManifestExtras(
                JSONObject().put("buildNumber", "12").put("versionName", "0.0.6.1").put("commit", "ABC"),
                "dev-12",
            ),
        )
        assertEquals(
            DevManifestExtras(null, null, null),
            parseDevManifestExtras(JSONObject().put("buildNumber", 10_000_001).put("commit", shaOf(1).uppercase()), "dev-10000001"),
        )
        assertEquals(
            DevManifestExtras(null, null, null),
            parseDevManifestExtras(JSONObject().put("buildNumber", 0), "dev-0"),
        )
    }

    @Test
    fun rangeMarkerIsParsedOrRejected() {
        val body = "Notes\n- a\n<!-- beygram-dev-range from=${shaOf(9)} to=${shaOf(10)} complete=true -->\n"
        assertEquals(RangeMarker(shaOf(9), shaOf(10), true), parseRangeMarker(body))
        val none = "<!-- beygram-dev-range from=none to=${shaOf(1)} complete=false -->"
        assertEquals(RangeMarker(null, shaOf(1), false), parseRangeMarker(none))
        assertNull(parseRangeMarker("no marker here"))
        assertNull(parseRangeMarker("<!-- beygram-dev-range from=none to=${shaOf(1)} complete=yes -->"))
        assertNull(parseRangeMarker("$none\n$none"))
    }

    @Test
    fun completeChainListsEveryBuildNewestFirst() {
        val releases = listOf(link(9, 8), link(10, 9), link(11, 10), link(12, 11), link(13, 12))
        val range = buildCumulativeChanges(releases, installed10, "dev-13")
        assertEquals(listOf(13, 12, 11), range.entries.map { it.buildNumber })
        assertTrue(range.complete)
        assertFalse(range.truncated)
        assertEquals("$repo/compare/${shaOf(10).take(12)}...${shaOf(13)}", range.compareUrl)
        // The marker is not part of the text shown to the reader.
        assertEquals("Build 13.\n\n- change 13", range.entries.first().notes)
    }

    @Test
    fun skippedNumbersAreFine() {
        val releases = listOf(link(9, 8), release(12, from = shaOf(10)), link(15, 12))
        val range = buildCumulativeChanges(releases, installed10, "dev-15")
        assertEquals(listOf(15, 12), range.entries.map { it.buildNumber })
        assertTrue(range.complete)
    }

    @Test
    fun missingMarkerFromOlderReleaseMakesListIncomplete() {
        val releases = listOf(
            link(9, 8),
            release(11, from = null),
            link(12, 11),
        )
        val range = buildCumulativeChanges(releases, installed10, "dev-12")
        assertEquals(listOf(12, 11), range.entries.map { it.buildNumber })
        assertFalse(range.complete)
    }

    @Test
    fun brokenChainMakesListIncomplete() {
        val releases = listOf(link(9, 8), link(11, 10), release(12, from = shaOf(99)))
        val range = buildCumulativeChanges(releases, installed10, "dev-12")
        assertFalse(range.complete)
        assertEquals(listOf(12, 11), range.entries.map { it.buildNumber })
    }

    @Test
    fun incompleteMarkerMakesListIncomplete() {
        val releases = listOf(link(9, 8), release(11, from = shaOf(10), complete = false))
        assertFalse(buildCumulativeChanges(releases, installed10, "dev-11").complete)
    }

    @Test
    fun unknownInstalledBuildShowsOnlyTheTarget() {
        val releases = listOf(link(9, 8), link(10, 9), link(11, 10), link(12, 11), link(13, 12))
        val local = buildCumulativeChanges(releases, InstalledBuild.Local, "dev-13")
        assertEquals(listOf(13), local.entries.map { it.buildNumber })
        assertFalse(local.complete)
        assertNull(local.compareUrl)

        val unknownNumber = buildCumulativeChanges(
            releases,
            InstalledBuild.UnknownNumber(shaOf(10).take(12)),
            "dev-13",
        )
        assertEquals(listOf(13), unknownNumber.entries.map { it.buildNumber })
        assertFalse(unknownNumber.complete)
        assertEquals("$repo/compare/${shaOf(10).take(12)}...${shaOf(13)}", unknownNumber.compareUrl)
    }

    @Test
    fun releasesAboveTheFetchedPagesMakeListIncomplete() {
        // The oldest fetched build (12) is above the installed build (10): builds in between may be missing.
        val releases = listOf(release(12, from = shaOf(10)), link(13, 12))
        val range = buildCumulativeChanges(releases, installed10, "dev-13")
        assertEquals(listOf(13, 12), range.entries.map { it.buildNumber })
        assertFalse(range.complete)
    }

    @Test
    fun truncationByCountAndCharacters() {
        val releases = listOf(link(11, 10), link(12, 11), link(13, 12), link(14, 13))
        val byCount = buildCumulativeChanges(releases, installed10, "dev-14", maxEntries = 2)
        assertEquals(listOf(14, 13), byCount.entries.map { it.buildNumber })
        assertTrue(byCount.truncated)
        assertFalse(byCount.complete)

        val byChars = buildCumulativeChanges(releases, installed10, "dev-14", maxChars = 30)
        assertEquals(2, byChars.entries.size)
        assertEquals(14, byChars.entries[0].buildNumber)
        assertTrue(byChars.entries[1].notes.length < "Build 13.\n\n- change 13".length)
        assertTrue(byChars.truncated)
        assertFalse(byChars.complete)
    }

    @Test
    fun duplicatedTagsAppearOnce() {
        val releases = listOf(link(9, 8), link(10, 9), link(11, 10), link(12, 11), link(12, 11), link(13, 12))
        val range = buildCumulativeChanges(releases, installed10, "dev-13")
        assertEquals(listOf(13, 12, 11), range.entries.map { it.buildNumber })
        assertTrue(range.complete)
    }

    @Test
    fun releasesOutsideTheRangeAreExcluded() {
        val releases = listOf(
            link(9, 8),
            link(10, 9),
            link(11, 10),
            link(12, 11),
            link(13, 12),
            link(14, 13),
            release(15, from = shaOf(14), draft = true),
            release(16, from = shaOf(15), prerelease = false),
        )
        val range = buildCumulativeChanges(releases, installed10, "dev-13")
        assertEquals(listOf(13, 12, 11), range.entries.map { it.buildNumber })
        assertTrue(range.complete)
    }

    @Test
    fun compareUrlNeedsAFullTargetCommit() {
        val releases = listOf(link(11, 10), release(12, from = shaOf(11), commit = "not-a-sha"))
        val range = buildCumulativeChanges(releases, installed10, "dev-12")
        assertNull(range.compareUrl)
    }

    @Test
    fun publicUrlHelpersAcceptOnlyValidInput() {
        assertEquals("$repo/commit/abc1234", commitUrl("abc1234"))
        assertNull(commitUrl("ABC1234"))
        assertNull(commitUrl("abc12"))
        assertNull(commitUrl(null))
        assertEquals("$repo/releases/tag/dev-12", devReleaseUrl("dev-12"))
        assertNull(devReleaseUrl("v0.0.6.1"))
        assertNull(devReleaseUrl("dev-12/../x"))
    }

    @Test
    fun buildInfoTextNamesTheBuildAndCommit() {
        val text = devBuildInfoText(
            packageName = "com.beykus.beygram.dev",
            versionName = "0.0.6.1-dev",
            versionCode = 600_000_045L,
            installed = InstalledBuild.Ci(45, "abc123def456"),
            availableBuild = 46,
            availableCommit = shaOf(46),
        )
        assertTrue(text.contains("versionName: 0.0.6.1-dev"))
        assertTrue(text.contains("versionCode: 600000045"))
        assertTrue(text.contains("build: 45"))
        assertTrue(text.contains("commit: abc123def456"))
        assertTrue(text.contains("available build: 46"))
        val local = devBuildInfoText("p", "0.0.6.1-dev", 1L, InstalledBuild.Local)
        assertTrue(local.contains("build: local"))
        assertTrue(local.contains("commit: unknown"))
        assertFalse(local.contains("available"))
    }
}

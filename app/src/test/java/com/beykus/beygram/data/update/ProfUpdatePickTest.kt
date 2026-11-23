package com.beykus.beygram.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfUpdatePickTest {
    private fun release(tag: String, prerelease: Boolean = true, draft: Boolean = false) =
        JSONObject().put("tag_name", tag).put("prerelease", prerelease).put("draft", draft)

    @Test fun latestProfWinsOnlyProfTags() {
        val listed = listOf(release("prof-2"), release("dev-999"), release("prof-12"),
            release("v1.0.0.0", prerelease = false), release("prof-9"))
        assertEquals("prof-12", pickLatestProfRelease(listed)?.getString("tag_name"))
    }

    @Test fun draftStableAndMalformedTagsIgnored() {
        assertNull(pickLatestProfRelease(listOf(release("prof-99", draft = true),
            release("prof-98", prerelease = false), release("dev-100"), release("prof-x"))))
    }

    @Test fun packageRoutingPreventsCrossChannelUpdates() {
        assertEquals(UpdateChannel.ProfBeta, updateChannelFor(PROF_PACKAGE))
        assertEquals(UpdateChannel.DevBeta, updateChannelFor(DEV_PACKAGE))
        assertEquals(UpdateChannel.Stable, updateChannelFor(StableUpdatePolicy.PACKAGE))
    }

    @Test fun profExtrasRequireMatchingTagAndVersionSuffix() {
        val manifest = JSONObject().put("buildNumber", 7).put("versionName", "0.0.6.1-prof")
            .put("commit", "a".repeat(40))
        assertEquals(7, parseProfManifestExtras(manifest, "prof-7").buildNumber)
        assertNull(parseProfManifestExtras(manifest, "dev-7").buildNumber)
        assertNull(parseProfManifestExtras(manifest.put("versionName", "0.0.6.1-dev"), "prof-7").versionName)
    }
}

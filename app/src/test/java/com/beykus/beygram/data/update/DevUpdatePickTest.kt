package com.beykus.beygram.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevUpdatePickTest {
    private fun release(tag: String, prerelease: Boolean = true, draft: Boolean = false) =
        JSONObject().put("tag_name", tag).put("prerelease", prerelease).put("draft", draft)

    @Test
    fun highestDevNumberWinsWhateverTheListOrder() {
        val listed = listOf(
            release("v0.0.2.1", prerelease = false),
            release("dev-99"), release("dev-98"), release("dev-92"),
            release("dev-112"), release("dev-111"), release("dev-103"),
        )
        assertEquals("dev-112", pickLatestDevRelease(listed)?.getString("tag_name"))
    }

    @Test
    fun draftsStableAndForeignTagsAreIgnored() {
        val listed = listOf(
            release("dev-200", draft = true),
            release("dev-150", prerelease = false),
            release("dev-x1"),
            release("dev-5"),
        )
        assertEquals("dev-5", pickLatestDevRelease(listed)?.getString("tag_name"))
        assertNull(pickLatestDevRelease(listOf(release("v0.0.2.1", prerelease = false))))
    }
}

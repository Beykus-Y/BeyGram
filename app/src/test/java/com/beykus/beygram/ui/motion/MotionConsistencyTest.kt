package com.beykus.beygram.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.designFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionConsistencyTest {
    @Test
    fun allTokensBecomeInstantUnderMotionOffAndReducedMotion() {
        val off = MotionSettings().withPreset(MotionPreset.Off)
        for (token in MotionToken.entries) {
            val offSpec = resolveMotion(token, off, reduced = false)
            assertTrue("${token.name} should be instant when Motion Off", offSpec.isInstant)
            assertEquals(0, offSpec.durationMillis)
            assertEquals(0f, offSpec.intensity, 0f)

            val reducedSpec = resolveMotion(token, MotionSettings(), reduced = true)
            assertTrue("${token.name} should be instant when reduced motion is on", reducedSpec.isInstant)
            assertEquals(0, reducedSpec.durationMillis)
            assertEquals(0f, reducedSpec.intensity, 0f)
        }
    }

    @Test
    fun eachCategoryDisablesOnlyItsOwnTokens() {
        for (category in MotionCategory.entries) {
            val settings = MotionSettings().withCategoryEnabled(category, enabled = false)
            for (token in MotionToken.entries) {
                val spec = resolveMotion(token, settings, reduced = false)
                if (token.category == category) {
                    assertTrue("${token.name} should be instant when $category is disabled", spec.isInstant)
                } else {
                    assertFalse("${token.name} should stay active when only $category is disabled", spec.isInstant)
                }
            }
        }
    }

    @Test
    fun fadeTransitionsUseNoneWhenInstantAcrossAllDesignStyles() {
        val off = MotionSettings().withPreset(MotionPreset.Off)
        for (style in DesignStyle.entries) {
            val profile = designFor(style).motion
            for (token in MotionToken.entries) {
                val instant = resolveMotion(token, off, reduced = false).withDesignProfile(profile)
                assertTrue("${token.name} under $style must remain instant", instant.isInstant)
                assertEquals(EnterTransition.None, instant.fadeEnter())
                assertEquals(ExitTransition.None, instant.fadeExit())

                val active = resolveMotion(token, MotionSettings(), reduced = false).withDesignProfile(profile)
                assertFalse("${token.name} under $style should be active when motion is on", active.isInstant)
                assertNotEquals(EnterTransition.None, active.fadeEnter())
                assertNotEquals(ExitTransition.None, active.fadeExit())
            }
        }
    }
}

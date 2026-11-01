package com.beykus.beygram.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import com.beykus.beygram.ui.design.DesignMotionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The design style may shape motion, but never overrides the user's or the system's restrictions. */
class MotionDesignProfileTest {
    @Test fun theDefaultProfileKeepsTheResolvedTimingExactly() {
        val spec = MotionSpec(durationMillis = 260, intensity = 1f, easing = FastOutSlowInEasing)
        assertEquals(spec, spec.withDesignProfile(DesignMotionProfile(speedScale = 1f, intensityScale = 1f)))
    }

    @Test fun aStyleProfileScalesDurationAndTravelOnly() {
        val spec = MotionSpec(durationMillis = 200, intensity = 1f, easing = FastOutSlowInEasing)
        val scaled = spec.withDesignProfile(DesignMotionProfile(speedScale = 1.2f, intensityScale = 1.5f))
        assertEquals(240, scaled.durationMillis)
        assertEquals(1.5f, scaled.intensity, 0.0001f)
        // Easing is part of the token, not of the style.
        assertEquals(FastOutSlowInEasing, scaled.easing)
    }

    @Test fun aDisabledCategoryOrReducedMotionStaysInstantWhateverTheStyleAsks() {
        val settings = MotionSettings(preset = MotionPreset.Off)
        val resolved = resolveMotion(MotionToken.NavigationPush, settings, reduced = false)
        assertTrue(resolved.isInstant)
        // An instant spec is not stretched into a visible animation by a style profile.
        assertTrue(resolved.withDesignProfile(DesignMotionProfile(2f, 2f)).isInstant)
    }

    @Test fun scaledDurationsNeverRoundDownToZero() {
        val spec = MotionSpec(durationMillis = 1, intensity = 1f, easing = FastOutSlowInEasing)
        assertEquals(1, spec.withDesignProfile(DesignMotionProfile(speedScale = 0.1f, intensityScale = 1f)).durationMillis)
    }
}

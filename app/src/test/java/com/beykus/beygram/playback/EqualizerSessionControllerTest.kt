package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerSessionControllerTest {
    private class FakeEffect(
        private val frequencies: List<Int> = listOf(60, 230, 910, 3_600, 14_000),
        private val range: IntRange = -1_500..1_500,
        private val presetNames: List<String> = listOf("Normal", "Rock")
    ) : EqualizerEffect {
        var enabled: Boolean? = null
        var closed = false
        var failLevels = false
        var control = true
        val levels = mutableMapOf<Int, Int>()

        override val numberOfBands: Int get() = frequencies.size
        override val numberOfPresets: Int get() = presetNames.size

        override fun centerFrequencyHz(band: Int): Int = frequencies[band]
        override fun presetName(index: Int): String? = presetNames.getOrNull(index)
        override fun hasControl(): Boolean = control
        override fun bandLevelRangeMb(): IntRange = range

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun setBandLevel(band: Int, levelMb: Int) {
            if (failLevels) throw IllegalStateException("effect died")
            levels[band] = levelMb
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeFactory : EqualizerEffectFactory {
        val createdSessions = mutableListOf<Int>()
        val effects = mutableListOf<FakeEffect>()
        var failure: RuntimeException? = null
        var configure: (FakeEffect) -> Unit = {}

        override fun create(audioSessionId: Int): EqualizerEffect {
            createdSessions += audioSessionId
            failure?.let { throw it }
            return FakeEffect().also {
                effects += it
                configure(it)
            }
        }
    }

    private fun settings(
        enabled: Boolean = true,
        preset: EqualizerPreset = EqualizerPreset.Bass
    ) = EqualizerSettings(enabled = enabled, selectedPreset = preset)

    @Test
    fun noSessionNeverCreatesAnEffect() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)

        controller.onSession(null, settings())

        assertTrue(factory.createdSessions.isEmpty())
        assertEquals(EqualizerConnection.Idle, controller.state.connection)
        assertNull(controller.state.capabilities)
    }

    @Test
    fun sessionZeroIsNeverUsedAsAGlobalEffect() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)

        controller.onSession(0, settings())

        assertTrue(factory.createdSessions.isEmpty())
        assertEquals(EqualizerConnection.Unsupported, controller.state.connection)
        assertNull(controller.state.capabilities)
    }

    @Test
    fun sessionAttachesAppliesAndIsReleasedForVoiceStopAndSessionChange() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)

        controller.onSession(7, settings())
        assertEquals(listOf(7), factory.createdSessions)
        val first = factory.effects.single()
        assertEquals(true, first.enabled)
        assertEquals(5, first.levels.size)
        assertTrue(first.levels[0]!! > first.levels[4]!!)
        assertEquals(EqualizerConnection.Attached, controller.state.connection)
        assertEquals(7, controller.state.audioSessionId)
        assertEquals(listOf(60, 230, 910, 3_600, 14_000), controller.state.capabilities?.frequenciesHz)
        assertEquals(listOf("Normal", "Rock"), controller.state.capabilities?.systemPresets)

        controller.onSession(7, settings(enabled = false))
        assertEquals(1, factory.createdSessions.size)
        assertEquals(false, first.enabled)
        assertFalse(first.closed)

        controller.onSession(8, settings())
        assertTrue("Changing the session must release the old effect", first.closed)
        assertEquals(listOf(7, 8), factory.createdSessions)
        assertEquals(8, controller.state.audioSessionId)

        // PlaybackEngine reports null for voice/video notes, stop and ENDED.
        controller.onSession(null, settings())
        assertTrue(factory.effects.last().closed)
        assertEquals(EqualizerConnection.Idle, controller.state.connection)
        assertNull(controller.state.audioSessionId)
        assertNotNull("Paused/idle UI keeps the last real band layout", controller.state.capabilities)
    }

    @Test
    fun sameSessionWhenMusicResumesDoesNotLeakASecondEffect() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)
        controller.onSession(11, settings())
        repeat(4) { controller.onSession(11, settings(preset = EqualizerPreset.Rock)) }

        assertEquals(1, factory.createdSessions.size)
        assertEquals(1, factory.effects.size)
        assertFalse(factory.effects.single().closed)
        assertEquals(EqualizerConnection.Attached, controller.state.connection)
    }

    @Test
    fun unsupportedDeviceIsReportedWithoutTouchingPlayback() {
        val factory = FakeFactory().apply { failure = UnsupportedOperationException("no effect") }
        val controller = EqualizerSessionController(factory)

        controller.onSession(4, settings())
        assertEquals(EqualizerConnection.Unsupported, controller.state.connection)
        assertNull(controller.state.capabilities)
        assertEquals(0, factory.effects.size)

        // A settings change on the same session does not hammer a device that already said no.
        controller.onSession(4, settings(preset = EqualizerPreset.Rock))
        assertEquals(1, factory.createdSessions.size)

        // Music stopping and starting again is a new attempt.
        controller.onSession(null, settings())
        factory.failure = null
        controller.onSession(5, settings())
        assertEquals(listOf(4, 5), factory.createdSessions)
        assertEquals(EqualizerConnection.Attached, controller.state.connection)
    }

    @Test
    fun effectWithoutControlIsRejectedInsteadOfShowingDeadSliders() {
        val factory = FakeFactory().apply { configure = { effect -> effect.control = false } }
        val controller = EqualizerSessionController(factory)

        controller.onSession(6, settings())

        assertTrue(factory.effects.single().closed)
        assertEquals(EqualizerConnection.Failed, controller.state.connection)
        assertNull(controller.state.capabilities)
    }

    @Test
    fun failedParameterUpdateReleasesTheEffectAndFallsBack() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)
        controller.onSession(9, settings())
        val effect = factory.effects.single()
        effect.failLevels = true

        controller.onSession(9, settings(preset = EqualizerPreset.Rock))

        assertTrue(effect.closed)
        assertEquals(EqualizerConnection.Failed, controller.state.connection)
        assertNull(controller.state.capabilities)
        assertNull(controller.state.audioSessionId)
    }

    @Test
    fun closeReleasesTheAttachedEffect() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)
        controller.onSession(3, settings())
        controller.close()

        assertTrue(factory.effects.single().closed)
        assertEquals(EqualizerConnection.Idle, controller.state.connection)
        assertNull(controller.state.capabilities)
    }

    @Test
    fun hardwareRangeAndPresetNamesAreTakenFromTheEffect() {
        val factory = object : EqualizerEffectFactory {
            override fun create(audioSessionId: Int): EqualizerEffect =
                FakeEffect(range = -900..300, presetNames = listOf("Flat", "Vocal"))
        }
        val controller = EqualizerSessionController(factory)
        controller.onSession(2, settings())

        val capabilities = controller.state.capabilities
        assertNotNull(capabilities)
        assertEquals(-900, capabilities?.hardwareMinLevelMb)
        assertEquals(300, capabilities?.hardwareMaxLevelMb)
        assertEquals(listOf("Flat", "Vocal"), capabilities?.systemPresets)
        assertTrue(capabilities!!.levelsFor(settings()).all { it <= 300 })
    }

    @Test
    fun flatPresetWritesExactZeroesAndBypassesTheEffect() {
        val factory = FakeFactory()
        val controller = EqualizerSessionController(factory)
        controller.onSession(1, settings(preset = EqualizerPreset.Flat))

        val effect = factory.effects.single()
        assertEquals(List(5) { 0 }, effect.levels.values.toList())
        assertFalse("Flat must leave the signal untouched, not just draw a flat line", effect.enabled == true)
    }
}

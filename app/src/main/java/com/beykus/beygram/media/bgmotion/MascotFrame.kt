package com.beykus.beygram.media.bgmotion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Flat, native-friendly contract for the temporary 2D mascot test rig.
 *
 * Native BG Motion writes one float per field into [MascotFrameBuffer.values] and then calls
 * [MascotFrameBuffer.publish]. The indices are frozen for the experiment: append new fields and
 * bump [FLOAT_COUNT] instead of reordering the existing ones.
 *
 * Pose fields are expected in -1..1 (0 is rest). Music fields are 0..1 unless noted otherwise:
 * [MascotFrameLayout.PHRASE_LOUDNESS] is -1 when unknown, and [MascotFrameLayout.INTENSITY] is a
 * gain that the native side clamps to 0.5..1.5.
 */
object MascotFrameLayout {
    // Pose
    const val BOB = 0
    const val LEAN = 1
    const val ARM_LEFT = 2
    const val ARM_RIGHT = 3
    const val LEG_LEFT = 4
    const val LEG_RIGHT = 5
    const val ROTATION = 6
    const val ENERGY = 7

    // Secondary pose
    const val HEAD_NOD = 8
    const val HEAD_TILT = 9
    const val HEAD_TURN = 10
    const val CHEST_TWIST = 11
    const val HIP_SHIFT = 12
    const val KNEE_LEFT = 13
    const val KNEE_RIGHT = 14
    const val ELBOW_LEFT = 15
    const val ELBOW_RIGHT = 16
    const val FOOT_LEFT = 17
    const val FOOT_RIGHT = 18

    // Motion state
    const val PHASE = 19
    const val BLEND = 20
    const val IMPACT = 21
    const val MOVE_ORDINAL = 22
    const val ACCENTED = 23

    // Music
    const val PHRASE_LOUDNESS = 24
    const val ACCENT = 25
    const val BASS = 26
    const val SILENCE_WEIGHT = 27
    const val BEAT = 28
    const val BEAT_IN_BAR = 29
    const val BAR = 30
    const val BPM = 31
    const val SECTION_ENERGY = 32
    const val SECTION_KIND = 33
    const val ANALYZED = 34
    const val INTENSITY = 35
    const val BEATS_PER_BAR = 36
    const val SEEK_PULSE = 37
    const val SEEK_DIRECTION = 38
    const val TRANSITION_PULSE = 39

    const val FLOAT_COUNT = 40
}

/**
 * One frame shared between the native motion loop and Compose.
 *
 * [values] is a plain FloatArray so native code can write it without allocating. After writing,
 * [publish] bumps [version]. The rig reads [version] in its draw block, which invalidates drawing
 * only instead of recomposing the whole player sheet sixty times a second.
 */
class MascotFrameBuffer {
    val values = FloatArray(MascotFrameLayout.FLOAT_COUNT)
    var version by mutableIntStateOf(0)

    fun publish() {
        version++
    }
}

/** Stable native order written to [MascotFrameLayout.MOVE_ORDINAL]; append-only. */
enum class MascotMove {
    Idle,
    Pulse,
    Bounce,
    Sway,
    ArmWave,
    Spin,
    Pose,
    Freeze,
    HeadNod,
    BassDip,
    AccentPose;

    companion object {
        /** Maps the native [MascotFrameLayout.MOVE_ORDINAL] float; unknown or invalid values become [Idle]. */
        fun fromOrdinal(v: Float): MascotMove {
            if (!v.isFinite()) return Idle
            return entries.getOrElse(v.roundToInt()) { Idle }
        }
    }
}

/** Stable native order written to [MascotFrameLayout.SECTION_KIND]; append-only. */
enum class MascotSectionKind {
    Unknown,
    Intro,
    Build,
    Main,
    Breakdown,
    Outro;

    companion object {
        /** Maps the native [MascotFrameLayout.SECTION_KIND] float; unknown or invalid values become [Unknown]. */
        fun fromOrdinal(v: Float): MascotSectionKind {
            if (!v.isFinite()) return Unknown
            return entries.getOrElse(v.roundToInt()) { Unknown }
        }
    }
}

// Small typed readers for the flat frame. They intentionally do not clamp: the renderer sanitises
// values at the point of use so an out-of-range native write cannot crash a draw pass.

fun FloatArray.bob(): Float = this[MascotFrameLayout.BOB]
fun FloatArray.lean(): Float = this[MascotFrameLayout.LEAN]
fun FloatArray.armLeft(): Float = this[MascotFrameLayout.ARM_LEFT]
fun FloatArray.armRight(): Float = this[MascotFrameLayout.ARM_RIGHT]
fun FloatArray.legLeft(): Float = this[MascotFrameLayout.LEG_LEFT]
fun FloatArray.legRight(): Float = this[MascotFrameLayout.LEG_RIGHT]
fun FloatArray.rotation(): Float = this[MascotFrameLayout.ROTATION]
fun FloatArray.energy(): Float = this[MascotFrameLayout.ENERGY]
fun FloatArray.headNod(): Float = this[MascotFrameLayout.HEAD_NOD]
fun FloatArray.headTilt(): Float = this[MascotFrameLayout.HEAD_TILT]
fun FloatArray.headTurn(): Float = this[MascotFrameLayout.HEAD_TURN]
fun FloatArray.chestTwist(): Float = this[MascotFrameLayout.CHEST_TWIST]
fun FloatArray.hipShift(): Float = this[MascotFrameLayout.HIP_SHIFT]
fun FloatArray.kneeLeft(): Float = this[MascotFrameLayout.KNEE_LEFT]
fun FloatArray.kneeRight(): Float = this[MascotFrameLayout.KNEE_RIGHT]
fun FloatArray.elbowLeft(): Float = this[MascotFrameLayout.ELBOW_LEFT]
fun FloatArray.elbowRight(): Float = this[MascotFrameLayout.ELBOW_RIGHT]
fun FloatArray.footLeft(): Float = this[MascotFrameLayout.FOOT_LEFT]
fun FloatArray.footRight(): Float = this[MascotFrameLayout.FOOT_RIGHT]
fun FloatArray.phase(): Float = this[MascotFrameLayout.PHASE]
fun FloatArray.blend(): Float = this[MascotFrameLayout.BLEND]
fun FloatArray.impact(): Float = this[MascotFrameLayout.IMPACT]
fun FloatArray.moveOrdinal(): Float = this[MascotFrameLayout.MOVE_ORDINAL]
fun FloatArray.accented(): Float = this[MascotFrameLayout.ACCENTED]
fun FloatArray.phraseLoudness(): Float = this[MascotFrameLayout.PHRASE_LOUDNESS]
fun FloatArray.accent(): Float = this[MascotFrameLayout.ACCENT]
fun FloatArray.bass(): Float = this[MascotFrameLayout.BASS]
fun FloatArray.silenceWeight(): Float = this[MascotFrameLayout.SILENCE_WEIGHT]
fun FloatArray.beat(): Float = this[MascotFrameLayout.BEAT]
fun FloatArray.beatInBar(): Float = this[MascotFrameLayout.BEAT_IN_BAR]
fun FloatArray.bar(): Float = this[MascotFrameLayout.BAR]
fun FloatArray.bpm(): Float = this[MascotFrameLayout.BPM]
fun FloatArray.sectionEnergy(): Float = this[MascotFrameLayout.SECTION_ENERGY]
fun FloatArray.sectionKind(): MascotSectionKind =
    MascotSectionKind.fromOrdinal(this[MascotFrameLayout.SECTION_KIND])

fun FloatArray.isAnalyzed(): Boolean = this[MascotFrameLayout.ANALYZED] >= 0.5f
fun FloatArray.analyzed(): Boolean = isAnalyzed()
fun FloatArray.intensity(): Float = this[MascotFrameLayout.INTENSITY]
fun FloatArray.beatsPerBar(): Int {
    val value = this[MascotFrameLayout.BEATS_PER_BAR]
    if (!value.isFinite()) return 4
    return value.roundToInt().coerceIn(1, 8)
}
fun FloatArray.seekPulse(): Float {
    val value = this[MascotFrameLayout.SEEK_PULSE]
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
fun FloatArray.seekDirection(): Float {
    val value = this[MascotFrameLayout.SEEK_DIRECTION]
    return if (value.isFinite() && value < 0f) -1f else 1f
}
fun FloatArray.transitionPulse(): Float {
    val value = this[MascotFrameLayout.TRANSITION_PULSE]
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
fun FloatArray.move(): MascotMove = MascotMove.fromOrdinal(this[MascotFrameLayout.MOVE_ORDINAL])

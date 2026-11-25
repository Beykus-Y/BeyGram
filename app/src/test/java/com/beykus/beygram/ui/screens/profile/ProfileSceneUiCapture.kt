package com.beykus.beygram.ui.screens.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfilePresence
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the real profile route with synthetic profile media and the same animation as production. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class ProfileSceneUiCapture {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureRevealAndScroll() {
        val destination = System.getenv("BEYGRAM_PROFILE_CAPTURE_DIR")
        assumeNotNull(destination)
        val output = File(checkNotNull(destination)).apply { mkdirs() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photoFile = File(context.cacheDir, "profile-scene-fixture.png")
        writeFixture(photoFile)
        val files = mapOf(1 to MutableStateFlow(MediaFileState(1, localPath = photoFile.absolutePath)))
        val profile = ProfileState(ProfileInfo(
            id = 42L,
            title = "Александра Синицына",
            username = "sample_profile",
            bio = "Синтетический профиль для визуальной проверки. Фото, имя и статус меняют положение при прокрутке.",
            status = ProfilePresence.Online,
            photo = AvatarPhoto(smallFileId = 1, bigFileId = 1, photoId = 42L),
            isVerified = true
        ))
        ProfileSceneSettings.load(context)
        ProfileSceneSettings.setEnabled(context, true)
        var exits = 0
        try {
            compose.setContent {
                BeyGramTheme(darkTheme = true) {
                    ProfileScreen(
                        state = profile,
                        isOwnProfile = false,
                        observeFile = { files.getValue(it) },
                        downloadFile = {},
                        onBack = { exits++ },
                        onRefresh = {},
                        onEdit = {},
                        setPhoto = { Result.success(Unit) },
                        lastSeenStyle = LastSeenStyle.Relative
                    )
                }
            }
            val avatar = compose.onNodeWithContentDescription(context.getString(R.string.a11y_profile_photo_open))
            val restWidth = avatar.assertIsDisplayed().getUnclippedBoundsInRoot().let { it.right - it.left }
            assertEquals(112.dp, restWidth)
            capture(output, "01-c-rest")
            compose.mainClock.autoAdvance = false
            avatar.performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }
            compose.runOnIdle {}
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(112)
            compose.waitForIdle()
            val midwayWidth = avatar.getUnclippedBoundsInRoot().let { it.right - it.left }
            capture(output, "02-c-reveal-mid")
            assertTrue("Rest width $restWidth, middle width $midwayWidth", midwayWidth > restWidth)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val expandedWidth = avatar.getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue(expandedWidth > midwayWidth)
            capture(output, "03-c-revealed")

            val content = compose.onNode(hasScrollAction())
            content.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 200f) }
            capture(output, "04-c-collapse-mid")
            content.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 3000f) }
            capture(output, "05-c-compact")

            pressBack()
            assertEquals(restWidth, avatar.assertIsDisplayed().getUnclippedBoundsInRoot().let { it.right - it.left })
            assertEquals(0, exits)
            capture(output, "06-c-rest-after-back")
            avatar.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val touchedWidth = avatar.getUnclippedBoundsInRoot().let { it.right - it.left }
            assertEquals("Physical pointer hits the same reveal target", expandedWidth, touchedWidth)
            pressBack()

            compose.runOnIdle { ProfileSceneSettings.setEnabled(context, false) }
            avatar.assertIsDisplayed()
            capture(output, "07-a-stable")
        } finally {
            ProfileSceneSettings.setEnabled(context, false)
        }
    }

    private fun capture(destination: File, frame: String) {
        val image = File(destination, "profile-$frame.png")
        compose.onRoot().captureRoboImage(image.absolutePath)
        check(image.length() > 0) { "Missing screenshot $image" }
    }

    private fun writeFixture(file: File) {
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFF263B61.toInt())
        paint.color = 0xFFF4B86B.toInt()
        canvas.drawCircle(450f, 185f, 72f, paint)
        paint.color = 0xFFE08365.toInt()
        canvas.drawCircle(255f, 340f, 150f, paint)
        paint.color = 0xFF6895A0.toInt()
        canvas.drawRect(0f, 500f, 640f, 640f, paint)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

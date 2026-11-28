package com.beykus.beygram.ui.screens.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileAccentTheme
import com.beykus.beygram.domain.model.ProfileInfo
import com.beykus.beygram.domain.model.ProfilePresence
import com.beykus.beygram.domain.model.ProfilePhotoMedia
import com.beykus.beygram.domain.model.ProfilePhotosPage
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import javax.imageio.ImageIO
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
            isVerified = true,
            profileAccentTheme = ProfileAccentTheme(
                lightBackground = listOf(0x4B7BEC, 0x778BEB),
                darkBackground = listOf(0x2B4C8C, 0x4A3A7A),
                lightPalette = listOf(0x3867D6),
                darkPalette = listOf(0x70A1FF)
            )
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

            val expandedHeight = avatar.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            val content = compose.onNode(hasScrollAction())
            content.performTouchInput {
                down(center)
                moveBy(Offset(0f, 180f))
            }
            val stretchedHeight = avatar.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue("Pull-down must grow the same photo", stretchedHeight > expandedHeight)
            assertTrue(stretchedHeight - expandedHeight <= 56.dp)
            capture(output, "03b-c-pull-down")
            content.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(expandedHeight, avatar.getUnclippedBoundsInRoot().let { it.bottom - it.top })
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

    @Test
    @Config(qualifiers = "ru-rRU-w780dp-h360dp-land-xhdpi")
    fun captureCoverOnWideProfileWithoutPhoto() {
        val destination = System.getenv("BEYGRAM_PROFILE_CAPTURE_DIR")
        assumeNotNull(destination)
        val output = File(checkNotNull(destination)).apply { mkdirs() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "Александра Синицына"
        val profile = ProfileState(ProfileInfo(id = 43L, title = name, status = ProfilePresence.Online))
        val style = mutableStateOf(DesignStyle.Default)
        val dark = mutableStateOf(false)
        ProfileSceneSettings.load(context)
        ProfileSceneSettings.setEnabled(context, true)
        try {
            compose.setContent {
                BeyGramTheme(
                    darkTheme = dark.value,
                    design = designFor(style.value, GlassQuality.High, liquidGlassV2 = true)
                ) {
                    ProfileScreen(
                        state = profile,
                        isOwnProfile = true,
                        observeFile = { error("A profile without photos should not request files") },
                        downloadFile = {},
                        onBack = {},
                        onRefresh = {},
                        onEdit = {},
                        setPhoto = { Result.success(Unit) },
                        lastSeenStyle = LastSeenStyle.Relative
                    )
                }
            }
            compose.onNodeWithText(name).assertIsDisplayed()
            ProfileSceneSettings.setDensity(context, ProfileSceneDensity.Calm)
            compose.waitForIdle()
            capture(output, "08-c-wide-light-calm")
            compose.runOnIdle { ProfileSceneSettings.setDensity(context, ProfileSceneDensity.Rich) }
            for (designStyle in DesignStyle.entries) {
                compose.runOnIdle {
                    style.value = designStyle
                    dark.value = designStyle in listOf(DesignStyle.LiquidGlass, DesignStyle.Signature)
                }
                compose.onNodeWithText(name).assertIsDisplayed()
                capture(output, "09-c-wide-rich-${designStyle.name}")
            }
        } finally {
            ProfileSceneSettings.setDensity(context, ProfileSceneDensity.Balanced)
            ProfileSceneSettings.setEnabled(context, false)
        }
    }

    @Test
    fun swipeHistoryAndOpenGalleryOnSamePhoto() {
        val destination = System.getenv("BEYGRAM_PROFILE_CAPTURE_DIR")
        assumeNotNull(destination)
        val output = File(checkNotNull(destination)).apply { mkdirs() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (1..2).associateWith { id ->
            val path = File(context.cacheDir, "profile-scene-history-$id.png")
            writeFixture(path, if (id == 1) 0xFF263B61.toInt() else 0xFF6C3B5F.toInt())
            MutableStateFlow(MediaFileState(id, localPath = path.absolutePath))
        }
        val profile = ProfileState(ProfileInfo(
            id = 44L,
            title = "Александра Синицына",
            photo = AvatarPhoto(smallFileId = 1, bigFileId = 1, photoId = 101L)
        ))
        var requests = 0
        ProfileSceneSettings.load(context)
        ProfileSceneSettings.setEnabled(context, true)
        try {
            compose.setContent {
                BeyGramTheme(darkTheme = true) {
                    ProfileScreen(
                        state = profile,
                        isOwnProfile = false,
                        observeFile = { files.getValue(it) },
                        downloadFile = {},
                        loadProfilePhotos = { profileId, offset, _ ->
                            assertEquals(44L, profileId)
                            assertEquals(0, offset)
                            requests++
                            Result.success(ProfilePhotosPage(
                                photos = listOf(ProfilePhotoMedia(101L, 1, 1), ProfilePhotoMedia(102L, 2, 2)),
                                totalCount = 2,
                                nextOffset = 2,
                                hasMore = false
                            ))
                        },
                        onBack = {},
                        onRefresh = {},
                        onEdit = {},
                        setPhoto = { Result.success(Unit) },
                        lastSeenStyle = LastSeenStyle.Relative
                    )
                }
            }
            val avatar = compose.onNodeWithContentDescription(context.getString(R.string.a11y_profile_photo_open))
            val first = context.getString(R.string.profile_gallery_position, 1, 2)
            val second = context.getString(R.string.profile_gallery_position, 2, 2)
            val expanded = context.getString(R.string.a11y_profile_photo_expanded_state)
            compose.runOnIdle { assertEquals(0, requests) }
            avatar.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(400)
            avatar.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$expanded. $first"))
            avatar.performTouchInput { swipeLeft() }
            avatar.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$expanded. $second"))
            compose.waitUntil(timeoutMillis = 5_000) {
                val screenshot = ImageIO.read(capture(output, "10-c-inline-second-photo"))
                screenshot.getRGB(screenshot.width * 12 / 100, screenshot.height * 13 / 100) == 0xFF6C3B5F.toInt()
            }
            avatar.performTouchInput { click() }
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            capture(output, "11-c-gallery-second-photo")
            pressBack()
            avatar.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$expanded. $second"))
            compose.runOnIdle { assertEquals(1, requests) }
        } finally {
            ProfileSceneSettings.setEnabled(context, false)
        }
    }

    private fun capture(destination: File, frame: String): File {
        val image = File(destination, "profile-$frame.png")
        compose.onRoot().captureRoboImage(image.absolutePath)
        check(image.length() > 0) { "Missing screenshot $image" }
        return image
    }

    private fun writeFixture(file: File, background: Int = 0xFF263B61.toInt()) {
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(background)
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

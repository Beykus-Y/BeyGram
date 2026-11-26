package com.beykus.beygram.ui.screens.chat

import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.camera.view.PreviewView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoNoteRecordingPreviewViewTest {
    @Test fun configureCircularVideoNotePreviewAppliesCompatibleTransparentCircularClipping() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val previewView = configureCircularVideoNotePreview(PreviewView(context))

        assertEquals(PreviewView.ImplementationMode.COMPATIBLE, previewView.implementationMode)
        assertEquals(PreviewView.ScaleType.FILL_CENTER, previewView.scaleType)
        assertTrue(previewView.clipToOutline)
        assertSame(CircularVideoNoteOutlineProvider, previewView.outlineProvider)
        assertEquals(
            android.graphics.Color.TRANSPARENT,
            (previewView.background as? ColorDrawable)?.color
        )
    }

    @Test fun circularOutlineProviderProducesOvalForLaidOutViewAndSkipsZeroSizedView() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = View(context)
        val zeroOutline = Outline()
        CircularVideoNoteOutlineProvider.getOutline(view, zeroOutline)
        assertTrue(zeroOutline.isEmpty)

        view.layout(0, 0, 400, 300)
        val laidOutOutline = Outline()
        CircularVideoNoteOutlineProvider.getOutline(view, laidOutOutline)
        assertFalse(laidOutOutline.isEmpty)
    }
}

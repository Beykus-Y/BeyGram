package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.TELEGRAM_STARS_CURRENCY
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidContentMappingTest {
    private fun file(id: Int) = TdApi.File().apply { this.id = id; size = 100; local = TdApi.LocalFile(); remote = TdApi.RemoteFile() }

    @Test
    fun lockedBundleKeepsThePreviewPriceAndCaptionButNoPurchasedMedia() {
        val content = TdApi.MessagePaidMedia().apply {
            starCount = 25
            media = arrayOf<TdApi.PaidMedia>(TdApi.PaidMediaPreview().apply {
                width = 640
                height = 480
                duration = 12
                minithumbnail = TdApi.Minithumbnail().apply { width = 16; height = 12; data = byteArrayOf(1, 2, 3) }
            })
            caption = TdApi.FormattedText("Preview caption", emptyArray())
        }

        val paid = (TdMediaMapper(MediaFileStore()).special(content) as MessageSpecialContent.PaidMedia).media

        assertEquals(25L, paid.starCount)
        assertEquals(1, paid.itemCount)
        assertFalse(paid.isPurchased)
        assertEquals("Preview caption", paid.caption)
        assertEquals(640, paid.previewWidth)
        assertEquals(480, paid.previewHeight)
        assertEquals(12, paid.previewDurationSeconds)
        assertArrayEquals(byteArrayOf(1, 2, 3), paid.blurredMinithumbnail)
        assertNull(paid.firstPurchasedMedia)
    }

    @Test
    fun purchasedBundleMapsItsFirstPhotoAsAnOrdinaryMediaItem() {
        val content = TdApi.MessagePaidMedia().apply {
            starCount = 5
            media = arrayOf<TdApi.PaidMedia>(TdApi.PaidMediaPhoto().apply {
                photo = TdApi.Photo(false, null, arrayOf(TdApi.PhotoSize("x", file(21), 1280, 960, intArrayOf())))
            })
        }

        val paid = (TdMediaMapper(MediaFileStore()).special(content) as MessageSpecialContent.PaidMedia).media
        val first = paid.firstPurchasedMedia!!

        assertTrue(paid.isPurchased)
        assertEquals(MediaKind.Photo, first.kind)
        assertEquals(21, first.fileId)
        assertEquals(1280, first.width)
    }

    @Test
    fun purchasedVideoBundleMapsItsVideoItem() {
        val content = TdApi.MessagePaidMedia().apply {
            starCount = 8
            media = arrayOf<TdApi.PaidMedia>(TdApi.PaidMediaVideo().apply {
                video = TdApi.Video().apply {
                    this.video = file(31)
                    width = 720
                    height = 1280
                    duration = 9
                    mimeType = "video/mp4"
                    supportsStreaming = true
                }
            })
        }

        val paid = (TdMediaMapper(MediaFileStore()).special(content) as MessageSpecialContent.PaidMedia).media
        val first = paid.firstPurchasedMedia!!

        assertTrue(paid.isPurchased)
        assertEquals(MediaKind.Video, first.kind)
        assertEquals(31, first.fileId)
        assertEquals(9, first.durationSeconds)
        assertTrue(first.supportsStreaming)
    }

    @Test
    fun invoiceMapsTheProductTotalCurrencyTestFlagAndReceipt() {
        val mapper = TdMediaMapper(MediaFileStore())
        val invoice = TdApi.MessageInvoice().apply {
            productInfo = TdApi.ProductInfo().apply {
                title = "Pro pass"
                description = TdApi.FormattedText("Monthly access", emptyArray())
            }
            currency = TELEGRAM_STARS_CURRENCY
            totalAmount = 150
            isTest = true
        }

        val info = (mapper.special(invoice) as MessageSpecialContent.Invoice).invoice

        assertEquals("Pro pass", info.title)
        assertEquals("Monthly access", info.description)
        assertEquals(TELEGRAM_STARS_CURRENCY, info.currency)
        assertEquals(150L, info.totalAmountMinor)
        assertTrue(info.isTest)
        assertFalse(info.isPaid)

        invoice.receiptMessageId = 77
        val paid = (mapper.special(invoice) as MessageSpecialContent.Invoice).invoice
        assertTrue(paid.isPaid)
    }

    @Test
    fun paymentConfirmationsStayServiceTextAndNeverBecomeCards() {
        val mapper = TdMediaMapper(MediaFileStore())

        assertNull(mapper.special(TdApi.MessagePaymentSuccessful()))
        assertNull(mapper.special(TdApi.MessagePaymentSuccessfulBot()))
    }
}

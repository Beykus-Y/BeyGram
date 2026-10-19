package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class ProfilePhotosWindowTest {
    private fun item(id: Long) = ProfilePhotoMedia(id, id.toInt(), id.toInt() + 100)

    @Test fun pagesPreservePhotoIdsWithoutDuplicatesAndUseRawOffsets() {
        val seed = ProfilePhotosWindow(photos = listOf(item(9)), totalCount = 1)
        val one = seed.append(ProfilePhotosPage(listOf(item(9), item(8), item(7)), 8, 3, true))
        assertEquals(listOf(9L,8L,7L), one.photos.map { it.photoId })
        assertEquals(3,one.offset)
        val two = one.append(ProfilePhotosPage(listOf(item(7),item(6)),8,5,true))
        assertEquals(listOf(9L,8L,7L,6L), two.photos.map { it.photoId })
        assertEquals(5,two.offset)
    }
    @Test fun duplicateAndStaleResponsesCannotUndoProgress() {
        val first = ProfilePhotosWindow().append(ProfilePhotosPage(listOf(item(2)), 2, 1, true))
        assertEquals(first, first.append(ProfilePhotosPage(listOf(item(99)), 99, 0, true)))
        assertFalse(first.append(ProfilePhotosPage(emptyList(),2,1,true)).hasMore)
    }
    @Test fun completionAndDeletion() {
        val window = ProfilePhotosWindow().append(ProfilePhotosPage(listOf(item(1), item(2)), 2, 2, false))
        assertFalse(window.hasMore)
        assertEquals(listOf(2L), window.remove(1).photos.map { it.photoId })
        assertEquals(1, window.remove(1).totalCount)
    }
    @Test fun serverAnimationReplacesCurrentStaticAvatar() {
        val seed = ProfilePhotosWindow(photos = listOf(ProfilePhotoMedia(7, 10, 20)))
        val loaded = seed.append(ProfilePhotosPage(listOf(ProfilePhotoMedia(7, 10, 20, 30)), 1, 1, false))
        assertEquals(30, loaded.photos.single().animationFileId)
    }
    @Test fun animationIdentityRemainsThePhotoIdentity() {
        val video = ProfilePhotoMedia(456L,2,3,4)
        assertEquals(456L,video.photoId)
        assertEquals(4,video.fileId)
        assertEquals(3, video.bigFileId)
        assertEquals(3, ProfilePhotoMedia(457L,2,3).fileId)
    }
}

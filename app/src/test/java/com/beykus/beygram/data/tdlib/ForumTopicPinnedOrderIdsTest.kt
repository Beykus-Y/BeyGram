package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ForumTopicPinnedOrderIdsTest {
    @Test fun thePinnedOrderGoesToTdlibAsTopicIdsInDisplayOrder() {
        assertArrayEquals(intArrayOf(9, 2, 5), forumTopicIdArray(listOf(9L, 2L, 5L)))
    }

    @Test fun anEmptyOrderIsAnEmptyArray() {
        assertArrayEquals(IntArray(0), forumTopicIdArray(emptyList()))
    }
}

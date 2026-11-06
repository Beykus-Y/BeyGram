package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewestAnswersTest {
    @Test fun anAnswerThatArrivesLateCannotReplaceANewerOne() {
        val answers = NewestAnswers<Long>()
        val older = answers.start()
        val newer = answers.start()
        assertTrue(answers.accept(1L, newer))
        assertFalse(answers.accept(1L, older))
    }

    @Test fun anOlderAnswerIsStillShownWhenNoNewerOneHasBeenAccepted() {
        val answers = NewestAnswers<Long>()
        val older = answers.start()
        // A newer request that has not been answered, or failed, does not hide the older answer.
        answers.start()
        assertTrue(answers.accept(1L, older))
    }

    @Test fun anAnswerToARequestStartedBeforeAChangeIsRefused() {
        val answers = NewestAnswers<Long>()
        val before = answers.start()
        answers.change(1L)
        assertFalse(answers.accept(1L, before))
        assertTrue(answers.accept(1L, answers.start()))
    }

    @Test fun keysAreIndependent() {
        val answers = NewestAnswers<Long>()
        val first = answers.start()
        val other = answers.start()
        assertTrue(answers.accept(2L, other))
        assertTrue(answers.accept(1L, first))
    }
}

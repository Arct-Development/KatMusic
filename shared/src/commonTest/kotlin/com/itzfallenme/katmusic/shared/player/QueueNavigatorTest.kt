package com.itzfallenme.katmusic.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.random.Random

class QueueNavigatorTest {

    private val fixedRandom = Random(42)

    @Test
    fun `sequential next advances one track`() {
        val next = QueueNavigator.nextIndex(
            size = 5,
            currentIndex = 1,
            repeatMode = RepeatMode.OFF,
            shuffle = false,
            autoAdvance = false,
            random = fixedRandom,
        )
        assertEquals(2, next)
    }

    @Test
    fun `end of queue stops when repeat is off`() {
        val next = QueueNavigator.nextIndex(
            size = 3,
            currentIndex = 2,
            repeatMode = RepeatMode.OFF,
            shuffle = false,
            autoAdvance = false,
            random = fixedRandom,
        )
        assertEquals(-1, next)
    }

    @Test
    fun `repeat all wraps to the first track`() {
        val next = QueueNavigator.nextIndex(
            size = 3,
            currentIndex = 2,
            repeatMode = RepeatMode.ALL,
            shuffle = false,
            autoAdvance = false,
            random = fixedRandom,
        )
        assertEquals(0, next)
    }

    @Test
    fun `repeat one replays on auto advance but advances on explicit next`() {
        val auto = QueueNavigator.nextIndex(
            size = 4,
            currentIndex = 2,
            repeatMode = RepeatMode.ONE,
            shuffle = false,
            autoAdvance = true,
            random = fixedRandom,
        )
        val explicit = QueueNavigator.nextIndex(
            size = 4,
            currentIndex = 2,
            repeatMode = RepeatMode.ONE,
            shuffle = false,
            autoAdvance = false,
            random = fixedRandom,
        )
        assertEquals(2, auto)
        assertEquals(3, explicit)
    }

    @Test
    fun `shuffle never picks the current track`() {
        repeat(50) {
            val next = QueueNavigator.nextIndex(
                size = 6,
                currentIndex = 3,
                repeatMode = RepeatMode.OFF,
                shuffle = true,
                autoAdvance = false,
                random = fixedRandom,
            )
            assertEquals(true, next in 0..5)
            assertEquals(false, next == 3)
        }
    }

    @Test
    fun `previous restarts the track when it is past the threshold`() {
        val restart = QueueNavigator.previousIndex(
            size = 5,
            currentIndex = 3,
            positionMs = 10_000,
            repeatMode = RepeatMode.OFF,
            shuffle = false,
            random = fixedRandom,
        )
        val stepBack = QueueNavigator.previousIndex(
            size = 5,
            currentIndex = 3,
            positionMs = 500,
            repeatMode = RepeatMode.OFF,
            shuffle = false,
            random = fixedRandom,
        )
        assertEquals(3, restart)
        assertEquals(2, stepBack)
    }

    @Test
    fun `previous at the queue start stays on the first track`() {
        val previous = QueueNavigator.previousIndex(
            size = 4,
            currentIndex = 0,
            positionMs = 0,
            repeatMode = RepeatMode.OFF,
            shuffle = false,
            random = fixedRandom,
        )
        assertEquals(0, previous)
    }

    @Test
    fun `repeat mode cycles off all one`() {
        assertEquals(RepeatMode.ALL, QueueNavigator.cycle(RepeatMode.OFF))
        assertEquals(RepeatMode.ONE, QueueNavigator.cycle(RepeatMode.ALL))
        assertEquals(RepeatMode.OFF, QueueNavigator.cycle(RepeatMode.ONE))
    }
}

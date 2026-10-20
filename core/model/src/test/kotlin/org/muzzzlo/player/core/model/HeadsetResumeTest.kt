package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class HeadsetResumeTest {
    private val resume = HeadsetResume(windowMs = 1000)

    @Test
    fun `defaults keep the old behaviour`() {
        val behavior = PlaybackBehaviorSettings()
        assertEquals(TransientFocusAction.DUCK, behavior.transientFocus)
        assertFalse(behavior.resumeOnHeadset)
        assertFalse(behavior.skipSilence)
        assertEquals(behavior, AppSettings().playbackBehavior)
    }

    @ParameterizedTest
    @CsvSource(
        "true, 0, true",
        "true, 1000, true",
        "true, 1001, false",
        "false, 10, false",
    )
    fun `resumes only after an unplug, inside the window and when enabled`(
        enabled: Boolean,
        connectedAtMs: Long,
        expected: Boolean,
    ) {
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = true, nowMs = 0)
        assertEquals(expected, resume.shouldResume(enabled, connectedAtMs))
    }

    @Test
    fun `a pause by the user does not resume`() {
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = false, nowMs = 0)
        assertFalse(resume.shouldResume(enabled = true, nowMs = 10))
    }

    @Test
    fun `playing again or a later pause by the user cancels the resume`() {
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = true, nowMs = 0)
        resume.onPlayWhenReadyChanged(playWhenReady = true, becomingNoisy = false, nowMs = 5)
        assertFalse(resume.shouldResume(enabled = true, nowMs = 10))

        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = true, nowMs = 20)
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = false, nowMs = 30)
        assertFalse(resume.shouldResume(enabled = true, nowMs = 40))
    }

    @Test
    fun `one unplug resumes once`() {
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = true, nowMs = 0)
        assertEquals(true, resume.shouldResume(enabled = true, nowMs = 10))
        assertFalse(resume.shouldResume(enabled = true, nowMs = 20))
    }

    @Test
    fun `a clock that went back does not resume`() {
        resume.onPlayWhenReadyChanged(playWhenReady = false, becomingNoisy = true, nowMs = 500)
        assertFalse(resume.shouldResume(enabled = true, nowMs = 100))
    }
}

package com.hci.ren.feature.plangeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PlanGenerationMotionTest {
    private val baseProfile = WaveMotionProfile(
        waveReach = 0.7f,
        drift = 0.04f,
        scaleXBase = 0.68f,
        scaleYBase = 0.44f,
    )

    @Test fun visualStepDurationsUseCalmerUnevenPacing() {
        assertEquals(2.seconds, planGenerationVisualStepMinimumDuration(PlanStatus.Uploading))
        assertEquals(3.seconds, planGenerationVisualStepMinimumDuration(PlanStatus.Analyzing))
        assertEquals(2.seconds, planGenerationVisualStepMinimumDuration(PlanStatus.IdentifyingTopics))
        assertEquals(4.seconds, planGenerationVisualStepMinimumDuration(PlanStatus.CreatingBlocks))
        assertEquals(2.seconds, planGenerationVisualStepMinimumDuration(PlanStatus.Finalizing))
    }

    @Test fun baseWaveKeepsItsOriginalPace() {
        val motion = planGenerationWaveMotion(baseProfile, index = 0, progress = 0.25f)

        assertEquals(0f, motion.speedTaming, 0.0001f)
        assertEquals(0.25f, motion.progress, 0.0001f)
        assertEquals(0.68f, motion.scaleX, 0.0001f)
        assertEquals(0.62f, motion.scaleY, 0.0001f)
        assertEquals(0.5625f, motion.alphaMultiplier, 0.0001f)
    }

    @Test fun aggressiveWaveIsSoftenedWithoutFreezing() {
        val rawScaleX = baseProfile.scaleXBase + 3 * 0.11f
        val rawScaleY = baseProfile.scaleYBase + 1 * 0.09f
        val motion = planGenerationWaveMotion(baseProfile, index = 3, progress = 0.25f)

        assertTrue(motion.speedTaming > 0.9f)
        assertTrue(motion.progress < 0.25f)
        assertTrue(motion.scaleX < rawScaleX)
        assertTrue(motion.scaleX > baseProfile.scaleXBase)
        assertTrue(motion.scaleY < rawScaleY)
        assertTrue(motion.scaleY > baseProfile.scaleYBase)
    }
}

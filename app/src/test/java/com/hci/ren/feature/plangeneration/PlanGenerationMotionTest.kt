package com.hci.ren.feature.plangeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PlanGenerationMotionTest {
    private val baseProfile = WaveMotionProfile(
        waveReach = 0.7f,
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

    @Test fun breathMotionUsesSmallSynchronizedRange() {
        val neutral = planGenerationBreathMotion(0f)
        val expanded = planGenerationBreathMotion(0.25f)
        val contracted = planGenerationBreathMotion(0.75f)

        assertEquals(1f, neutral.scale, 0.0001f)
        assertEquals(1.065f, expanded.scale, 0.0001f)
        assertEquals(0.935f, contracted.scale, 0.0001f)
        assertTrue(expanded.scale - contracted.scale <= 0.14f)
        assertTrue(contracted.auraAlphaMultiplier > expanded.auraAlphaMultiplier)
        assertTrue(expanded.coreAlphaMultiplier > contracted.coreAlphaMultiplier)
    }

    @Test fun rippleProgressUsesSharedBreathWithoutRestarting() {
        val innerContracted = planGenerationRippleProgress(0.75f, index = 0, waveCount = 4)
        val innerExpanded = planGenerationRippleProgress(0.25f, index = 0, waveCount = 4)
        val outerContracted = planGenerationRippleProgress(0.75f, index = 3, waveCount = 4)
        val outerExpanded = planGenerationRippleProgress(0.25f, index = 3, waveCount = 4)

        assertTrue(innerContracted > innerExpanded)
        assertTrue(outerExpanded > outerContracted)
        assertTrue(innerContracted in 0.12f..0.88f)
        assertTrue(outerExpanded in 0.12f..0.88f)
    }

    @Test fun baseWaveKeepsItsPaceAndCapsVerticalScale() {
        val motion = planGenerationWaveMotion(baseProfile, index = 0, progress = 0.25f)

        assertEquals(0f, motion.speedTaming, 0.0001f)
        assertEquals(0.25f, motion.progress, 0.0001f)
        assertEquals(0.68f, motion.scaleX, 0.0001f)
        assertEquals(0.58f, motion.scaleY, 0.0001f)
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
        assertTrue(motion.scaleX <= 0.9f)
        assertTrue(motion.scaleY <= 0.58f)
    }
}

package com.hci.ren.feature.plangeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PlanGenerationMotionTest {
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
}

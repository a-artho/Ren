package com.hci.ren.feature.plangeneration

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanGenerationModelsTest {
    @Test fun wireStatusesMapToVisibleStages() {
        assertEquals(PlanStatus.Analyzing, PlanStatus.fromWire("ANALYZING"))
        assertEquals(PlanStatus.IdentifyingTopics, PlanStatus.fromWire("IDENTIFYING_TOPICS"))
        assertEquals(PlanStatus.CreatingBlocks, PlanStatus.fromWire("CREATING_BLOCKS"))
        assertEquals(PlanStatus.Finalizing, PlanStatus.fromWire("FINALIZING"))
        assertEquals(PlanStatus.Completed, PlanStatus.fromWire("COMPLETED"))
        assertEquals(PlanStatus.Failed, PlanStatus.fromWire("FAILED"))
    }
}

package com.hci.ren.feature.plangeneration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGenerationModelsTest {
    @Test fun wireStatusesMapToVisibleStages() {
        assertEquals(PlanStatus.Analyzing, PlanStatus.fromWire("ANALYZING"))
        assertEquals(PlanStatus.IdentifyingTopics, PlanStatus.fromWire("IDENTIFYING_TOPICS"))
        assertEquals(PlanStatus.CreatingBlocks, PlanStatus.fromWire("CREATING_BLOCKS"))
        assertEquals(PlanStatus.Finalizing, PlanStatus.fromWire("FINALIZING"))
        assertEquals(PlanStatus.Completed, PlanStatus.fromWire("COMPLETED"))
        assertEquals(PlanStatus.Failed, PlanStatus.fromWire("FAILED"))
        assertEquals(PlanStatus.Failed, PlanStatus.fromWire("CANCELED"))
    }

    @Test fun retryReusesRequestUnlessBackendDefinitelyFinished() {
        assertEquals(
            "existing",
            requestIdForRetry("existing", GenerationFailurePhase.UploadOrCreate) { "new" },
        )
        assertEquals(
            "existing",
            requestIdForRetry("existing", GenerationFailurePhase.Polling) { "new" },
        )
        assertEquals(
            "new",
            requestIdForRetry("existing", GenerationFailurePhase.BackendTerminal) { "new" },
        )
    }

    @Test fun httpFailureClassificationSeparatesPermanentAndTransientErrors() {
        assertFalse(isRetryableStatusCode(404))
        assertFalse(isRetryableStatusCode(422))
        assertTrue(isRetryableStatusCode(408))
        assertTrue(isRetryableStatusCode(429))
        assertTrue(isRetryableStatusCode(503))
    }

    @Test fun defaultProjectNameUsesConstant() {
        val plan = GeneratedStudyPlan("p1", emptyList(), emptyList())
        assertEquals(DEFAULT_PROJECT_NAME, plan.projectName)
    }

    @Test fun aiTitleIsUsedAsProjectNameWhenPresent() {
        val plan = GeneratedStudyPlan("p1", emptyList(), emptyList(), projectName = "Calculus I - Derivatives")
        assertEquals("Calculus I - Derivatives", plan.projectName)
    }
}

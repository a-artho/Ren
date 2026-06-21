package com.hci.ren.feature.plangeneration

enum class PlanStatus {
    Uploading, Analyzing, IdentifyingTopics, CreatingBlocks, Finalizing, Completed, Failed;

    companion object {
        fun fromWire(value: String) = when (value) {
            "ANALYZING" -> Analyzing
            "IDENTIFYING_TOPICS" -> IdentifyingTopics
            "CREATING_BLOCKS" -> CreatingBlocks
            "FINALIZING" -> Finalizing
            "COMPLETED" -> Completed
            "FAILED" -> Failed
            "CANCELED" -> Failed
            else -> Failed
        }
    }
}

enum class GenerationFailurePhase {
    UploadOrCreate,
    Polling,
    BackendTerminal,
}

internal fun requestIdForRetry(
    previousRequestId: String,
    phase: GenerationFailurePhase,
    newId: () -> String,
): String = if (phase == GenerationFailurePhase.BackendTerminal) newId() else previousRequestId

internal fun isRetryableStatusCode(code: Int): Boolean =
    code == 408 || code == 429 || code >= 500

data class StudyTopic(val id: String, val title: String, val order: Int)
data class GeneratedStudyBlock(
    val id: String,
    val title: String,
    val order: Int,
    val durationMinutes: Int,
    val instructions: String,
    val topicIds: List<String>,
)
data class GeneratedStudyPlan(
    val id: String,
    val topics: List<StudyTopic>,
    val blocks: List<GeneratedStudyBlock>,
    val totalEstimatedMinutes: Int,
)

data class PlanGenerationUiState(
    val status: PlanStatus = PlanStatus.Uploading,
    val planId: String? = null,
    val plan: GeneratedStudyPlan? = null,
    val errorMessage: String? = null,
)


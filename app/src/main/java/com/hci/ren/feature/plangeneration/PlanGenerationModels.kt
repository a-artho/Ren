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

const val DEFAULT_PROJECT_NAME = "Study plan"

internal fun requestIdForRetry(
    previousRequestId: String,
    phase: GenerationFailurePhase,
    newId: () -> String,
): String = if (phase == GenerationFailurePhase.BackendTerminal) newId() else previousRequestId

internal fun isRetryableStatusCode(code: Int): Boolean =
    code == 408 || code == 429 || code >= 500

data class StudyTopic(val id: String, val title: String, val order: Int)
enum class StudyBlockDifficulty { Light, Standard, Heavy }
enum class EstimateConfidence { Low, Medium, High }
enum class StudyTaskType(val defaultMinimumMinutes: Int) {
    Concept(20),
    Practice(15),
    Review(10),
    MockTest(30),
    Memorization(10),
    Reading(15),
    Summary(10),
    MistakeReview(10),
    Custom(10),
    Quiz(10),
}
enum class StudyTaskStatus {
    NotStarted,
    InProgress,
    Completed,
    DeferredByUser,
    Locked,
    Overdue,
    Rescheduled,
    ExcludedByUser,
    Unscheduled,
    OverCapacity,
}
data class StudySourceDocument(
    val id: String,
    val filename: String,
    val order: Int,
    val pageCount: Int? = null,
    val uploadDocumentId: String? = null,
)
data class StudySourceRef(
    val documentId: String,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val sectionTitle: String? = null,
)
data class ExtractionWarning(
    val type: String,
    val message: String,
    val blockId: String? = null,
    val documentId: String? = null,
    val startPage: Int? = null,
    val endPage: Int? = null,
)
data class GeneratedStudyBlock(
    val id: String,
    val title: String,
    val order: Int,
    val durationMinutes: Int,
    val effortMinMinutes: Int = durationMinutes,
    val effortLikelyMinutes: Int = durationMinutes,
    val effortMaxMinutes: Int = durationMinutes,
    val instructions: String,
    val topicIds: List<String>,
    val minimumUsefulMinutes: Int = 10,
    val taskType: StudyTaskType = StudyTaskType.Review,
    val status: StudyTaskStatus = StudyTaskStatus.NotStarted,
    val scheduledDate: String? = null,
    val dependencies: List<String> = emptyList(),
    val sourceRefs: List<StudySourceRef> = emptyList(),
    val difficulty: StudyBlockDifficulty = StudyBlockDifficulty.Standard,
    val difficultyScore: Int? = null,
    val densityScore: Int? = null,
    val productionDemandScore: Int? = null,
    val estimateConfidence: EstimateConfidence = EstimateConfidence.Medium,
    val estimatedMinutes: Int = durationMinutes,
    val completionCriteria: List<String> = emptyList(),
    val splitAllowed: Boolean = true,
    val continuityGroup: String? = null,
)
data class GeneratedStudyPlan(
    val id: String,
    val topics: List<StudyTopic>,
    val blocks: List<GeneratedStudyBlock>,
    val totalEstimatedMinutes: Int,
    val projectName: String = DEFAULT_PROJECT_NAME,
    val planVersion: Int = 1,
    val sourceDocuments: List<StudySourceDocument> = emptyList(),
    val extractionWarnings: List<ExtractionWarning> = emptyList(),
)

data class PlanGenerationUiState(
    val status: PlanStatus = PlanStatus.Uploading,
    val planId: String? = null,
    val plan: GeneratedStudyPlan? = null,
    val uploadingDocumentIndex: Int = 0,
    val uploadingDocumentTotal: Int = 0,
    val errorMessage: String? = null,
)


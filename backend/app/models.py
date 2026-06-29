from __future__ import annotations

from datetime import date
from enum import StrEnum
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator

VALID_STUDY_DAYS = frozenset({
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
})


class PlanStatus(StrEnum):
    UPLOADING = "UPLOADING"
    ANALYZING = "ANALYZING"
    IDENTIFYING_TOPICS = "IDENTIFYING_TOPICS"
    CREATING_BLOCKS = "CREATING_BLOCKS"
    FINALIZING = "FINALIZING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"


class Setup(BaseModel):
    model_config = ConfigDict(extra="forbid")
    goal: Literal["PrepareForExam"]
    planTitle: str | None = Field(default=None, max_length=80)
    deadline: Literal["Tomorrow", "InThreeDays", "InOneWeek", "ChooseDate"]
    deadlineDate: str | None = None
    dailyStudyMinutes: int = Field(gt=0, le=1440)
    studyDays: list[str] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_setup(self):
        if self.deadline == "ChooseDate" and not self.deadlineDate:
            raise ValueError("deadlineDate is required when deadline is ChooseDate")
        if self.deadlineDate:
            try:
                date.fromisoformat(self.deadlineDate)
            except ValueError as exc:
                raise ValueError("deadlineDate must be an ISO date") from exc
        invalid_days = [day for day in self.studyDays if day not in VALID_STUDY_DAYS]
        if invalid_days:
            raise ValueError(f"Unsupported study day: {invalid_days[0]}")
        if len(set(self.studyDays)) != len(self.studyDays):
            raise ValueError("studyDays must be unique")
        return self


class CreatePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentIds: list[str] = Field(min_length=1, max_length=10)
    requestId: str
    setup: Setup


class Topic(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    order: int = Field(ge=1)


class SourceDocumentInfo(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(min_length=1)
    filename: str = Field(min_length=1)
    order: int = Field(ge=1)
    pageCount: int | None = Field(default=None, ge=1)
    uploadDocumentId: str | None = None


class SourceRef(BaseModel):
    model_config = ConfigDict(extra="ignore")
    documentId: str = Field(min_length=1)
    startPage: int | None = Field(default=None, ge=1)
    endPage: int | None = Field(default=None, ge=1)
    sectionTitle: str | None = None
    materialGroupTitle: str | None = None

    @model_validator(mode="after")
    def validate_page_range(self):
        if self.startPage and self.endPage and self.endPage < self.startPage:
            self.endPage = self.startPage
        return self


class StudyTaskType(StrEnum):
    CONCEPT = "CONCEPT"
    PRACTICE = "PRACTICE"
    REVIEW = "REVIEW"
    MOCK_TEST = "MOCK_TEST"
    MEMORIZATION = "MEMORIZATION"
    READING = "READING"
    SUMMARY = "SUMMARY"
    MISTAKE_REVIEW = "MISTAKE_REVIEW"
    QUIZ = "QUIZ"
    CUSTOM = "CUSTOM"


class Difficulty(StrEnum):
    LIGHT = "LIGHT"
    STANDARD = "STANDARD"
    HEAVY = "HEAVY"


class EstimateConfidence(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class StudyTaskStatus(StrEnum):
    NOT_STARTED = "NOT_STARTED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    DEFERRED_BY_USER = "DEFERRED_BY_USER"
    LOCKED = "LOCKED"
    OVERDUE = "OVERDUE"
    RESCHEDULED = "RESCHEDULED"
    EXCLUDED_BY_USER = "EXCLUDED_BY_USER"
    UNSCHEDULED = "UNSCHEDULED"
    OVER_CAPACITY = "OVER_CAPACITY"


TASK_TYPE_MINIMUMS = {
    StudyTaskType.CONCEPT: 20,
    StudyTaskType.PRACTICE: 10,
    StudyTaskType.REVIEW: 5,
    StudyTaskType.MOCK_TEST: 30,
    StudyTaskType.MEMORIZATION: 5,
    StudyTaskType.READING: 10,
    StudyTaskType.SUMMARY: 10,
    StudyTaskType.MISTAKE_REVIEW: 5,
    StudyTaskType.QUIZ: 10,
    StudyTaskType.CUSTOM: 5,
}


DIFFICULTY_SCORE_DEFAULTS = {
    Difficulty.LIGHT: 2,
    Difficulty.STANDARD: 3,
    Difficulty.HEAVY: 4,
}

TASK_TYPE_PRODUCTION_DEFAULTS = {
    StudyTaskType.CONCEPT: 3,
    StudyTaskType.PRACTICE: 4,
    StudyTaskType.REVIEW: 2,
    StudyTaskType.MOCK_TEST: 5,
    StudyTaskType.MEMORIZATION: 3,
    StudyTaskType.READING: 2,
    StudyTaskType.SUMMARY: 3,
    StudyTaskType.MISTAKE_REVIEW: 3,
    StudyTaskType.QUIZ: 4,
    StudyTaskType.CUSTOM: 3,
}

TASK_TYPE_DENSITY_DEFAULTS = {
    StudyTaskType.CONCEPT: 3,
    StudyTaskType.PRACTICE: 3,
    StudyTaskType.REVIEW: 2,
    StudyTaskType.MOCK_TEST: 4,
    StudyTaskType.MEMORIZATION: 3,
    StudyTaskType.READING: 2,
    StudyTaskType.SUMMARY: 3,
    StudyTaskType.MISTAKE_REVIEW: 3,
    StudyTaskType.QUIZ: 3,
    StudyTaskType.CUSTOM: 3,
}


class StudyBlock(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    order: int = Field(ge=1)
    durationMinutes: int | None = Field(default=None, gt=0, le=1440)
    estimatedMinutes: int | None = Field(default=None, gt=0, le=1440)
    minimumUsefulMinutes: int = Field(default=10, gt=0, le=1440)
    taskType: StudyTaskType = StudyTaskType.REVIEW
    instructions: str = Field(min_length=1)
    topicIds: list[str] = Field(min_length=1)
    sourceRefs: list[SourceRef] = Field(default_factory=list)
    difficulty: Difficulty = Difficulty.STANDARD
    difficultyScore: int | None = Field(default=None, ge=1, le=5)
    densityScore: int | None = Field(default=None, ge=1, le=5)
    productionDemandScore: int | None = Field(default=None, ge=1, le=5)
    estimateConfidence: EstimateConfidence = EstimateConfidence.MEDIUM
    effortMinMinutes: int | None = Field(default=None, gt=0, le=1440)
    effortLikelyMinutes: int | None = Field(default=None, gt=0, le=1440)
    effortMaxMinutes: int | None = Field(default=None, gt=0, le=1440)
    completionCriteria: list[str] = Field(default_factory=list)
    splitAllowed: bool = True
    continuityGroup: str | None = None
    dependencies: list[str] = Field(default_factory=list)
    status: StudyTaskStatus = StudyTaskStatus.NOT_STARTED
    scheduledDate: str | None = None

    @model_validator(mode="after")
    def validate_workload_fields(self):
        minimum_floor = TASK_TYPE_MINIMUMS.get(self.taskType, 5)
        self.minimumUsefulMinutes = max(self.minimumUsefulMinutes, minimum_floor)
        minutes = self.estimatedMinutes or self.durationMinutes or self.minimumUsefulMinutes
        minutes = max(minutes, self.minimumUsefulMinutes)
        self.estimatedMinutes = minutes
        self.durationMinutes = minutes
        likely = self.effortLikelyMinutes or minutes
        likely = max(likely, self.minimumUsefulMinutes)
        min_minutes = self.effortMinMinutes or max(self.minimumUsefulMinutes, round(likely * 0.7))
        max_minutes = self.effortMaxMinutes or max(likely, round(likely * 1.35))
        min_minutes = min(max(min_minutes, self.minimumUsefulMinutes), likely)
        max_minutes = max(max_minutes, likely)
        self.effortMinMinutes = min_minutes
        self.effortLikelyMinutes = likely
        self.effortMaxMinutes = max_minutes
        self.difficultyScore = self.difficultyScore or DIFFICULTY_SCORE_DEFAULTS.get(self.difficulty, 3)
        self.densityScore = self.densityScore or TASK_TYPE_DENSITY_DEFAULTS.get(self.taskType, 3)
        self.productionDemandScore = self.productionDemandScore or TASK_TYPE_PRODUCTION_DEFAULTS.get(self.taskType, 3)
        self.completionCriteria = [item.strip() for item in self.completionCriteria if item.strip()]
        return self


class ExtractionWarning(BaseModel):
    model_config = ConfigDict(extra="ignore")
    type: str = Field(min_length=1)
    message: str = Field(min_length=1)
    blockId: str | None = None
    documentId: str | None = None
    startPage: int | None = Field(default=None, ge=1)
    endPage: int | None = Field(default=None, ge=1)


class GeneratedPlan(BaseModel):
    model_config = ConfigDict(extra="ignore")
    planVersion: int = Field(default=2, ge=1)
    title: str = Field(default="Study plan", max_length=80, min_length=1)
    sourceDocuments: list[SourceDocumentInfo] = Field(default_factory=list)
    topics: list[Topic] = Field(min_length=1)
    blocks: list[StudyBlock] = Field(min_length=1)
    extractionWarnings: list[ExtractionWarning] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_order_and_references(self):
        topic_ids = {topic.id for topic in self.topics}
        if len(topic_ids) != len(self.topics):
            raise ValueError("Topic IDs must be unique")
        if sorted(t.order for t in self.topics) != list(range(1, len(self.topics) + 1)):
            raise ValueError("Topic order must be contiguous")
        if sorted(b.order for b in self.blocks) != list(range(1, len(self.blocks) + 1)):
            raise ValueError("Block order must be contiguous")
        if any(not set(block.topicIds).issubset(topic_ids) for block in self.blocks):
            raise ValueError("Blocks must reference known topics")

        document_ids = {document.id for document in self.sourceDocuments}
        if document_ids:
            for block in self.blocks:
                for ref in block.sourceRefs:
                    if ref.documentId not in document_ids:
                        raise ValueError("Block source refs must reference known documents")
        return self

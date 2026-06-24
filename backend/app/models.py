from __future__ import annotations

from enum import StrEnum
from pydantic import BaseModel, ConfigDict, Field, model_validator


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
    goal: str
    deadline: str
    deadlineDate: str | None = None
    dailyStudyMinutes: int = Field(gt=0, le=1440)
    studyDays: list[str] = Field(min_length=1)


class CreatePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentId: str
    requestId: str
    setup: Setup


class Topic(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    order: int = Field(ge=1)


class StudyBlock(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    order: int = Field(ge=1)
    durationMinutes: int = Field(gt=0, le=1440)
    minimumUsefulMinutes: int = Field(default=10, gt=0, le=1440)
    priority: str = Field(default="MEDIUM", pattern="^(HIGH|MEDIUM|LOW)$")
    taskType: str = Field(default="REVIEW", pattern="^(LEARN|PRACTICE|REVIEW|QUIZ|MOCK_EXAM|SKIM)$")
    priorityReason: str = Field(default="Supports the study goal", min_length=1)
    isSkippable: bool = True
    instructions: str = Field(min_length=1)
    topicIds: list[str] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_useful_duration(self):
        minimums = {"LEARN": 20, "PRACTICE": 15, "REVIEW": 10, "QUIZ": 10, "MOCK_EXAM": 30, "SKIM": 5}
        if self.durationMinutes < self.minimumUsefulMinutes:
            raise ValueError("Duration cannot be below the minimum useful duration")
        if self.minimumUsefulMinutes < minimums[self.taskType]:
            raise ValueError("Minimum useful duration is too short for this task type")
        return self


class GeneratedPlan(BaseModel):
    model_config = ConfigDict(extra="ignore")
    title: str = Field(default="Study plan", max_length=80, min_length=1)
    topics: list[Topic] = Field(min_length=1)
    blocks: list[StudyBlock] = Field(min_length=1)

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
        return self


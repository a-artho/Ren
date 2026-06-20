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
    instructions: str = Field(min_length=1)
    topicIds: list[str] = Field(min_length=1)


class GeneratedPlan(BaseModel):
    model_config = ConfigDict(extra="ignore")
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


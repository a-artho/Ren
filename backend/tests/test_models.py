import pytest
from pydantic import ValidationError
from app.models import CreatePlanRequest, GeneratedPlan, Setup

def test_plan_rejects_unknown_topic_reference():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
          "blocks":[{"id":"b1","title":"Block","order":1,"durationMinutes":30,"instructions":"Read","topicIds":["missing"]}]})

def test_plan_rejects_non_contiguous_order():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":2}],
          "blocks":[{"id":"b1","title":"Block","order":1,"durationMinutes":30,"instructions":"Read","topicIds":["t1"]}]})

def test_plan_rejects_duration_below_minimum_useful_time():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
          "blocks":[{"id":"b1","title":"Block","order":1,"durationMinutes":10,
            "minimumUsefulMinutes":20,"priority":"HIGH","taskType":"LEARN",
            "priorityReason":"Foundation","isSkippable":False,"instructions":"Read","topicIds":["t1"]}]})

def test_create_plan_request_single_document():
    req = CreatePlanRequest(
        documentIds=["doc-1"],
        requestId="req-1",
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    assert len(req.documentIds) == 1
    assert req.documentIds[0] == "doc-1"

def test_create_plan_request_multiple_documents():
    req = CreatePlanRequest(
        documentIds=["doc-1", "doc-2", "doc-3"],
        requestId="req-1",
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    assert len(req.documentIds) == 3

def test_create_plan_request_empty_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[],
            requestId="req-1",
            setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
        )

def test_create_plan_request_too_many_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[f"doc-{i}" for i in range(11)],
            requestId="req-1",
            setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
        )

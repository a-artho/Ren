import pytest
from pydantic import ValidationError
from app.models import CreatePlanRequest, GeneratedPlan, Setup

def test_plan_rejects_unknown_topic_reference():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
          "blocks":[{"id":"b1","title":"Block","order":1,
                     "effortMinMinutes":20,"effortLikelyMinutes":30,"effortMaxMinutes":40,
                     "instructions":"Read","topicIds":["missing"]}]})

def test_plan_rejects_non_contiguous_order():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":2}],
          "blocks":[{"id":"b1","title":"Block","order":1,
                     "effortMinMinutes":20,"effortLikelyMinutes":30,"effortMaxMinutes":40,
                     "instructions":"Read","topicIds":["t1"]}]})

def test_plan_normalizes_effort_range_order():
    plan = GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
      "blocks":[{"id":"b1","title":"Block","order":1,
        "effortMinMinutes":40,"effortLikelyMinutes":20,"effortMaxMinutes":10,
        "taskType":"CONCEPT",
        "instructions":"Read","topicIds":["t1"]}]})

    assert plan.blocks[0].effortMinMinutes == 40
    assert plan.blocks[0].effortLikelyMinutes == 40
    assert plan.blocks[0].effortMaxMinutes == 40

def test_plan_fills_score_defaults():
    plan = GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
      "blocks":[{"id":"b1","title":"Block","order":1,
        "effortMinMinutes":20,"effortLikelyMinutes":30,"effortMaxMinutes":40,
        "taskType":"CONCEPT",
        "instructions":"Read","topicIds":["t1"]}]})

    assert plan.blocks[0].difficultyScore == 3
    assert plan.blocks[0].densityScore == 3
    assert plan.blocks[0].productionDemandScore == 3

def test_custom_blocks_can_stay_low_effort():
    plan = GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
      "blocks":[{"id":"b1","title":"Admin note","order":1,
        "effortMinMinutes":3,"effortLikelyMinutes":5,"effortMaxMinutes":8,
        "taskType":"CUSTOM",
        "instructions":"Check the note.","topicIds":["t1"]}]})

    assert plan.blocks[0].effortLikelyMinutes == 5

def test_create_plan_request_single_document():
    req = CreatePlanRequest(
        documentIds=["doc-1"],
        requestId="req-1",
        setup=Setup(goal="PrepareForExam", planTitle="HCI final", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    assert len(req.documentIds) == 1
    assert req.documentIds[0] == "doc-1"
    assert req.setup.planTitle == "HCI final"

def test_create_plan_request_multiple_documents():
    req = CreatePlanRequest(
        documentIds=["doc-1", "doc-2", "doc-3"],
        requestId="req-1",
        setup=Setup(goal="PrepareForExam", planTitle="HCI final", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    assert len(req.documentIds) == 3

def test_create_plan_request_empty_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[],
            requestId="req-1",
            setup=Setup(goal="PrepareForExam", planTitle="HCI final", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
        )

def test_create_plan_request_too_many_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[f"doc-{i}" for i in range(11)],
            requestId="req-1",
            setup=Setup(goal="PrepareForExam", planTitle="HCI final", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
        )

def test_create_plan_request_rejects_duplicate_or_blank_document_ids():
    setup = Setup(
        goal="PrepareForExam",
        planTitle="HCI final",
        deadline="InOneWeek",
        dailyStudyMinutes=30,
        studyDays=["Monday"],
    )

    with pytest.raises(ValidationError, match="documentIds must be unique"):
        CreatePlanRequest(documentIds=["doc-1", "doc-1"], requestId="req-1", setup=setup)
    with pytest.raises(ValidationError, match="documentIds must be non-empty strings"):
        CreatePlanRequest(documentIds=["doc-1", "  "], requestId="req-1", setup=setup)

def test_setup_rejects_invalid_custom_deadline_date():
    with pytest.raises(ValidationError):
        Setup(
            goal="PrepareForExam",
            planTitle="HCI final",
            deadline="ChooseDate",
            deadlineDate="not-a-date",
            dailyStudyMinutes=30,
            studyDays=["Monday"],
        )

def test_setup_rejects_invalid_or_duplicate_study_days():
    with pytest.raises(ValidationError):
        Setup(
            goal="PrepareForExam",
            planTitle="HCI final",
            deadline="InOneWeek",
            dailyStudyMinutes=30,
            studyDays=["Moonday"],
        )
    with pytest.raises(ValidationError):
        Setup(
            goal="PrepareForExam",
            planTitle="HCI final",
            deadline="InOneWeek",
            dailyStudyMinutes=30,
            studyDays=["Monday", "Monday"],
        )

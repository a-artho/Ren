import pytest
from pydantic import ValidationError
from app.models import GeneratedPlan

def test_plan_rejects_unknown_topic_reference():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":1}],
          "blocks":[{"id":"b1","title":"Block","order":1,"durationMinutes":30,"instructions":"Read","topicIds":["missing"]}]})

def test_plan_rejects_non_contiguous_order():
    with pytest.raises(ValidationError):
        GeneratedPlan.model_validate({"topics":[{"id":"t1","title":"One","order":2}],
          "blocks":[{"id":"b1","title":"Block","order":1,"durationMinutes":30,"instructions":"Read","topicIds":["t1"]}]})

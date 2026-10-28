from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class HistoryMessage(StrictModel):
    role: Literal["farmer", "assistant"]
    text: str = Field(min_length=1, max_length=1200)


class FarmContext(StrictModel):
    plots: list[str] = Field(default_factory=list, max_length=12)
    plans: list[str] = Field(default_factory=list, max_length=12)
    upcoming_activities: list[str] = Field(default_factory=list, max_length=12)
    recent_harvests: list[str] = Field(default_factory=list, max_length=6)

    @field_validator("plots", "plans", "upcoming_activities", "recent_harvests")
    @classmethod
    def bound_items(cls, values: list[str]) -> list[str]:
        if any(len(value) > 180 for value in values):
            raise ValueError("context item is too long")
        return values


class ChatRequest(StrictModel):
    question: str = Field(min_length=2, max_length=1200)
    language: Literal["en", "hi"] = "en"
    history: list[HistoryMessage] = Field(default_factory=list, max_length=10)
    farm_context: FarmContext = Field(default_factory=FarmContext)


class ChatResponse(StrictModel):
    text: str = Field(min_length=1, max_length=12_000)
    provider: str = Field(min_length=1, max_length=80)
    model: str = Field(min_length=1, max_length=160)


class CropAnalysisRequest(StrictModel):
    image_base64: str = Field(min_length=32, max_length=7_000_000)
    mime_type: Literal["image/jpeg", "image/png", "image/webp"]
    crop_name: str = Field(min_length=2, max_length=60)
    symptoms: list[str] = Field(default_factory=list, max_length=20)
    affected_parts: list[str] = Field(default_factory=list, max_length=10)
    observations: str = Field(default="", max_length=800)
    language: Literal["en", "hi"] = "en"

    @field_validator("symptoms", "affected_parts")
    @classmethod
    def bound_labels(cls, values: list[str]) -> list[str]:
        if any(not value or len(value) > 80 for value in values):
            raise ValueError("crop label is invalid")
        return values


class CropAnalysisResponse(StrictModel):
    visible_symptoms: list[str] = Field(max_length=20)
    possible_causes: list[str] = Field(max_length=12)
    supporting_observations: list[str] = Field(max_length=20)
    uncertainty: str = Field(min_length=1, max_length=300)
    evidence_needed: list[str] = Field(max_length=12)
    safe_actions: list[str] = Field(max_length=12)
    expert_recommendation: str = Field(min_length=1, max_length=500)
    provider: str
    model: str

    @field_validator("visible_symptoms", "possible_causes", "supporting_observations", "evidence_needed", "safe_actions")
    @classmethod
    def bound_output_items(cls, values: list[str]) -> list[str]:
        if any(not value or len(value) > 300 for value in values):
            raise ValueError("provider output item is invalid")
        return values


class MandiRecord(StrictModel):
    commodity: str = Field(min_length=1, max_length=80)
    state: str = Field(min_length=1, max_length=80)
    district: str = Field(min_length=1, max_length=80)
    market: str = Field(min_length=1, max_length=120)
    variety: str = Field(default="", max_length=80)
    min_price: float = Field(ge=0)
    max_price: float = Field(gt=0)
    modal_price: float = Field(gt=0)
    arrival_date: date
    unit: Literal["INR/quintal"] = "INR/quintal"


class MandiResponse(StrictModel):
    source: str = "AGMARKNET via data.gov.in"
    fetched_at: int
    records: list[MandiRecord]


class HealthResponse(StrictModel):
    status: Literal["ok"] = "ok"
    ai_enabled: bool
    vision_enabled: bool
    mandi_enabled: bool

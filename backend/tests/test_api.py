import base64
import asyncio
from io import BytesIO

import httpx
from fastapi import HTTPException
from PIL import Image
from pydantic import ValidationError
from starlette.middleware.cors import CORSMiddleware

from app.config import Settings
from app.main import RateLimiter, create_app
from app.models import ChatRequest, CropAnalysisRequest
from app.providers import DataGovMandiClient, MockAiProvider, OpenAiCompatibleProvider, ProviderUnavailable, parse_mandi_records


async def verified(_token: str) -> dict:
    return {"uid": "test-farmer"}


def configured(**changes) -> Settings:
    values = Settings().__dict__ | {
        "live_ai_enabled": True,
        "ai_api_key": "test-only",
        "ai_model": "mock",
        "ai_vision_model": "mock",
        "rate_limit_per_minute": 12,
    } | changes
    return Settings(**values)


def image_base64() -> str:
    output = BytesIO()
    Image.new("RGB", (96, 96), "green").save(output, format="JPEG")
    return base64.b64encode(output.getvalue()).decode()


def endpoint(app, path: str):
    return next(route.endpoint for route in app.routes if getattr(route, "path", None) == path)


def auth_dependency(app, path: str):
    route = next(route for route in app.routes if getattr(route, "path", None) == path)
    return route.dependant.dependencies[0].call


def test_health_is_public_and_reports_disabled_by_default():
    response = asyncio.run(endpoint(create_app(Settings()), "/health")())
    assert response.status == "ok"
    assert response.ai_enabled is False


def test_production_disables_docs_and_cors_is_explicit_only():
    app = create_app(configured(environment="production", cors_allowed_origins=("https://admin.example",)), MockAiProvider(), verified)
    assert app.docs_url is None
    cors = next(item for item in app.user_middleware if item.cls is CORSMiddleware)
    assert cors.kwargs["allow_origins"] == ["https://admin.example"]
    try:
        configured(cors_allowed_origins=("*",))
        raise AssertionError("wildcard CORS was accepted")
    except ValueError:
        pass


def test_paid_routes_require_firebase_token():
    dependency = auth_dependency(create_app(configured(), MockAiProvider(), verified), "/v1/chat")
    try:
        asyncio.run(dependency(None))
        raise AssertionError("missing token was accepted")
    except HTTPException as error:
        assert error.status_code == 401


def test_chat_and_crop_analysis_use_explicit_test_mock():
    app = create_app(configured(), MockAiProvider(), verified)
    chat = asyncio.run(endpoint(app, "/v1/chat")(ChatRequest(question="Help me plan wheat", language="en"), "test-farmer"))
    assert chat.provider == "mock-test-provider"
    crop = asyncio.run(endpoint(app, "/v1/crop-analysis")(CropAnalysisRequest(
        image_base64=image_base64(), mime_type="image/jpeg", crop_name="Wheat",
        symptoms=["yellowing"], affected_parts=["leaves"], observations="",
    ), "test-farmer"))
    assert crop.uncertainty == "Mock result; not a diagnosis."


def test_limits_and_rate_limit_are_enforced():
    limiter = RateLimiter(1)
    limiter.check("test-farmer")
    try:
        limiter.check("test-farmer")
        raise AssertionError("rate limit was not enforced")
    except HTTPException as error:
        assert error.status_code == 429
    try:
        ChatRequest(question="x" * 1201, language="en")
        raise AssertionError("request length was not enforced")
    except ValidationError:
        pass


def test_mandi_parser_rejects_invalid_prices_and_parses_official_fields():
    records = parse_mandi_records([
        {"commodity": "Wheat", "state": "Rajasthan", "district": "Jaipur", "market": "Jaipur",
         "variety": "Local", "min_price": "2300", "max_price": "2500", "modal_price": "2400", "arrival_date": "22/10/2026"},
        {"commodity": "Wheat", "state": "Rajasthan", "district": "Jaipur", "market": "Bad",
         "min_price": "2600", "max_price": "2500", "modal_price": "2550", "arrival_date": "22/10/2026"},
    ])
    assert len(records) == 1
    assert records[0].modal_price == 2400
    assert records[0].unit == "INR/quintal"


def test_ai_retries_one_transient_failure_without_exposing_provider_body():
    calls = 0

    def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        if calls == 1:
            return httpx.Response(503, text="provider-internal-details")
        return httpx.Response(200, json={
            "model": "mock", "choices": [{"message": {"content": "Safe answer"}}],
            "usage": {"prompt_tokens": 4, "completion_tokens": 2, "total_tokens": 6},
        })

    provider = OpenAiCompatibleProvider(configured(ai_max_retries=1), httpx.MockTransport(handler))
    text, model = asyncio.run(provider.chat(ChatRequest(question="Help with wheat")))
    assert (text, model, calls) == ("Safe answer", "mock", 2)


def test_mandi_retries_get_and_provider_failures_are_generic():
    calls = 0

    def handler(_request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(503, text="upstream-secret-details")

    client = DataGovMandiClient(configured(data_gov_api_key="test-only", mandi_max_retries=1), httpx.MockTransport(handler))
    try:
        asyncio.run(client.fetch("Wheat", "Rajasthan", "Jaipur"))
        raise AssertionError("provider failure was accepted")
    except ProviderUnavailable as error:
        assert str(error) == "mandi provider is unavailable"
        assert calls == 2


def test_crop_image_rejects_invalid_content():
    request = CropAnalysisRequest(
        image_base64=base64.b64encode(b"not-an-image" * 4).decode(), mime_type="image/jpeg",
        crop_name="Wheat", symptoms=[], affected_parts=[], observations="",
    )
    try:
        asyncio.run(MockAiProvider().analyze_crop(request))
        raise AssertionError("invalid image was accepted")
    except ValueError as error:
        assert str(error) == "invalid image"

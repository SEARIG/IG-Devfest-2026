import json
import logging
import time
import uuid
from collections import defaultdict, deque
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .auth import FirebaseTokenVerifier, TokenVerifier
from .config import Settings
from .models import ChatRequest, ChatResponse, CropAnalysisRequest, CropAnalysisResponse, HealthResponse, MandiResponse
from .providers import AiProvider, DataGovMandiClient, OpenAiCompatibleProvider, ProviderUnavailable


logger = logging.getLogger("agro_ai.operations")


class RateLimiter:
    # shortcut: per-process only, replace with a shared limiter before multiple workers or instances.
    def __init__(self, limit: int):
        self.limit = limit
        self.requests: dict[str, deque[float]] = defaultdict(deque)

    def check(self, uid: str) -> None:
        now = time.monotonic()
        bucket = self.requests[uid]
        while bucket and bucket[0] < now - 60:
            bucket.popleft()
        if len(bucket) >= self.limit:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Rate limit exceeded")
        bucket.append(now)


def create_app(
    settings: Settings | None = None,
    provider: AiProvider | None = None,
    token_verifier: TokenVerifier | None = None,
    mandi_client: DataGovMandiClient | None = None,
) -> FastAPI:
    config = settings or Settings()
    logging.getLogger("agro_ai").setLevel(logging.INFO)
    ai = provider or (OpenAiCompatibleProvider(config) if config.ai_ready else None)
    verify = token_verifier or FirebaseTokenVerifier(config.firebase_project_id)
    mandi = mandi_client or (DataGovMandiClient(config) if config.mandi_ready else None)
    limiter = RateLimiter(config.rate_limit_per_minute)
    app = FastAPI(
        title="AGRO AI Backend",
        version="1.0.0",
        docs_url=None if config.environment == "production" else "/docs",
        redoc_url=None if config.environment == "production" else "/redoc",
        openapi_url=None if config.environment == "production" else "/openapi.json",
    )
    if config.cors_allowed_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=list(config.cors_allowed_origins),
            allow_credentials=False,
            allow_methods=["GET", "POST"],
            allow_headers=["Authorization", "Content-Type"],
            expose_headers=["X-Request-ID"],
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(_request: Request, error: RequestValidationError) -> JSONResponse:
        details = [{key: item[key] for key in ("loc", "msg", "type") if key in item} for item in error.errors()]
        return JSONResponse(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, content={"detail": details})

    @app.middleware("http")
    async def operations_log(request: Request, call_next):
        request_id = uuid.uuid4().hex
        started = time.monotonic()
        try:
            response = await call_next(request)
            status_code = response.status_code
        except Exception:
            status_code = 500
            logger.error(json.dumps({"event": "request_failed", "request_id": request_id, "method": request.method, "path": request.url.path}))
            raise
        finally:
            logger.info(json.dumps({
                "event": "request_complete", "request_id": request_id, "method": request.method,
                "path": request.url.path, "status": status_code,
                "duration_ms": round((time.monotonic() - started) * 1000, 1),
            }))
        response.headers["X-Request-ID"] = request_id
        return response

    async def current_uid(authorization: Annotated[str | None, Header()] = None) -> str:
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Firebase ID token required")
        try:
            claims = await verify(authorization.removeprefix("Bearer ").strip())
            uid = claims.get("uid") or claims.get("sub")
            if not isinstance(uid, str) or not uid:
                raise ValueError("missing uid")
        except Exception as error:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Firebase ID token") from error
        limiter.check(uid)
        return uid

    @app.get("/health", response_model=HealthResponse)
    async def health() -> HealthResponse:
        return HealthResponse(ai_enabled=config.ai_ready, vision_enabled=config.vision_ready, mandi_enabled=config.mandi_ready)

    @app.post("/v1/chat", response_model=ChatResponse)
    async def chat(request: ChatRequest, _uid: str = Depends(current_uid)) -> ChatResponse:
        if ai is None or not config.ai_ready:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Live AI is disabled")
        try:
            text, model = await ai.chat(request)
            return ChatResponse(text=text, provider=ai.name, model=model)
        except ProviderUnavailable as error:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(error)) from error

    @app.post("/v1/crop-analysis", response_model=CropAnalysisResponse)
    async def crop_analysis(request: CropAnalysisRequest, _uid: str = Depends(current_uid)) -> CropAnalysisResponse:
        if ai is None or not config.vision_ready:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Live vision AI is disabled")
        try:
            return await ai.analyze_crop(request)
        except ValueError as error:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(error)) from error
        except ProviderUnavailable as error:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(error)) from error

    @app.get("/v1/mandi", response_model=MandiResponse)
    async def mandi_prices(
        commodity: Annotated[str, Query(min_length=2, max_length=80)],
        state_name: Annotated[str, Query(alias="state", min_length=2, max_length=80)],
        district: Annotated[str, Query(min_length=2, max_length=80)],
        _uid: str = Depends(current_uid),
    ) -> MandiResponse:
        if mandi is None or not config.mandi_ready:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Mandi provider is disabled")
        try:
            records = await mandi.fetch(commodity, state_name, district)
            return MandiResponse(fetched_at=int(time.time() * 1000), records=records)
        except ProviderUnavailable as error:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(error)) from error

    return app


app = create_app()

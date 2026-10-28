import os
from dataclasses import dataclass


def _flag(name: str, default: bool = False) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


def _origins() -> tuple[str, ...]:
    return tuple(value.strip().rstrip("/") for value in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",") if value.strip())


@dataclass(frozen=True)
class Settings:
    environment: str = os.getenv("ENVIRONMENT", "development").strip().lower()
    firebase_project_id: str = os.getenv("FIREBASE_PROJECT_ID", "agro-ai-34b4a")
    cors_allowed_origins: tuple[str, ...] = _origins()
    live_ai_enabled: bool = _flag("LIVE_AI_ENABLED")
    ai_base_url: str = os.getenv("AI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    ai_api_key: str = os.getenv("AI_API_KEY", "")
    ai_model: str = os.getenv("AI_MODEL", "")
    ai_vision_model: str = os.getenv("AI_VISION_MODEL", "")
    ai_timeout_seconds: float = max(1.0, min(float(os.getenv("AI_TIMEOUT_SECONDS", "25")), 60.0))
    ai_max_retries: int = max(0, min(int(os.getenv("AI_MAX_RETRIES", "0")), 1))
    ai_max_output_tokens: int = max(64, min(int(os.getenv("AI_MAX_OUTPUT_TOKENS", "700")), 1200))
    rate_limit_per_minute: int = max(1, min(int(os.getenv("RATE_LIMIT_PER_MINUTE", "12")), 60))
    data_gov_api_key: str = os.getenv("DATA_GOV_API_KEY", "")
    mandi_timeout_seconds: float = max(1.0, min(float(os.getenv("MANDI_TIMEOUT_SECONDS", "20")), 60.0))
    mandi_max_retries: int = max(0, min(int(os.getenv("MANDI_MAX_RETRIES", "1")), 2))

    def __post_init__(self) -> None:
        if self.environment not in {"development", "test", "production"}:
            raise ValueError("ENVIRONMENT must be development, test, or production")
        if "*" in self.cors_allowed_origins:
            raise ValueError("CORS_ALLOWED_ORIGINS must list explicit origins")
        if self.ai_ready and not self.ai_base_url.startswith("https://") and not self.ai_base_url.startswith(("http://127.0.0.1", "http://localhost")):
            raise ValueError("AI_BASE_URL must use HTTPS outside local development")

    @property
    def ai_ready(self) -> bool:
        return self.live_ai_enabled and bool(self.ai_api_key and self.ai_model)

    @property
    def vision_ready(self) -> bool:
        return self.ai_ready and bool(self.ai_vision_model or self.ai_model)

    @property
    def mandi_ready(self) -> bool:
        return bool(self.data_gov_api_key)

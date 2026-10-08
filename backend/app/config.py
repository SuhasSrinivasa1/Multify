from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    environment: str = "development"
    bind_host: str = "0.0.0.0"
    bind_port: int = 8080

    phone_client_id: str = "multify-phone"
    phone_hmac_secret: str
    max_clock_skew_seconds: int = 90
    nonce_ttl_seconds: int = 600

    groww_api_key: str
    groww_totp_secret: str
    groww_base_url: str = "https://api.groww.in/"

    database_path: str = "data/live_gateway.sqlite3"
    kill_switch: bool = False
    max_order_rate_per_second: int = 8
    max_order_notional_rupees: float = 200_000.0
    max_symbol_net_notional_rupees: float = 250_000.0
    max_daily_new_notional_rupees: float = 500_000.0
    max_open_symbols: int = 2
    daily_loss_halt_rupees: float = 2_500.0
    market_open_hhmm: str = "09:15"
    last_new_order_hhmm: str = "15:20"
    force_flat_hhmm: str = "15:24"

    @property
    def db_path(self) -> Path:
        path = Path(self.database_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        return path


@lru_cache
def get_settings() -> Settings:
    return Settings()

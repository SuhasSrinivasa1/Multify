from __future__ import annotations

from typing import Any, Literal
from pydantic import BaseModel, Field


class ApiError(BaseModel):
    code: str
    message: str


class Envelope(BaseModel):
    status: str = "SUCCESS"
    payload: Any | None = None
    error: ApiError | None = None


class GatewayHealth(BaseModel):
    status: str
    broker_authenticated: bool
    kill_switch: bool
    db_ok: bool
    version: str = "6.1.0-long-holdings-gateway"


class HoldingOrderIntent(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=96)
    strategy_id: str = Field(min_length=1, max_length=96)
    symbol: str = Field(pattern=r"^[A-Z0-9&\-]{1,32}$")
    action: Literal["BUY_HOLDING", "SELL_OWNED_HOLDING"]
    quantity: int = Field(gt=0, le=100000)
    expected_ltp: float = Field(default=0.0, ge=0.0)
    reason: str = Field(default="", max_length=512)


class KillSwitchRequest(BaseModel):
    enabled: bool
    reason: str = Field(default="manual", max_length=256)


class MarketDataRequest(BaseModel):
    symbol: str = Field(pattern=r"^[A-Z0-9&\-]{1,32}$")

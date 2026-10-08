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
    version: str = "4.5.0-live-gateway"


class OrderIntent(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=96)
    strategy_id: str = Field(min_length=1, max_length=96)
    symbol: str = Field(pattern=r"^[A-Z0-9&\-]{1,32}$")
    side: Literal["BUY", "SELL"]
    quantity: int = Field(gt=0, le=100000)
    product: Literal["MIS"] = "MIS"
    order_type: Literal["MARKET", "LIMIT"] = "MARKET"
    price: float = Field(default=0.0, ge=0.0)
    trigger_price: float = Field(default=0.0, ge=0.0)
    reason: str = Field(default="", max_length=512)
    wave: int = Field(default=1, ge=1, le=10)
    priority: Literal["NEW_MULTIFY", "FIRST_WAVE_EXIT", "FIRST_WAVE_ENTRY", "WAVE", "FORCE_FLAT"] = "WAVE"
    expected_ltp: float = Field(default=0.0, ge=0.0)


class OcoIntent(BaseModel):
    idempotency_key: str = Field(min_length=8, max_length=96)
    strategy_id: str = Field(min_length=1, max_length=96)
    symbol: str = Field(pattern=r"^[A-Z0-9&\-]{1,32}$")
    quantity: int = Field(gt=0, le=100000)
    net_position_quantity: int
    exit_side: Literal["BUY", "SELL"]
    target_price: float = Field(gt=0)
    stop_price: float = Field(gt=0)


class KillSwitchRequest(BaseModel):
    enabled: bool
    reason: str = Field(default="manual", max_length=256)


class MarketDataRequest(BaseModel):
    symbol: str = Field(pattern=r"^[A-Z0-9&\-]{1,32}$")

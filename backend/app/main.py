from __future__ import annotations

from contextlib import asynccontextmanager
from fastapi import Depends, FastAPI, HTTPException, Query

from .broker import BrokerError, GrowwBroker
from .config import get_settings
from .models import Envelope, GatewayHealth, KillSwitchRequest, OcoIntent, OrderIntent
from .risk import RiskEngine, RiskViolation
from .security import require_phone_auth
from .service import LiveTradingService
from .storage import audit, init_db, read_risk_value, write_risk_value


broker = GrowwBroker()
risk = RiskEngine()
service = LiveTradingService(broker, risk)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield
    await broker.close()


app = FastAPI(title="Multify Live Gateway", version="4.5.0", lifespan=lifespan)


def ok(payload=None) -> dict:
    return {"status": "SUCCESS", "payload": payload, "error": None}


def fail(code: str, message: str, status: int = 400):
    raise HTTPException(status_code=status, detail={"code": code, "message": message})


@app.get("/health", response_model=GatewayHealth)
async def health():
    authenticated = False
    try:
        await broker.ensure_token()
        authenticated = True
    except Exception:
        pass
    settings = get_settings()
    return GatewayHealth(status="ok", broker_authenticated=authenticated, kill_switch=settings.kill_switch or read_risk_value("kill_switch", "0") == "1", db_ok=True)


@app.get("/v1/user/detail", dependencies=[Depends(require_phone_auth)])
async def profile():
    try:
        return await broker.profile()
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/margins/detail/user", dependencies=[Depends(require_phone_auth)])
async def margins():
    try:
        return await broker.margins()
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/positions/user", dependencies=[Depends(require_phone_auth)])
async def positions():
    try:
        return await broker.positions()
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/holdings/user", dependencies=[Depends(require_phone_auth)])
async def holdings():
    try:
        return await broker.holdings()
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/live-data/quote", dependencies=[Depends(require_phone_auth)])
async def quote(trading_symbol: str = Query(...)):
    try:
        return await broker.quote(trading_symbol.upper())
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/historical/candles", dependencies=[Depends(require_phone_auth)])
async def candles(groww_symbol: str, start_time: str, end_time: str, candle_interval: str = "5minute"):
    symbol = groww_symbol.split("-", 1)[-1].upper()
    try:
        return await broker.candles(symbol, start_time, end_time, candle_interval)
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.post("/v1/gateway/orders", dependencies=[Depends(require_phone_auth)])
async def order(intent: OrderIntent):
    try:
        return ok(await service.execute_order(intent))
    except RiskViolation as exc:
        audit("RISK", "ORDER_BLOCKED", {"symbol": intent.symbol, "reason": str(exc), "priority": intent.priority})
        fail("RISK_BLOCK", str(exc), 409)
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.get("/v1/gateway/orders/{order_id}", dependencies=[Depends(require_phone_auth)])
async def order_status(order_id: str):
    try:
        return await broker.order_status(order_id)
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.post("/v1/gateway/oco", dependencies=[Depends(require_phone_auth)])
async def oco(intent: OcoIntent):
    try:
        return ok(await service.execute_oco(intent))
    except (RiskViolation, BrokerError) as exc:
        fail("OCO_FAILED", str(exc), 409)


@app.put("/v1/gateway/oco/{smart_order_id}", dependencies=[Depends(require_phone_auth)])
async def modify_oco(smart_order_id: str, target_price: float, stop_price: float, quantity: int):
    try:
        return await broker.modify_oco(smart_order_id, {
            "smart_order_type": "OCO", "segment": "CASH", "duration": "DAY", "quantity": quantity, "product_type": "MIS",
            "target": {"trigger_price": f"{target_price:.2f}"}, "stop_loss": {"trigger_price": f"{stop_price:.2f}"},
        })
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.post("/v1/gateway/oco/{smart_order_id}/cancel", dependencies=[Depends(require_phone_auth)])
async def cancel_oco(smart_order_id: str):
    try:
        return await broker.cancel_oco(smart_order_id)
    except BrokerError as exc:
        fail("BROKER", str(exc), 502)


@app.post("/v1/gateway/kill-switch", dependencies=[Depends(require_phone_auth)])
async def kill_switch(req: KillSwitchRequest):
    write_risk_value("kill_switch", "1" if req.enabled else "0")
    audit("RISK", "KILL_SWITCH", {"enabled": req.enabled, "reason": req.reason})
    return ok({"enabled": req.enabled})

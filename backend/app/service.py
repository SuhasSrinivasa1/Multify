from __future__ import annotations

import hashlib
import json
import time
from typing import Any

from .broker import GrowwBroker
from .models import OcoIntent, OrderIntent
from .risk import RiskEngine, RiskViolation
from .storage import audit, db_connection


def _json_hash(data: dict) -> str:
    raw = json.dumps(data, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def _payload(envelope: dict) -> dict:
    if str(envelope.get("status", "SUCCESS")).upper() == "SUCCESS" and "payload" in envelope:
        return envelope.get("payload") or {}
    return envelope


class LiveTradingService:
    def __init__(self, broker: GrowwBroker, risk: RiskEngine) -> None:
        self.broker = broker
        self.risk = risk

    def _idem_begin(self, key: str, request: dict) -> dict | None:
        h = _json_hash(request)
        now = int(time.time())
        with db_connection() as db:
            row = db.execute("SELECT request_hash,response_json,status FROM idempotency WHERE idem_key=?", (key,)).fetchone()
            if row:
                if row["request_hash"] != h:
                    raise RiskViolation("idempotency key reused with different request")
                if row["status"] == "DONE" and row["response_json"]:
                    return json.loads(row["response_json"])
                raise RiskViolation("matching request is already in progress")
            db.execute("INSERT INTO idempotency(idem_key,request_hash,status,created_at,updated_at) VALUES(?,?,?,?,?)", (key,h,"IN_PROGRESS",now,now))
            db.commit()
        return None

    def _idem_finish(self, key: str, response: dict) -> None:
        now = int(time.time())
        with db_connection() as db:
            db.execute("UPDATE idempotency SET response_json=?, status='DONE', updated_at=? WHERE idem_key=?", (json.dumps(response),now,key))
            db.commit()

    def _idem_fail(self, key: str) -> None:
        now = int(time.time())
        with db_connection() as db:
            db.execute("UPDATE idempotency SET status='FAILED', updated_at=? WHERE idem_key=?", (now,key))
            db.commit()

    async def execute_order(self, intent: OrderIntent) -> dict:
        request = intent.model_dump()
        replay = self._idem_begin(intent.idempotency_key, request)
        if replay is not None:
            return replay
        try:
            await self.risk.validate(intent)
            await self._reconcile_symbol(intent.symbol)
            order_body = {
                "trading_symbol": intent.symbol,
                "quantity": intent.quantity,
                "price": intent.price,
                "trigger_price": intent.trigger_price,
                "validity": "DAY",
                "exchange": "NSE",
                "segment": "CASH",
                "product": intent.product,
                "order_type": intent.order_type,
                "transaction_type": intent.side,
                "order_reference_id": intent.idempotency_key[:32],
            }
            response = await self.broker.place_order(order_body)
            payload = _payload(response)
            order_id = str(payload.get("groww_order_id", ""))
            if not order_id:
                raise RiskViolation(f"broker returned no order id: {response}")
            audit("ORDER", "SUBMITTED", {"symbol": intent.symbol, "side": intent.side, "qty": intent.quantity, "wave": intent.wave, "priority": intent.priority, "order_id": order_id})
            result = {"broker": response, "order_id": order_id, "idempotent_replay": False}
            self._idem_finish(intent.idempotency_key, result)
            return result
        except Exception:
            self._idem_fail(intent.idempotency_key)
            raise

    async def execute_oco(self, intent: OcoIntent) -> dict:
        request = intent.model_dump()
        replay = self._idem_begin(intent.idempotency_key, request)
        if replay is not None:
            return replay
        try:
            body = {
                "reference_id": intent.idempotency_key[:32],
                "smart_order_type": "OCO",
                "segment": "CASH",
                "trading_symbol": intent.symbol,
                "quantity": intent.quantity,
                "net_position_quantity": intent.net_position_quantity,
                "transaction_type": intent.exit_side,
                "target": {"trigger_price": f"{intent.target_price:.2f}", "order_type": "LIMIT", "price": f"{intent.target_price:.2f}"},
                "stop_loss": {"trigger_price": f"{intent.stop_price:.2f}", "order_type": "SL_M"},
                "product_type": "MIS",
                "exchange": "NSE",
                "duration": "DAY",
            }
            response = await self.broker.create_oco(body)
            result = {"broker": response, "idempotent_replay": False}
            self._idem_finish(intent.idempotency_key, result)
            audit("ORDER", "OCO_CREATED", {"symbol": intent.symbol, "qty": intent.quantity, "target": intent.target_price, "stop": intent.stop_price})
            return result
        except Exception:
            self._idem_fail(intent.idempotency_key)
            raise

    async def _reconcile_symbol(self, symbol: str) -> None:
        # Fetching broker positions immediately before a new risk-changing order makes broker state the source of truth.
        positions = _payload(await self.broker.positions()).get("positions", [])
        matching = [p for p in positions if str(p.get("trading_symbol", "")).upper() == symbol.upper() and str(p.get("product", "")).upper() == "MIS"]
        if len(matching) > 1:
            raise RiskViolation("unexpected duplicate broker position rows")

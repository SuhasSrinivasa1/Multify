from __future__ import annotations

import hashlib
import json
import time

from .broker import GrowwBroker
from .models import HoldingOrderIntent
from .risk import RiskEngine, RiskViolation
from .storage import audit, db_connection


def _json_hash(data: dict) -> str:
    raw = json.dumps(data, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def _payload(envelope: dict):
    if str(envelope.get("status", "SUCCESS")).upper() == "SUCCESS" and "payload" in envelope:
        return envelope.get("payload")
    return envelope


class LiveTradingService:
    def __init__(self, broker: GrowwBroker, risk: RiskEngine) -> None:
        self.broker = broker
        self.risk = risk

    def _idem_begin(self, key: str, request: dict) -> dict | None:
        h = _json_hash(request)
        now = int(time.time())
        with db_connection() as db:
            row = db.execute(
                "SELECT request_hash,response_json,status FROM idempotency WHERE idem_key=?",
                (key,),
            ).fetchone()
            if row:
                if row["request_hash"] != h:
                    raise RiskViolation("idempotency key reused with different request")
                if row["status"] == "DONE" and row["response_json"]:
                    return json.loads(row["response_json"])
                raise RiskViolation("matching request is already in progress")
            db.execute(
                "INSERT INTO idempotency(idem_key,request_hash,status,created_at,updated_at) VALUES(?,?,?,?,?)",
                (key, h, "IN_PROGRESS", now, now),
            )
            db.commit()
        return None

    def _idem_finish(self, key: str, response: dict) -> None:
        now = int(time.time())
        with db_connection() as db:
            db.execute(
                "UPDATE idempotency SET response_json=?, status='DONE', updated_at=? WHERE idem_key=?",
                (json.dumps(response), now, key),
            )
            db.commit()

    def _idem_fail(self, key: str) -> None:
        now = int(time.time())
        with db_connection() as db:
            db.execute(
                "UPDATE idempotency SET status='FAILED', updated_at=? WHERE idem_key=?",
                (now, key),
            )
            db.commit()

    async def execute_holding_order(self, intent: HoldingOrderIntent) -> dict:
        request = intent.model_dump()
        replay = self._idem_begin(intent.idempotency_key, request)
        if replay is not None:
            return replay
        try:
            await self.risk.validate(intent)
            transaction = "BUY"
            if intent.action == "SELL_OWNED_HOLDING":
                await self._require_owned_holding(intent.symbol, intent.quantity)
                transaction = "SELL"

            order_body = {
                "trading_symbol": intent.symbol,
                "quantity": intent.quantity,
                "price": 0.0,
                "trigger_price": 0.0,
                "validity": "DAY",
                "exchange": "NSE",
                "segment": "CASH",
                "product": "CNC",
                "order_type": "MARKET",
                "transaction_type": transaction,
                "order_reference_id": intent.idempotency_key[:32],
            }
            response = await self.broker.place_order(order_body)
            payload = _payload(response) or {}
            order_id = str(payload.get("groww_order_id", ""))
            if not order_id:
                raise RiskViolation(f"broker returned no order id: {response}")
            audit(
                "HOLDING_ORDER",
                intent.action,
                {"symbol": intent.symbol, "qty": intent.quantity, "order_id": order_id},
            )
            result = {"broker": response, "order_id": order_id, "idempotent_replay": False}
            self._idem_finish(intent.idempotency_key, result)
            return result
        except Exception:
            self._idem_fail(intent.idempotency_key)
            raise

    async def _require_owned_holding(self, symbol: str, quantity: int) -> None:
        payload = _payload(await self.broker.holdings())
        if isinstance(payload, dict):
            rows = payload.get("holdings", [])
        elif isinstance(payload, list):
            rows = payload
        else:
            rows = []
        matching = [
            row for row in rows
            if str(row.get("trading_symbol", "")).upper() == symbol.upper()
        ]
        if len(matching) != 1:
            raise RiskViolation("SELL_OWNED_HOLDING requires exactly one broker holding row")
        row = matching[0]
        available = int(row.get("demat_free_quantity") or row.get("quantity") or 0)
        if available < quantity:
            raise RiskViolation(
                f"owned holding quantity {available} is below requested sell quantity {quantity}"
            )

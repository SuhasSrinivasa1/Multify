from __future__ import annotations

import asyncio
from collections import deque
from datetime import datetime, time as dtime
from zoneinfo import ZoneInfo

from .config import get_settings
from .models import OrderIntent
from .storage import read_risk_value


INDIA = ZoneInfo("Asia/Kolkata")


class RiskViolation(RuntimeError):
    pass


def _parse_hhmm(value: str) -> dtime:
    h, m = value.split(":", 1)
    return dtime(hour=int(h), minute=int(m))


class RiskEngine:
    def __init__(self) -> None:
        self.settings = get_settings()
        self._order_times: deque[float] = deque()
        self._lock = asyncio.Lock()

    async def gate_rate(self) -> None:
        loop = asyncio.get_running_loop()
        now = loop.time()
        async with self._lock:
            while self._order_times and now - self._order_times[0] >= 1.0:
                self._order_times.popleft()
            if len(self._order_times) >= self.settings.max_order_rate_per_second:
                raise RiskViolation("server-side order rate limit reached")
            self._order_times.append(now)

    def gate_time(self, intent: OrderIntent) -> None:
        now = datetime.now(INDIA)
        t = now.time()
        if now.weekday() >= 5:
            raise RiskViolation("market day guard: weekend")
        if intent.priority == "FORCE_FLAT":
            return
        if not (_parse_hhmm(self.settings.market_open_hhmm) <= t <= _parse_hhmm(self.settings.last_new_order_hhmm)):
            raise RiskViolation("new orders are outside configured NSE live window")

    def gate_kill_switch(self) -> None:
        if self.settings.kill_switch or read_risk_value("kill_switch", "0") == "1":
            raise RiskViolation("live gateway kill switch is active")

    def gate_intent_shape(self, intent: OrderIntent) -> None:
        if intent.order_type == "LIMIT" and intent.price <= 0:
            raise RiskViolation("limit order requires positive price")
        reference_price = intent.price if intent.price > 0 else intent.expected_ltp
        if reference_price > 0:
            notional = reference_price * intent.quantity
            if notional > self.settings.max_order_notional_rupees:
                raise RiskViolation(f"single order notional {notional:.2f} exceeds server cap")

    async def validate(self, intent: OrderIntent) -> None:
        self.gate_kill_switch()
        self.gate_time(intent)
        self.gate_intent_shape(intent)
        await self.gate_rate()

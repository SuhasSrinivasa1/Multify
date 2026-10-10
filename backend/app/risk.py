from __future__ import annotations

import asyncio
from collections import deque
from datetime import datetime, time as dtime
from zoneinfo import ZoneInfo

from .config import get_settings
from .models import HoldingOrderIntent
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

    def gate_time(self) -> None:
        now = datetime.now(INDIA)
        t = now.time()
        if now.weekday() >= 5:
            raise RiskViolation("market day guard: weekend")
        if not (_parse_hhmm(self.settings.market_open_hhmm) <= t <= _parse_hhmm(self.settings.last_new_order_hhmm)):
            raise RiskViolation("holding orders are outside the NSE regular session")

    def gate_kill_switch(self, intent: HoldingOrderIntent) -> None:
        if intent.action == "BUY_HOLDING" and (
            self.settings.kill_switch or read_risk_value("kill_switch", "0") == "1"
        ):
            raise RiskViolation("live gateway kill switch is active")

    def gate_intent_shape(self, intent: HoldingOrderIntent) -> None:
        if intent.expected_ltp > 0:
            notional = intent.expected_ltp * intent.quantity
            if notional > self.settings.max_order_notional_rupees:
                raise RiskViolation(f"single order notional {notional:.2f} exceeds server cap")

    async def validate(self, intent: HoldingOrderIntent) -> None:
        self.gate_kill_switch(intent)
        self.gate_time()
        self.gate_intent_shape(intent)
        await self.gate_rate()

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass
from typing import Any

import httpx
import pyotp

from .config import get_settings


class BrokerError(RuntimeError):
    pass


@dataclass
class TokenState:
    value: str = ""
    obtained_at: float = 0.0
    expiry_text: str = ""


class GrowwBroker:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = httpx.AsyncClient(
            base_url=self.settings.groww_base_url,
            headers={"Accept": "application/json", "X-API-VERSION": "1.0"},
            timeout=httpx.Timeout(12.0, connect=8.0),
        )
        self.token = TokenState()
        self._token_lock = asyncio.Lock()

    async def close(self) -> None:
        await self.client.aclose()

    async def ensure_token(self, force: bool = False) -> str:
        # Groww access tokens are refreshed conservatively; exact broker expiry is also returned and logged.
        if not force and self.token.value and (time.time() - self.token.obtained_at) < 6 * 60 * 60:
            return self.token.value
        async with self._token_lock:
            if not force and self.token.value and (time.time() - self.token.obtained_at) < 6 * 60 * 60:
                return self.token.value
            totp = pyotp.TOTP(self.settings.groww_totp_secret).now()
            response = await self.client.post(
                "v1/token/api/access",
                headers={"Authorization": f"Bearer {self.settings.groww_api_key}"},
                json={"key_type": "totp", "totp": totp},
            )
            data = self._decode(response, "access token")
            token = data.get("token") or (data.get("payload") or {}).get("token")
            if not token:
                raise BrokerError(f"Groww did not return an access token: {data}")
            self.token = TokenState(str(token), time.time(), str(data.get("expiry", "")))
            return self.token.value

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict:
        token = await self.ensure_token()
        headers = dict(kwargs.pop("headers", {}))
        headers["Authorization"] = f"Bearer {token}"
        response = await self.client.request(method, path, headers=headers, **kwargs)
        if response.status_code == 401:
            token = await self.ensure_token(force=True)
            headers["Authorization"] = f"Bearer {token}"
            response = await self.client.request(method, path, headers=headers, **kwargs)
        return self._decode(response, path)

    @staticmethod
    def _decode(response: httpx.Response, label: str) -> dict:
        try:
            data = response.json()
        except Exception as exc:
            raise BrokerError(f"{label}: HTTP {response.status_code} returned non-JSON") from exc
        if response.is_error:
            raise BrokerError(f"{label}: HTTP {response.status_code}: {data}")
        if isinstance(data, dict) and str(data.get("status", "SUCCESS")).upper() == "FAILURE":
            raise BrokerError(f"{label}: {data.get('error') or data}")
        return data

    async def profile(self) -> dict:
        return await self._request("GET", "v1/user/detail")

    async def margins(self) -> dict:
        return await self._request("GET", "v1/margins/detail/user")

    async def positions(self) -> dict:
        return await self._request("GET", "v1/positions/user", params={"segment": "CASH"})

    async def holdings(self) -> dict:
        return await self._request("GET", "v1/holdings/user")

    async def quote(self, symbol: str) -> dict:
        return await self._request("GET", "v1/live-data/quote", params={"exchange": "NSE", "segment": "CASH", "trading_symbol": symbol})

    async def candles(self, symbol: str, start_time: str, end_time: str, interval: str = "5minute") -> dict:
        return await self._request("GET", "v1/historical/candles", params={
            "exchange": "NSE", "segment": "CASH", "groww_symbol": f"NSE-{symbol}",
            "start_time": start_time, "end_time": end_time, "candle_interval": interval,
        })

    async def place_order(self, body: dict) -> dict:
        return await self._request("POST", "v1/order/create", json=body)

    async def order_status(self, order_id: str) -> dict:
        return await self._request("GET", f"v1/order/detail/{order_id}", params={"segment": "CASH"})

    async def create_oco(self, body: dict) -> dict:
        return await self._request("POST", "v1/order-advance/create", json=body)

    async def modify_oco(self, smart_order_id: str, body: dict) -> dict:
        return await self._request("PUT", f"v1/order-advance/modify/{smart_order_id}", json=body)

    async def cancel_oco(self, smart_order_id: str) -> dict:
        return await self._request("POST", f"v1/order-advance/cancel/CASH/OCO/{smart_order_id}")

    async def smart_order_status(self, smart_order_id: str) -> dict:
        return await self._request("GET", f"v1/order-advance/status/CASH/OCO/internal/{smart_order_id}")

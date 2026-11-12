"""Read-only, five-minute OHLCV replay of entry timing after a Fixed signal.

No API calls, no real orders, no performance claims from absent data. Only
information available at a signal or at a completed later bar can trigger an
entry. This is not an exact reconstruction of the live 15m Fixed engine/GTT.
"""
from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from math import isfinite
from zoneinfo import ZoneInfo

IST = ZoneInfo("Asia/Kolkata")
BAR_MS = 300_000


@dataclass(frozen=True)
class Bar:
    symbol: str
    at_ms: int
    open: float
    high: float
    low: float
    close: float
    volume: float

    @property
    def day(self):
        return datetime.fromtimestamp(self.at_ms / 1000, IST).date()


@dataclass(frozen=True)
class Signal:
    key: str
    symbol: str
    at_ms: int
    target_pct: float = 1.1161043

    @property
    def day(self):
        return datetime.fromtimestamp(self.at_ms / 1000, IST).date()


def read_csv(path, cls):
    with open(path, newline="", encoding="utf-8-sig") as stream:
        return [cls(**{key: float(value) if key in {"open", "high", "low", "close", "volume", "target_pct"}
                       else int(value) if key == "at_ms"
                       else value for key, value in row.items() if value not in (None, "")})
                for row in csv.DictReader(stream)]


def validate_bars(bars):
    seen = set()
    for b in bars:
        ident = (b.symbol, b.at_ms)
        if ident in seen:
            raise ValueError("duplicate candle timestamp: " + str(ident))
        seen.add(ident)
        if b.at_ms <= 0 or b.at_ms % BAR_MS != 0 or not all(isfinite(v) for v in (
                b.open, b.high, b.low, b.close, b.volume)) or b.volume < 0 or (
                b.low <= 0 or b.low > min(b.open, b.close) or
                b.high < max(b.open, b.close)):
            raise ValueError("invalid five-minute OHLCV candle: " + str(ident))
    return sorted(bars, key=lambda b: (b.symbol, b.at_ms))


def confirmation_index(day_bars, signal_ms, max_wait_ms=30 * 60_000):
    """Confirm at bar close, then enter *subsequent* bar open. Never same bar."""
    for i in range(1, len(day_bars) - 1):
        prev, now, nxt = day_bars[i - 1], day_bars[i], day_bars[i + 1]
        if not (signal_ms < now.at_ms + BAR_MS <= signal_ms + max_wait_ms):
            continue
        if now.at_ms - prev.at_ms != BAR_MS or nxt.at_ms - now.at_ms != BAR_MS:
            continue
        if now.close > prev.high and now.volume > prev.volume:
            return i + 1
    return None


def first_entry_index(day_bars, signal_ms):
    return next((i for i, bar in enumerate(day_bars) if bar.at_ms > signal_ms), None)


def simulate(day_bars, index, target_pct, stop_pct, slippage_bps, round_trip_cost_bps,
             horizon_min):
    if index is None:
        return {"state": "NO_ENTRY", "net_pct": 0.0}
    entry_bar = day_bars[index]
    entry = entry_bar.open * (1 + slippage_bps / 10_000)
    target = entry * (1 + target_pct / 100)
    stop = entry * (1 - stop_pct / 100)
    horizon_ms = horizon_min * 60_000
    if not isfinite(target_pct) or not (target_pct > 0 and 0 < stop_pct < 100):
        raise ValueError("target and stop must be valid percentages")
    path = [b for b in day_bars[index:] if b.at_ms < entry_bar.at_ms + horizon_ms]
    if not path:
        return {"state": "CENSORED", "net_pct": None}
    for a, b in zip(path, path[1:]):
        if b.at_ms != a.at_ms + BAR_MS:
            return {"state": "MISSING_BARS", "net_pct": None}
    exit_at, reason, ambiguous = None, None, False
    for b in path:
        hit_target, hit_stop = b.high >= target, b.low <= stop
        if hit_target and hit_stop:
            reason, exit_at, ambiguous = "AMBIGUOUS_ASSUME_STOP", stop, True
            break
        if hit_stop:
            reason, exit_at = "STOP_FIRST", stop
            break
        if hit_target:
            reason, exit_at = "TARGET_FIRST", target
            break
    if exit_at is None:
        if path[-1].at_ms + BAR_MS < entry_bar.at_ms + horizon_ms:
            return {"state": "CENSORED", "net_pct": None}
        reason, exit_at = "HORIZON_EXIT", path[-1].close
    net_pct = (exit_at * (1 - slippage_bps / 10_000) / entry - 1) * 100 - round_trip_cost_bps / 100
    return {
        "state": reason, "net_pct": round(net_pct, 6),
        "entry_at_ms": entry_bar.at_ms, "entry_assumed_price": round(entry, 6),
        "exit_reference_price": round(exit_at, 6),
        "ambiguous_intrabar": ambiguous, "hypothetical_not_realized": True,
    }


def replay(bars, signals, *, stop_pct=5.0, slippage_bps=5.0,
           round_trip_cost_bps=20.0, horizon_min=60):
    """Same candidate population; 9:45 is a test, not an assumed optimum.

    This replays post-Fixed entry timing only. Pre-Fixed early-watch entry
    cannot be reconstructed without separate, timestamped candidate signals.
    """
    if any(x < 0 for x in (slippage_bps, round_trip_cost_bps)) or not 0 < horizon_min <= 360:
        raise ValueError("invalid assumptions")
    ordered = validate_bars(bars)
    groups = defaultdict(list)
    for b in ordered:
        groups[(b.symbol, b.day)].append(b)
    result = []
    keys = set()
    for s in sorted(signals, key=lambda x: (x.at_ms, x.symbol, x.key)):
        if not s.key or not s.symbol or s.at_ms <= 0 or not 0 < s.target_pct < 100:
            raise ValueError("invalid signal")
        if s.key in keys:
            raise ValueError("duplicate signal event id")
        keys.add(s.key)
        day_bars = groups[(s.symbol, s.day)]
        if not day_bars:
            unavailable = {"state": "DATA_MISSING", "net_pct": None}
            result.append({
                "key": s.key, "symbol": s.symbol, "signal_at_ms": s.at_ms,
                "time_phase": "UNKNOWN_WITHOUT_CANDLES",
                "comparison": {
                    "FIXED_REFERENCE_DELAYED_OPEN": dict(unavailable),
                    "FIVE_MINUTE_CONFIRMATION": dict(unavailable),
                    "SCHEDULED_0945": dict(unavailable),
                },
            })
            continue
        i_fixed = first_entry_index(day_bars, s.at_ms)
        i_5m = confirmation_index(day_bars, s.at_ms)
        local_clock = datetime.fromtimestamp(s.at_ms / 1000, IST).time()
        after_0945 = (local_clock.hour, local_clock.minute) >= (9, 45)
        indices = {
            "FIXED_REFERENCE_DELAYED_OPEN": i_fixed,
            "FIVE_MINUTE_CONFIRMATION": i_5m,
            "SCHEDULED_0945": i_5m if after_0945 else i_fixed,
        }
        choices = {name: simulate(day_bars, i, s.target_pct, stop_pct, slippage_bps,
                                  round_trip_cost_bps, horizon_min)
                   for name, i in indices.items()}
        result.append({
            "key": s.key, "symbol": s.symbol, "signal_at_ms": s.at_ms,
            "time_phase": "AFTER_0945_IST" if after_0945 else "BEFORE_0945_IST",
            "comparison": choices,
        })
    summary = {}
    for name in ("FIXED_REFERENCE_DELAYED_OPEN", "FIVE_MINUTE_CONFIRMATION", "SCHEDULED_0945"):
        outcomes = [r["comparison"][name] for r in result]
        scored = [r["net_pct"] for r in outcomes if r["net_pct"] is not None]
        summary[name] = {
            "signal_count": len(outcomes),
            "resolved_count_including_no_entry": len(scored),
            "entered_count": sum(r["state"] not in {"NO_ENTRY", "CENSORED", "MISSING_BARS"} for r in outcomes),
            "ambiguous_same_candle_count": sum(r.get("ambiguous_intrabar", False) for r in outcomes),
            "mean_net_pct_per_resolved_opportunity": round(sum(scored) / len(scored), 6) if scored else None,
            "target_first_count": sum(r["state"] == "TARGET_FIRST" for r in outcomes),
            "stop_first_count": sum(r["state"] in {"STOP_FIRST", "AMBIGUOUS_ASSUME_STOP"} for r in outcomes),
        }
    return {
        "research_only": True, "realized_pnl": False,
        "does_not_reconstruct_earlier_than_fixed_signals": True,
        "does_not_model_trailing_gtt_or_broker_fills": True,
        "target_and_stop_same_candle_assumed_stop": True,
        "assumptions": {"stop_pct": stop_pct, "slippage_each_side_bps": slippage_bps,
                        "round_trip_cost_bps": round_trip_cost_bps, "horizon_minutes": horizon_min},
        "summary": summary, "events": result,
    }


def main():
    p = argparse.ArgumentParser(description="Fixed vs five-minute timing: shadow only")
    p.add_argument("--candles", required=True, help="CSV symbol,at_ms,open,high,low,close,volume")
    p.add_argument("--signals", required=True, help="CSV key,symbol,at_ms,target_pct")
    p.add_argument("--stop-pct", type=float, default=5.0)
    p.add_argument("--slippage-bps", type=float, default=5.0)
    p.add_argument("--round-trip-cost-bps", type=float, default=20.0)
    p.add_argument("--horizon-min", type=int, default=60)
    opts = p.parse_args()
    print(json.dumps(replay(read_csv(opts.candles, Bar), read_csv(opts.signals, Signal),
                           stop_pct=opts.stop_pct, slippage_bps=opts.slippage_bps,
                           round_trip_cost_bps=opts.round_trip_cost_bps,
                           horizon_min=opts.horizon_min), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

# Multify Trader Pro v9.0.7 Fixed Auto Buy Safe Recovery — TEST

## Incident and observed evidence (17 Nov 2026, 10:18 IST)
User enabled Fixed Auto Buy at 09:49:39 IST. Diagnostic ZIP showed
`fixed_auto_buy_enabled=true`, broker authenticated, static IP and DDPI ready,
but zero orders. Alpha received HTTP 429 on `GET /v1/live-data/ltp` at
09:45:33 (90s cooldown), 09:50:10 (5m), 10:05:34 (15m).
Four `FIXED_AUTO_BUY/BUY_BLOCKED_BROKER_NOT_READY` entries after 10:11
reported `HTTP_429_LIVE_DATA_COOLDOWN` and `broker_request_suppressed=true`.

**A broker LIVE_DATA cooldown cannot safely be ignored.** It covers Alpha's
fresh execution quote and stops Fixed BUY from proceeding. Beta is read-only,
and does not have authority to submit orders or protective GTTs. A fifteen-minute
cooldown exceeds the original five-minute maximum recommendation age.

## v9.0.7 changes
1. When Fixed Auto Buy is enabled and Beta LIVE is healthy, direct 35 of the
   53 existing NSE bulk LTP batch slots to authenticated, read-only Beta,
   versus the ordinary 26. This lowers Alpha *research* LTP pressure. It
   does not guarantee no 429 and does not alter the 53-slot universe,
   coverage requirement, per-account governors, or response fail-closed behavior.
   With Fixed Auto Buy OFF, original deterministic batch split remains exact.
2. Spread a deterministic subset of detailed read-only research quotes to
   healthy Beta while Auto Buy is ON, reducing competition with the mandatory
   fresh Alpha execution quote. Alpha/Beta limits and original historical
   account selection remain independent; no BUY quote may come from Beta.
3. `FixedAutoBuyCooldownQueue`: bounded 48-entry *in-memory intent*
   journal, never a broker order or a cached quote. Queue only post-activation
   recommendations with original 5-minute freshness. On cooldown, emit a
   one-shot recheck time only if expiry leaves at least 20 seconds for
   broker revalidation. Long 15-minute cooldowns never revive stale signals.
4. Lifecycle-scoped foreground-service observer receives retry intent and,
   after the time, calls the entire original `autoBuyFixedRecommendations`
   under its existing submission mutex. Any new order still requires
   authenticated Alpha, normal trading hours, Auto Buy ON, no safety halt,
   fresh broker Alpha quote, liquidity/price checks, current recommendation,
   existing broker/app inventory proof, protection readiness, and original
   post-fill -2% hard-stop/GTT creation. No retries or orders are issued
   during a live broker cooldown.
5. Explicit event-by-event recommendation ID, symbol, original expiry,
   broker cooldown, and retry decision are emitted to audit. Already stale
   rows are filtered before any broker readiness call.
6. When eligible Auto Buy candidates are blocked, a rate-limited status notification
   displays the broker cooldown and explicitly states that expired signals will
   not be bought. The notification does not place orders or bypass limits.
7. CI freezes v8.3.19 score/discovery/publication and v8.3.16 shadow;
   the original service logic is byte-identical after stripping exactly
   the marked V902 prefetch, V904 research gate, and V907 service-scope
   observer additions. Unit tests cover cooldown/retry safety and batch routing.

## Unchanged invariants
- Fixed 40-factor strategy, 15m verified candles, 28 discovery / 48
  detailed selection, 1.1161043% reference target, PRIMARY repeat.
- Original Alpha-only market order, fresh quote, broker holdings/positions
  duplicate check, hard -2% stop and +0.55/+0.50/+0.10 trail.
- Beta read-only. No change to Alpha/Beta governor limits, 429 backoff,
  authentication, pricing/liqidity thresholds, or order pacing.
- Strict post-activation publication and five-minute auto-buy expiry.
- Forecast remains frozen and its Auto Buy disabled.

## Release gates and limitations
- Full Android unit suite, backend replay, frozen-source parity, APK signature,
  and SHA-256 must pass.
- Current installed version may use a different Android signing certificate.
  Verify compatible fingerprints before in-place installation. Never uninstall
  an existing trading app without backup and holdings/GTT reconciliation.
- Keep this a TEST build until a live NSE session confirms fewer Alpha LTP 429s,
  no harmful Beta 429 increase, normal Fixed scan completion, and every
  submitted order has verified broker fill + protective GTT.
- The queue is not an instruction to buy after a 15-minute outage. If the
  cooldown exceeds original recommendation freshness, the BUY correctly expires.

# Multify Trader Pro v7.8.1 — Execution Safety

This release keeps the frozen Fixed recommendation engine and its input semantics unchanged while tightening execution ordering and common position protection.

## Execution controls

There are exactly three trading permissions in Settings:

- **ARM Multify calls** — controls Multify entry execution only.
- **Forecast auto-buy** — independently controls Forecast entry execution after the existing five-session Forecast calibration lock.
- **Fixed auto-buy** — independently controls Fixed entry execution.

Forecast discovery is automatic and opportunistic rather than a fourth trading toggle. With Forecast auto-buy OFF, Forecast may still scan/learn and publish notifications, but it cannot create a Forecast entry. Turning any entry toggle OFF never disables sell protection for an already-open app-owned holding.

## Order-first delivery

For successful live entries the broker order path precedes nonessential presentation/learning work. Multify no longer performs Fixed snapshot maintenance ahead of a TRADE_RELEASE buy. Fixed continues to auto-buy before its delivery event. Forecast notifications are now held behind the Forecast order pass; if Multify or Fixed priority interrupts Forecast, Forecast delivery is deferred.

A minimal local notification fingerprint reservation and mandatory broker/safety readiness checks remain before a Multify order. This is intentional idempotency protection against duplicate Android notifications and duplicate real BUYs; it is not recommendation logging or user-visible delivery.

## Common sell protection

Every app-owned Multify, Fixed and Forecast CNC LONG uses the same position monitor. The existing profitable stepped trailing policy remains unchanged:

- arm at **+0.55%** high-water gain;
- initially protect **+0.50%**;
- ratchet the protected gain in **+0.10%** steps.

v7.8.1 adds a common **−5.00% hard loss floor** from the actual holding cost basis. Before the profitable trail arms, the position is protected by a hard-stop GTT. Once the profitable trail becomes tighter, it replaces/raises the hard floor; protection never loosens. The after-hours monitor restores missing hard-stop protection for open LONG holdings, so overnight positions are not deliberately left unprotected.

A −5% trigger cannot guarantee a −5.00% realised loss if the market gaps through the trigger or fills with slippage; execution occurs at the first broker-available market price. Fees can also make net realised P&L slightly worse than the price-only threshold.

## No averaging down

There is no averaging-down, scale-in, pyramiding, or add-on-decline execution path. Each recommendation creates at most its normal entry holding; exits are handled by the common hard-stop/profitable-trailing architecture.

## Priority and recovery

Runtime priority remains **sell protection → active Multify trade event → Fixed → Forecast**. Forecast retains the 45-second Fixed guard and API-utilisation caps. The foreground engine remains `START_STICKY`, uses independent supervised workers, retries the position monitor after failures, reconciles broker holdings, and uses deterministic order references to recover ambiguous broker acknowledgements without blindly resubmitting duplicate orders.

## Fixed protection

The authoritative Fixed source lock, Fixed input-semantics lock, protected coordinator region, Fixed scan cadence, candidate discovery/ranking, 40-factor model, publication policy, and one-way Fixed→Forecast isolation are unchanged. The protected coordinator-region Git blob remains:

`a187eefb4ec2703af24c6cefdb61bfc51489b623`

The speed/cache/priority architecture reduces avoidable delay, but no software can guarantee that a market price will not peak between scans. This release does not alter the frozen Fixed formula or publication rules to make such a claim.


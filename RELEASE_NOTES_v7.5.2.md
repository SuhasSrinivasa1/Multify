# Multify Trader Pro v7.5.2

## Fixed-only recommendation and wallet rules

- Fixed publishes/executes only stocks with a recommendation entry price strictly above ₹100.
- Fixed may use the remaining CNC wallet balance when it is lower than the configured per-call budget.
  - Example: configured budget ₹50,000, current CNC cash ₹20,000 -> Fixed sizes from the ₹20,000 available balance.
  - Fixed still never exceeds the configured per-call budget.
- The 2% execution-price buffer remains in quantity sizing.

## Forecast and Multify budget rule

- Multify and adaptive Forecast require the full configured per-call budget to be available before a BUY can be sized.
- Example: configured budget ₹50,000, current CNC cash ₹20,000 -> no Multify/Forecast BUY is placed.

## Exit behavior

- No changes were made to the common long-holding exit engine.
- Fixed, Forecast and Multify holdings continue using the same stepped trailing policy:
  - arm at +0.55%
  - initially protect +0.50%
  - ratchet protected gain upward by +0.10% steps
  - never loosen an armed trailing stop
- Multify Book Profit notifications remain learning-only for app-owned Multify holdings and do not directly close them.

## Frozen Fixed model

The Fixed forecasting model remains the frozen FIXED-V741-2026-10-21 model/state. v7.5.2 adds only the explicit Fixed price-universe guardrail and Fixed execution-sizing exception requested above; it does not change the frozen factor weights, quality thresholds, publication confirmation rules, target model, or trailing-exit policy.

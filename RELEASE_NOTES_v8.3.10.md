# v8.3.10 — Fixed Auto Buy: New Signals Only

Baseline: v8.3.9 CI-verified source (55766c85d2e01e24e4f3b5f62e0841796ac10932).

- Remove the immediate-enable sweep that tried to BUY existing ACTIVE Fixed recommendations.
- Persist a new ON-session activation timestamp atomically with the Fixed Auto Buy toggle.
- Permit automatic buying only when the Fixed recommendation event was generated strictly after that activation.
- Recheck before quoting and immediately before submitting orders, including OFF→ON races.
- Existing ON=true installations without a timestamp fail closed until toggled OFF and back ON.
- A genuinely new later qualifying Fixed signal on the same stock remains eligible, subject to the existing broker, liquidity, price, ownership, hard-stop and GTT protections.
- Avoid broker token/order-preparation calls if there are no new eligible recommendations.
- No changes to frozen Fixed scoring, 15-minute candles, confidence, publication, target, scan cadence, or SELL/GTT logic.
- Broker 429 cooldown policy preserved from v8.3.9; live logs are still required to diagnose recurrent cooldowns.

Live broker-execution validation remains pending.

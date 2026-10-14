# Multify Trader Pro v6.7.0 — Profit-Gated Trailing

## What changed

- Autonomous profit trailing no longer arms from the rolling Multify average alone.
- The autonomous trailing gate is now **1.00% of the actual invested amount as estimated net profit after charges**.
- Example: for 26 shares bought at ₹1,859.63, invested capital is ₹48,350.38 and the autonomous trail requires about ₹483.50 estimated net profit before it can arm.
- The minimum autonomous trailing stop is cost-aware and is floored at the exit price required to protect the 1.00% net-profit gate.
- Once armed, the existing ₹0.25 high-water ratchet and continuous adaptive trailing remain active; the stop never loosens.
- Before an autonomous trailing SELL, the app verifies that the active stop itself protects at least the 1.00% estimated net-profit threshold.
- v6.6 low-profit trailing stops already stored on upgrade are reset to the emergency failsafe unless they satisfy the new 1.00% net-profit gate.
- The emergency/failsafe stop remains active independently and can still close a losing position.
- An explicit Multify Book Profit notification remains independent of the autonomous 1.00% gate and can still close the app-owned Multify holding.
- Trailing audit now records invested amount, required net profit, minimum profit exit price and projected net profit at the active stop.
- Version bumped to `6.7.0-profit-gated-trailing` (`versionCode 670`).

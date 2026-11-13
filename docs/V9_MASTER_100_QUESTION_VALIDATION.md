# Multify v9 Master — 100-question evidence and release gate

Each question is checked against source, uploaded Nov 13 data or explicitly marked unproven. Evidence statuses are **not** 100 performance wins. SOURCE_GATE means CI asserts it at build, not proven live. Public repository document contains no account, order or position secrets.

## Original baseline and provenance

1. **Is the accepted v7.4.1 Fixed contract still declared?** — SOURCE_PASS
2. **Are its 107 learning calls still the immutable reference?** — SOURCE_PASS
3. **Is the 1.1161043% target reference retained?** — SOURCE_PASS
4. **Is the reference source commit declared?** — SOURCE_PASS
5. **Is v8.3.16 source available verbatim?** — SOURCE_PASS
6. **Is v8.3.19 source available verbatim?** — SOURCE_PASS
7. **Are original v8.3.16 raw trading ZIPs available?** — MISSING_EVIDENCE
8. **Are original v8.3.19 trading ZIPs available?** — MISSING_EVIDENCE
9. **Are original complete v7.4.1 replay candles available?** — MISSING_EVIDENCE
10. **Can we truthfully rank historical v7/v16/v19 P&L today?** — NO

## Strategy accuracy and indicator semantics

11. **Does v16 include old VWAP history across sessions?** — SOURCE_PASS
12. **Does v19 calculate intraday VWAP from today's session?** — SOURCE_PASS
13. **Does v16 accept textual timestamps via index fallback?** — SOURCE_PASS
14. **Does v19 require parsed verified candle start time?** — SOURCE_PASS
15. **Does v19 discard still-forming 15m bars?** — SOURCE_PASS
16. **Could v16 use still-forming historical bars?** — SOURCE_RISK
17. **Does v16 allow unknown bid/ask spread as zero?** — SOURCE_RISK
18. **Does v19 reject missing PRIMARY spread?** — SOURCE_PASS
19. **Was v16 RSI exhaustion logic amended in v17?** — SOURCE_PASS
20. **Can we promote the v16 signal without checking lookahead bias?** — NO

## Live Fixed strategy invariants

21. **Is live production fixed at 15-minute candles?** — SOURCE_PASS
22. **Does v9 preserve v19 scoring without alteration?** — SOURCE_GATE
23. **Does v9 preserve 40-factor scoring?** — SOURCE_GATE
24. **Does v9 keep original publication confirmation?** — SOURCE_GATE
25. **Are 28 discovery slots preserved?** — SOURCE_GATE
26. **Are 48 detailed candidate slots preserved?** — SOURCE_GATE
27. **Does v9 preserve same-day repeat qualification?** — SOURCE_GATE
28. **Is daily recommendation capping still absent?** — SOURCE_PASS
29. **Can a slow scan suppress a recommendation in v9?** — NO
30. **Can any shadow model authorize Auto Buy?** — NO

## Hit/miss labels and statistical evidence

31. **Did we deduplicate cumulative v25 ZIP exports?** — AUDIT_PASS
32. **Did we deduplicate cumulative v27 ZIP exports?** — AUDIT_PASS
33. **Did v25 have 8 mature 15m candidates and 3 hits?** — AUDIT_PASS
34. **Did v26 have 2 mature 15m candidates and 1 hit?** — AUDIT_PASS
35. **Did v27 have 13 mature 15m candidates and 3 hits?** — AUDIT_PASS
36. **Is pooled 15m result 7/23 based on sampled LTP?** — AUDIT_PASS
37. **Can immature candidates be called misses?** — NO
38. **Are target touches actual filled trades?** — NO
39. **Does the available set prove which version was best?** — NO
40. **Can v28 be ranked without a v28 live-session export?** — NO

## Scanning latency and discovery

41. **Does v9 keep 90s nominal scan cadence?** — SOURCE_GATE
42. **Does 90s cadence guarantee scans finish in 90s?** — NO
43. **Does v27 use one Fixed worker per service instance?** — SOURCE_PASS
44. **Did v26 previously show overlapping workers?** — AUDIT_PASS
45. **Does v9 preserve unique scan IDs?** — SOURCE_PASS
46. **Are newly published recommendation IDs attributed once?** — SOURCE_PASS
47. **Can slow scans also reach the target?** — AUDIT_PASS
48. **Should 120s be used as a live veto today?** — NO
49. **Does time of day confound observed performance?** — YES
50. **Is median v27 completed scan shorter than 90s?** — NO

## API request and broker cooldown diagnosis

51. **Are Alpha 429s observed in the latest available v27 ZIP?** — AUDIT_PASS
52. **Are Beta and Alpha counters kept separate?** — SOURCE_PASS
53. **Are historical 429s the only rate-limit problem?** — NO
54. **Can holdings/positions calls also receive 429s?** — AUDIT_PASS
55. **Does existing admission block locally during cooldown?** — SOURCE_PASS
56. **Do local pre-dispatch waits differ from HTTP failures?** — YES
57. **Does v9 observe per-endpoint 1-second admitted counts?** — SOURCE_GATE
58. **Does v9 observe per-endpoint 60-second admitted counts?** — SOURCE_GATE
59. **Do these measurements prove Groww's private quota limits?** — NO
60. **Does v9 increase API request volume for shadow scoring?** — NO

## Execution, GTT and losses

61. **Is the common long stop policy set to minus 2%?** — SOURCE_GATE
62. **Is the release-integrity stop manifest also minus 2%?** — SOURCE_GATE
63. **Is a stop trigger a guaranteed fill at the same price?** — NO
64. **Is confirmed average fill the stop-price basis?** — SOURCE_PASS
65. **Are +0.55% trailing activation rules unchanged?** — SOURCE_GATE
66. **Is +0.50% initial gain protection unchanged?** — SOURCE_GATE
67. **Are +0.10% trailing increments preserved?** — SOURCE_GATE
68. **Do existing tighter GTTs remain untouched?** — SOURCE_GATE
69. **Does an old GTT require broker verification after update?** — YES
70. **Is it safe to uninstall a live debug APK with unmanaged holdings?** — NO

## 5-minute challenger and outcome observation

71. **Does master retain v26 optional 5m post-close research?** — SOURCE_GATE
72. **Does the 5m collector use a separate broker request when permitted?** — YES_BOUNDED
73. **Can 5m data ever alter Fixed production decisions?** — NO
74. **Does v16/v19 shadow scoring require any new API call?** — NO
75. **Are v16/v19 research labels based on identical response inputs?** — SOURCE_GATE
76. **Is v16 quality compared under a neutral-breadth proxy?** — YES_LIMITATION
77. **Is live full-market v16 ranking replayed in v9?** — NO
78. **Are full 5m paired cohorts available in uploaded v25-v27 logs?** — NO
79. **Are concurrent target/stop touches in the same candle ambiguous?** — YES
80. **Can 5m be declared more profitable without slippage and fill testing?** — NO

## Export reliability and regression replay

81. **Does Android currently stream exports to ZIP entries?** — SOURCE_PASS
82. **Was oversize-export failure reported historically?** — PROJECT_EVIDENCE
83. **Do the five current diagnostic ZIPs exist and open?** — AUDIT_PASS
84. **Do the ZIPs preserve complete historical 15m series per candidate?** — NO
85. **Are most factor snapshots 3-candle excerpts?** — AUDIT_PASS
86. **Does v9 include a cumulative ZIP deduplication replay script?** — SOURCE_GATE
87. **Does v9 retain all current official 40-factor telemetry?** — SOURCE_GATE
88. **Can exact v16 history be reconstructed from 3 candle excerpts?** — NO
89. **Does versioned source-parity CI protect both v19 refs?** — SOURCE_GATE
90. **Will a single APK self-test establish a better live target rate?** — NO

## Release controls and Monday acceptance

91. **Is v9 a new separate branch rather than mutating v28?** — SOURCE_PASS
92. **Does master inherit all v28 functionality?** — SOURCE_PASS
93. **Is v9 versionCode greater than v28?** — SOURCE_GATE
94. **Is there a dedicated Android compile/test workflow?** — SOURCE_GATE
95. **Does CI cover exact v16 source copy parity?** — SOURCE_GATE
96. **Does CI cover frozen v19 production function parity?** — SOURCE_GATE
97. **Does CI cover -2% stop and original trailing rules?** — SOURCE_GATE
98. **Should Auto Buy stay OFF in initial v9 TEST?** — YES
99. **Must GTT ownership be reconciled in Groww before auto trading?** — YES
100. **Should a winner be promoted without matched, matured, out-of-sample evidence?** — NO

## Conditions of a valid strategy winner

Only compare variants on identical historical input windows, without future candles. Count matured target-before-stop observations using a consistent horizon and distinguish 5-minute LTP sampling from true candle high/low. Include no-trade, unsampled, 429-deferred and stale-signal cases in a separate denominator; do not convert missing data to misses. Quantify net executable return after applicable brokerage and slippage; use a held-out time segment, and do not promote v16 merely because it accepted unverified spread/candle data. Prior v7.4.1 reference is golden source parity only, not a full historical return study.

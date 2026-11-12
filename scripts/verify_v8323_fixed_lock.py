#!/usr/bin/env python3
"""Release gate: v8.3.23 must not change v8.3.22 Fixed selection or trading."""
from pathlib import Path
import subprocess

BASE = "2cf595d8d5fbfbda3eda2bd90750da9799f8fc37"
ROOT = Path("app/src/main/java/com/multify/traderpro")

def earlier(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"])

def same(path):
    if Path(path).read_bytes() != earlier(path):
        raise AssertionError(f"Frozen trading dependency changed: {path}")

immutable_paths = [
    "engine/FixedLocalStrategyEngine.kt",
    "engine/FixedForecastQualityPolicy.kt",
    "engine/FixedForecastPublicationPolicy.kt",
    "engine/FixedMarketDiscoveryPolicy.kt",
    "engine/FixedFinalContract.kt",
    "engine/FixedHistoricalWindowStore.kt",
    "engine/FixedDiscoveryWindowScheduler.kt",
    "engine/FixedPrimaryCarryForwardPolicy.kt",
    "data/network/GrowwRateLimitInterceptor.kt",
    "engine/SteppedTrailingPolicy.kt",
    "engine/HardStopLossPolicy.kt",
    "engine/HoldingSmartOrderPolicy.kt",
    "engine/GttExitReconciliationPolicy.kt",
    "engine/FixedDataPipelinePolicy.kt",
]
for relative in immutable_paths:
    same(ROOT / relative)

repo_path = ROOT / "data/repository/TradingRepository.kt"
latest = repo_path.read_text()
before = earlier(repo_path).decode()
def code_between(source, begin, end):
    i = source.index(begin)
    return source[i:source.index(end, i)]
for first, last in [
    ("suspend fun generateFixedForecasts(", "private fun fixedSnapshotMultifyMfeAverage("),
    ("private suspend fun fixedForecastEvaluationUsingAccount(",
     "suspend fun prepareNextFixedDiscoverySweep("),
    ("private suspend fun discoverFixedMarketWideCandidates(",
     "private suspend fun fetchFixedDiscoveryLtpBatch("),
]:
    if code_between(latest, first, last) != code_between(before, first, last):
        raise AssertionError(f"v8.3.22 Fixed candidate/scoring method drift: {first}")

# Verified transport-only modifications have no NEW full-sweep request.
assert latest.count("collectFixedWideLtp(") == before.count("collectFixedWideLtp(")
assert "PREFETCH_NOT_STARTED_USE_OFFICIAL_SWEEP" in latest
assert "officialSweepAllowedWithoutDuplication(old.started)" in latest
assert "if (fixedSweepPreparationActive || officialSweepPending)" in latest
assert "batch_window_latency_p95_ms" in latest
assert "extra_ltp_requests_for_profiling" in latest

service = (ROOT / "service/TradingEngineService.kt").read_text()
n = service.index("lastFixedCycleCompletedAtMs = System.currentTimeMillis()")
assert service.index("notifyNewFixedRecommendationsAfterOrderPath()", n) < service.index(
    "repository.prepareNextFixedDiscoverySweep()", n
)
assert "versionCode = 8404" in Path("app/build.gradle.kts").read_text()
assert 'versionName = "8.3.23-fixed-pipeline-reliability"' in Path("app/build.gradle.kts").read_text()
print("PASS: v8.3.22 Fixed candidate universe, scoring, API protection and 90s cadence unchanged")

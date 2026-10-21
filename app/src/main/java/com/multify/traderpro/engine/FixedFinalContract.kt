package com.multify.traderpro.engine

/**
 * Final immutable Fixed recommendation contract.
 *
 * Ground truth:
 * MultifyTraderPro-logs-v7.4.1-forecast-gtt-autobuy-debug-20261021-190239.zip
 * The recommendation engine is the exact v7.4.1 implementation; only the explicitly requested
 * Fixed overlays are allowed outside it: >₹100 publication eligibility, 09:15-15:30 IST session,
 * and Fixed-only remaining-wallet execution sizing.
 */
object FixedFinalContract {
    const val CONTRACT_ID = "FIXED-V741-20261021-190239-FINAL"
    const val SOURCE_COMMIT = "ca0eecaf63d2cc3500e11fd73f8de9bf2d87e39f"
    const val SOURCE_LOG_SHA256 = "d807204a6d5d71a69f45ef4d21d7ef63063b85b56d401a6795e8f265a9f393fe"
    const val REFERENCE_DATE = "2026-10-21"
    const val REFERENCE_EXPORT_AT_MS = 1792589559000L
    const val CANONICAL_LEARNING_CALLS = 107
    const val CANONICAL_MULTIFY_MFE_AVG_PCT = 1.1161043030113955
    const val MIN_FIXED_PUBLICATION_PRICE_EXCLUSIVE = 100.0

    // This extra call existed only in an older merged diagnostic bundle and was not present in the
    // final v7.4.1 session whose recommendations are the Fixed ground truth.
    const val EXCLUDED_LEARNING_DATE = "2026-10-19"
    const val EXCLUDED_LEARNING_SYMBOL = "SPORTKING"

    // Golden observed v7.4.1 session output. These are verification references, not training rows.
    val REFERENCE_FORECAST_SYMBOLS = listOf(
        "UTTAMSUGAR", "CHEMBONDCH", "BOHRAIND", "ORIRAIL",
        "DWARKESH", "SHAHINVEST", "TCC", "IPCALAB"
    )
    val REFERENCE_FIXED_ELIGIBLE_SYMBOLS = listOf(
        "UTTAMSUGAR", "CHEMBONDCH", "ORIRAIL", "SHAHINVEST", "IPCALAB"
    )
}

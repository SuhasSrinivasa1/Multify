package com.multify.traderpro.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.EngineHealthDto
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.WorkingRangeRecommendationDto
import com.multify.traderpro.ui.theme.MultifyTraderTheme
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.repository.EquityLowObservation
import com.multify.traderpro.data.repository.EquityResearchSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FixedLiteUiTest {
    @get:Rule val compose = createComposeRule()

    private fun ist(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, 20, hour, minute)
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test fun fixedHomeExplainsClosedSessionInsteadOfFakeZeroCoverage() {
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteFixedScreen(TraderUiState(dashboard = DashboardDto(fixedHealth = EngineHealthDto(discovery = "WAITING"))), onScan = {}, nowMs = ist(20, 18), onBuy = {})
            }
        }
        compose.onNodeWithText("Frozen 15m strategy").assertIsDisplayed()
        compose.onNodeWithText("Run Fixed scan").assertIsDisplayed()
        compose.onNodeWithText("MARKET CLOSED").assertIsDisplayed()
        compose.onNodeWithText("Run Fixed scan").assertIsNotEnabled()
        compose.onNodeWithText("Live discovery starts during the NSE session. Coverage is not measured while closed.").assertIsDisplayed()
        compose.onNodeWithTag("fixed-screen").performScrollToNode(hasText("No published recommendations"))
        compose.onNodeWithText("No published recommendations").assertIsDisplayed()
    }

    @Test fun fixedPrimaryCardSeparatesActionabilityFromSampledOutcome() {
        val row = ForecastDto(
            rank = 1,
            symbol = "TESTCO",
            confidence = 0.82,
            score = 0.91,
            entryPrice = 250.0,
            targetPrice = 252.79,
            targetPct = 1.116,
            status = "ACTIVE",
            maxFavourablePct = 0.74,
            maxAdversePct = -0.18,
            generatedAtMs = 1_800_000_000_000L,
            qualityTier = "PRIMARY",
            executionEligible = true,
            fixedFirstPrimaryPrice = 249.5,
            fixedActualPublishedAtMs = 1_800_000_005_000L
        )
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteFixedScreen(
                    TraderUiState(
                        settings = AppSettings(
                            brokerAuthenticated = true,
                            staticIpMatched = true
                        ),
                        dashboard = DashboardDto(fixedForecasts = listOf(row))
                    ),
                    onScan = {},
                    nowMs = ist(10, 0),
                    onBuy = {}
                )
            }
        }
        compose.onNodeWithTag("fixed-screen").performScrollToNode(hasText("TESTCO"))
        compose.onNodeWithText("TESTCO").assertIsDisplayed()
        compose.onNodeWithText("PRIMARY").assertIsDisplayed()
        compose.onNodeWithText("ACTIONABLE").assertIsDisplayed()
        compose.onNodeWithText("Manual BUY · Alpha").assertIsEnabled()
    }

    @Test fun workingRangeCardShowsTwoScanPilotAndKeepsOutcomeSamplingExplicit() {
        val row = WorkingRangeRecommendationDto(
            symbol = "RANGECO",
            state = "IN_RANGE",
            entryPrice = 100.0,
            targetPrice = 101.12,
            targetPct = 1.116,
            firstQualifiedPrice = 99.8,
            confirmedAtMs = 1_800_000_000_000L,
            lastObservedAtMs = 1_800_000_005_000L,
            lastPrice = 100.2,
            candidateAgeMinutes = 3.0,
            driftFromFirstQualifiedPct = 0.20,
            rsi = 84.0,
            rvol = 2.2,
            vwapExtensionAtr = 1.1,
            maxFavourablePct = 0.35,
            maxAdversePct = -0.12,
            sampledOutcomeNote = "UNRESOLVED · SPARSE CLIENT LTP SAMPLES",
            reason = "Two distinct scans confirmed the range",
            autoBuyState = "NOT_ATTEMPTED"
        )
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteRangeScreen(TraderUiState(dashboard = DashboardDto(workingRangeRecommendations = listOf(row))))
            }
        }
        compose.onNodeWithText("TWO DISTINCT SCANS").assertIsDisplayed()
        compose.onNodeWithTag("working-range-screen").performScrollToNode(hasText("RANGECO"))
        compose.onNodeWithText("RANGECO").assertIsDisplayed()
        compose.onNodeWithText("IN_RANGE").assertIsDisplayed()
        compose.onNodeWithText("UNRESOLVED · SPARSE CLIENT LTP SAMPLES").assertIsDisplayed()
    }

    @Test fun bottomNavigationExposesFixedWorkingRangeAndManual() {
        var selected = LitePage.FIXED
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteBottomBar(page = selected, onNavigate = { selected = it })
            }
        }
        compose.onNodeWithText("Fixed").assertIsDisplayed()
        compose.onNodeWithText("Working Range").assertIsDisplayed()
        compose.onNodeWithText("Manual").assertIsDisplayed()
        compose.onNodeWithText("Working Range").performClick()
        compose.runOnIdle { assertEquals(LitePage.WORKING_RANGE, selected) }
        compose.onNodeWithText("Manual").performClick()
        compose.runOnIdle { assertEquals(LitePage.MANUAL, selected) }
    }
    @Test fun lowScreenerShowsOnlyLargeAndMidCapsInBothLookbacks() {
        val date = LocalDate.of(2026, 11, 20)
        fun row(symbol: String, category: String, rank: Int?) = EquityLowObservation(
            symbol = symbol,
            close = 100.0,
            periodLow = 99.0,
            distancePct = 1.01,
            tradingDays = 55,
            latestDate = date,
            periodLowDate = date.minusDays(1),
            oneMonthLow = 99.0,
            oneMonthDistancePct = 1.01,
            lookbackMonths = 3,
            marketCapCategory = category,
            marketCapRank = rank
        )
        val snapshot = EquityResearchSnapshot(
            threeMonthLowRows = listOf(
                row("LARGE3", "LARGE_CAP", 10),
                row("MID3", "MID_CAP", 150),
                row("SMALL3", "SMALL_CAP", 300),
                row("UNKNOWN3", "UNCLASSIFIED", null)
            ),
            sixMonthLowRows = listOf(
                row("LARGE6", "LARGE_CAP", 20),
                row("MID6", "MID_CAP", 200),
                row("SMALL6", "SMALL_CAP", 400),
                row("UNKNOWN6", "UNCLASSIFIED", null)
            ),
            threeMonthCompletedDays = 60,
            completedDays = 120,
            threeMonthFirstDay = "2026-08-20",
            firstDay = "2026-05-01",
            latestDay = "2026-11-20",
            threeMonthHistoryComplete = true,
            sixMonthHistoryComplete = true,
            marketCapClassificationPeriod = "Jan–Jun 2026",
            marketCapClassificationCount = 350
        )
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                SixMonthLowResearchScreen(
                    TraderUiState(equityResearch = snapshot),
                    onScan = {}
                )
            }
        }

        compose.onNodeWithTag("six-month-low-screen")
            .performScrollToNode(hasText("At/below or up to 10% above previous 3M low · 2 of 2"))
        compose.onNodeWithText("LARGE3").assertIsDisplayed()
        compose.onNodeWithText("MID3").assertIsDisplayed()
        compose.onNodeWithText("SMALL3").assertDoesNotExist()
        compose.onNodeWithText("UNKNOWN3").assertDoesNotExist()

        compose.onNodeWithTag("six-month-low-screen")
            .performScrollToNode(hasText("NSE LOWS SCREENER"))
        compose.onNodeWithTag("low-range-6m").performClick()
        compose.onNodeWithTag("six-month-low-screen")
            .performScrollToNode(hasText("At/below or up to 10% above previous 6M low · 2 of 2"))
        compose.onNodeWithText("LARGE6").assertIsDisplayed()
        compose.onNodeWithText("MID6").assertIsDisplayed()
        compose.onNodeWithText("SMALL6").assertDoesNotExist()
        compose.onNodeWithText("UNKNOWN6").assertDoesNotExist()
    }

}

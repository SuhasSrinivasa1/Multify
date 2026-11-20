package com.multify.traderpro.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FixedLiteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedHomeExplainsWaitingWithoutInventingMarketCoverage() {
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteFixedScreen(TraderUiState(dashboard = DashboardDto(fixedHealth = EngineHealthDto(discovery = "WAITING"))), onScan = {}, onBuy = {})
            }
        }
        compose.onNodeWithText("Frozen 15m strategy").assertIsDisplayed()
        compose.onNodeWithText("Run Fixed scan").assertIsDisplayed()
        compose.onNodeWithText("WAITING").assertIsDisplayed()
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
                    TraderUiState(dashboard = DashboardDto(fixedForecasts = listOf(row))),
                    onScan = {},
                    onBuy = {}
                )
            }
        }
        compose.onNodeWithTag("fixed-screen").performScrollToNode(hasText("TESTCO"))
        compose.onNodeWithText("TESTCO").assertIsDisplayed()
        compose.onNodeWithText("PRIMARY").assertIsDisplayed()
        compose.onNodeWithText("ACTIONABLE").assertIsDisplayed()
        compose.onNodeWithText("Manual BUY · Alpha").assertIsDisplayed()
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
        compose.onNodeWithText("TWO DISTINCT SCANS REQUIRED").assertIsDisplayed()
        compose.onNodeWithTag("working-range-screen").performScrollToNode(hasText("RANGECO"))
        compose.onNodeWithText("RANGECO").assertIsDisplayed()
        compose.onNodeWithText("IN_RANGE").assertIsDisplayed()
        compose.onNodeWithText("UNRESOLVED · SPARSE CLIENT LTP SAMPLES").assertIsDisplayed()
    }

    @Test fun bottomNavigationExposesOnlyTheTwoPrimaryDestinations() {
        var selected = LitePage.FIXED
        compose.setContent {
            MultifyTraderTheme(darkTheme = false) {
                LiteBottomBar(page = selected, onNavigate = { selected = it })
            }
        }
        compose.onNodeWithText("Fixed").assertIsDisplayed()
        compose.onNodeWithText("Working Range").assertIsDisplayed()
        compose.onNodeWithText("Working Range").performClick()
        compose.runOnIdle { assertEquals(LitePage.WORKING_RANGE, selected) }
    }
}

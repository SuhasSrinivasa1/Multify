package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

internal data class FixedExecutionUiState(
    val recommendationId: Long,
    val symbol: String,
    val status: String,
    val reason: String,
    val requestedQuantity: Int? = null,
    val filledQuantity: Int? = null,
    val updatedAtMs: Long
)

/** Local, event-keyed presentation state; it never grants BUY permission. */
@Singleton
class FixedExecutionUiStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fixed_execution_ui_state_v9018", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun get(recommendationId: Long): FixedExecutionUiState? =
        all().firstOrNull { it.recommendationId == recommendationId }

    @Synchronized
    fun all(): List<FixedExecutionUiState> {
        val raw = prefs.getString(KEY_STATES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<FixedExecutionUiState>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(
        recommendationId: Long,
        symbol: String,
        status: String,
        reason: String,
        requestedQuantity: Int? = null,
        filledQuantity: Int? = null
    ) {
        val row = FixedExecutionUiState(
            recommendationId = recommendationId,
            symbol = symbol.uppercase(Locale.US),
            status = status,
            reason = reason.take(240),
            requestedQuantity = requestedQuantity,
            filledQuantity = filledQuantity,
            updatedAtMs = System.currentTimeMillis()
        )
        val rows = (all().filterNot { it.recommendationId == recommendationId } + row)
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_ROWS)
        prefs.edit().putString(KEY_STATES, gson.toJson(rows)).commit()
    }

    companion object {
        private const val KEY_STATES = "states"
        private const val MAX_ROWS = 500
    }
}

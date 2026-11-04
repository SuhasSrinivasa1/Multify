package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.ForecastNewsPatternPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.Reader
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Market-wide NSE public announcement ingest. Independent from Alpha and Beta credentials. */
@Singleton
class ForecastNewsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journal: ForecastNewsJournal,
    private val audit: AuditLogger
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8)).readTimeout(Duration.ofSeconds(12)).build()
    private val prefs = context.getSharedPreferences("forecast_news_symbol_catalog", Context.MODE_PRIVATE)
    private val gson = Gson()
    @Volatile private var companyByName: Map<String, String> = emptyMap()
    private var lastCatalogAttempt = 0L
    private var lastFetchAtMs = 0L

    suspend fun poll(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetchAtMs < 55_000L) return@withContext 0
        lastFetchAtMs = now
        if (companyByName.isEmpty()) refreshCatalog(now)
        var examined = 0
        var newlyStored = 0
        var failures = 0
        var missingDate = 0
        var futureDates = 0
        var staleDates = 0
        var unresolvedSymbols = 0
        var unclassified = 0
        var duplicateTitles = 0
        var duplicateIds = 0
        var eligibleDatedItems = 0
        var newestPublishedAtMs = 0L
        var oldestPublishedAtMs = Long.MAX_VALUE
        for (feed in RSS_FEEDS) {
            try {
                val entries = fetchRss(feed.second)
                examined += entries.size
                for (entry in entries) {
                    val published = parseDate(entry.published)
                    if (published == null) { missingDate++; continue }
                    newestPublishedAtMs = maxOf(newestPublishedAtMs, published)
                    oldestPublishedAtMs = minOf(oldestPublishedAtMs, published)
                    if (published > now + 60_000L) { futureDates++; continue }
                    if (published < now - 3L * 24L * 60 * 60_000L) { staleDates++; continue }
                    eligibleDatedItems++
                    val symbol = resolveSymbol(entry.title, entry.summary)
                    if (symbol.isBlank()) unresolvedSymbols++
                    val id = sha256(feed.first + "|" + (entry.guid.ifBlank { entry.url.ifBlank { entry.title + published } }))
                    // Existing unresolved events are retained by ID but may be promoted to
                    // research when a refreshed NSE catalog uniquely resolves their issuer.
                    val old = journal.recent(1500).firstOrNull { it.id == id }
                    if (old != null) {
                        if (old.symbol.isBlank() && symbol.isNotBlank()) {
                            val reassessed = ForecastNewsPatternPolicy.assess(entry.title, entry.summary,
                                ((now - published) / 60_000L).coerceAtLeast(0), 1, 1, 1, sourceVerified = true)
                            journal.update(old.copy(
                                symbol = symbol,
                                category = reassessed.category, direction = reassessed.direction,
                                classificationConfidence = reassessed.confidence,
                                featureJson = gson.toJson(reassessed.features),
                                status = if (reassessed.direction == "POSITIVE_POSSIBLE") "NEW" else "RESEARCH_ONLY",
                                rationale = "RE_RESOLVED_NSE_COMPANY_NAME"
                            ))
                            audit.logAsync("FORECAST_NEWS", "UNRESOLVED_SYMBOL_RECOVERED", mapOf(
                                "id" to id, "symbol" to symbol, "direction" to reassessed.direction))
                        }
                        duplicateIds++
                        continue
                    }
                    val near = journal.recent(500).filter { it.symbol == symbol && symbol.isNotBlank() &&
                        now - it.firstSeenAtMs in 0L..(24L * 60 * 60_000L) }
                    val canonicalTitle = normalize(entry.title).take(180)
                    if (near.any { normalize(it.headline).take(180) == canonicalTitle }) {
                        duplicateTitles++
                        audit.logAsync("FORECAST_NEWS", "DUPLICATE_CATALYST_SKIPPED", mapOf(
                            "symbol" to symbol, "feed" to feed.first,
                            "reason" to "SAME_NSE_TITLE_IN_MULTIPLE_FEEDS"))
                        continue
                    }
                    // Distinct-title counts, not repeated RSS entries, are used for clustering.
                    fun uniqueIn(minutes: Long) = near.filter {
                        now - it.firstSeenAtMs <= minutes * 60_000L
                    }.distinctBy { normalize(it.headline).take(180) }.size + 1
                    val assess = ForecastNewsPatternPolicy.assess(entry.title, entry.summary,
                        ((now - published) / 60_000L).coerceAtLeast(0),
                        uniqueIn(5), uniqueIn(10), uniqueIn(15), sourceVerified = true)
                    if (assess.category == "OTHER" || assess.direction != "POSITIVE_POSSIBLE") unclassified++
                    val event = ForecastNewsEvent(id, feed.first, symbol, entry.title.take(300),
                        entry.summary.take(1200), entry.url.take(500), assess.category, assess.direction,
                        published, now, gson.toJson(assess.features), assess.confidence,
                        status = if (symbol.isBlank()) "UNRESOLVED_SYMBOL" else if (assess.category == "OTHER" ||
                            assess.direction != "POSITIVE_POSSIBLE") "RESEARCH_ONLY" else "NEW",
                        rationale = assess.reason)
                    if (journal.addIfAbsent(event)) {
                        newlyStored++
                        audit.logAsync("FORECAST_NEWS", "EVENT_CAPTURED", mapOf(
                            "id" to id, "symbol" to symbol, "category" to assess.category,
                            "direction" to assess.direction, "published_at_ms" to published,
                            "observed_at_ms" to now, "confidence" to assess.confidence,
                            "feature_count" to assess.features.size,
                            "status" to event.status, "source_exchange" to "NSE",
                            "reported_amount_crore" to assess.features["reported_amount_crore"],
                            "catalyst_count_15m" to assess.features["cluster_15m"],
                            "distinct_recent_nse_titles_24h" to near.distinctBy {
                                normalize(it.headline).take(180) }.size + 1,
                            "off_market_collection" to (java.time.Instant.ofEpochMilli(now)
                                .atZone(java.time.ZoneId.of("Asia/Kolkata")).hour !in 9..15)))
                    } else duplicateIds++
                }
            } catch (err: Exception) {
                failures++
                audit.logAsync("FORECAST_NEWS", "FEED_ERROR", mapOf(
                    "feed" to feed.first, "error_type" to err.javaClass.simpleName,
                    "message" to (err.message ?: "").take(140)))
            }
        }
        audit.logAsync("FORECAST_NEWS", "POLL_PROFILE", mapOf("items_examined" to examined,
            "new_events" to newlyStored, "source_failures" to failures,
            "catalog_size" to companyByName.size, "poll_started_at_ms" to now,
            "symbol_resolution_policy" to "ANCHORED_UNAMBIGUOUS_CORPORATE_NAME",
            "unresolved_titles_are_research_not_orders" to true,
            "date_parse_failed" to missingDate, "future_dated" to futureDates,
            "older_than_72h" to staleDates, "dated_in_window" to eligibleDatedItems,
            "unresolved_nse_symbol" to unresolvedSymbols,
            "other_or_nonpositive_category" to unclassified,
            "duplicate_titles" to duplicateTitles, "duplicate_event_ids" to duplicateIds,
            "newest_feed_age_minutes" to (if (newestPublishedAtMs > 0L)
                ((now - newestPublishedAtMs) / 60_000L) else null),
            "oldest_feed_age_minutes" to (if (oldestPublishedAtMs < Long.MAX_VALUE)
                ((now - oldestPublishedAtMs) / 60_000L) else null),
            "nse_only" to true))
        newlyStored
    }

    private fun refreshCatalog(now: Long) {
        val cached = prefs.getString("catalog", null)
        if (cached != null && companyByName.isEmpty()) {
            companyByName = cached.lineSequence().mapNotNull { line ->
                val pos = line.indexOf('|'); if (pos < 1) null else line.substring(0,pos) to line.substring(pos + 1)
            }.toMap()
        }
        if (now - lastCatalogAttempt < 60L * 60 * 1000) return
        lastCatalogAttempt = now
        runCatching {
            val csv = fetch(NseSymbolRepository.EQUITY_URL)
            val rows = csv.lineSequence().drop(1).mapNotNull { line ->
                val cells = parseCsv(line)
                val symbol = cells.getOrNull(0)?.trim()?.uppercase(Locale.ROOT) ?: return@mapNotNull null
                val company = cells.getOrNull(1)?.trim() ?: return@mapNotNull null
                if (!symbol.matches(Regex("[A-Z0-9&._-]{1,32}"))) return@mapNotNull null
                normalize(company) to symbol
            }.toMap()
            require(rows.size > 500)
            companyByName = rows
            prefs.edit().putString("catalog", rows.entries.joinToString("\n") { it.key + "|" + it.value }).apply()
            audit.logAsync("FORECAST_NEWS", "SYMBOL_CATALOG_READY", mapOf("count" to rows.size))
        }.onFailure {
            audit.logAsync("FORECAST_NEWS", "CATALOG_ERROR", mapOf("type" to it.javaClass.simpleName))
        }
    }

    private fun resolveSymbol(title: String, summary: String): String =
        ForecastNewsSymbolResolutionPolicy.resolve(title, companyByName)


    internal fun parseDate(s: String): Long? = ForecastNewsDatePolicy.parse(s)

    internal data class RssItem(val title: String, val summary: String, val published: String, val url: String, val guid: String)
    /** Streaming parsing and bounded text prevent a malformed NSE feed from exhausting memory.
     * Completed items are retained if malformed XML appears later in the response. */
    internal fun parseRss(xml: String): List<RssItem> = parseRss(StringReader(xml))

    internal fun parseRss(reader: Reader): List<RssItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(reader) }
        val results = ArrayList<RssItem>()
        var inside = false; var title = ""; var desc = ""; var date = ""; var link = ""; var guid = ""
        var tag = ""
        fun cap(original: String, incoming: String?, limit: Int): String =
            if (original.length >= limit) original else original +
                incoming.orEmpty().take(limit - original.length)
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT && results.size < 1_000) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name.lowercase(Locale.ROOT)
                        if (tag == "item") {
                            inside = true; title = ""; desc = ""; date = ""; link = ""; guid = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (inside) when (tag) {
                        "title" -> title = cap(title, parser.text, 400)
                        "description" -> desc = cap(desc, parser.text, 2_000)
                        "pubdate", "published" -> date = cap(date, parser.text, 100)
                        "link" -> link = cap(link, parser.text, 500)
                        "guid" -> guid = cap(guid, parser.text, 500)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("item", true) && inside) {
                            if (title.isNotBlank())
                                results += RssItem(title.trim(), desc.trim(), date.trim(), link.trim(), guid.trim())
                            inside = false
                        }
                        tag = ""
                    }
                }
                parser.next()
            }
        } catch (problem: Exception) {
            if (results.isEmpty()) throw problem
            audit.logAsync("FORECAST_NEWS", "PARTIAL_RSS_RECOVERED", mapOf(
                "completed_items_retained" to results.size,
                "failure_type" to problem.javaClass.simpleName,
                "research_only" to true))
        }
        return results
    }

    /** Never load a multi-megabyte RSS response into a single String. */
    private fun fetchRss(url: String): List<RssItem> =
        http.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 MultifyForecastNews/1.0")
            .header("Accept", "application/rss+xml,application/xml,text/xml,*/*")
            .build()).execute().use { response ->
            if (!response.isSuccessful) error("Source returned HTTP ${response.code}")
            val body = response.body ?: error("Missing RSS body")
            require(body.contentLength() <= 3_000_000L || body.contentLength() < 0L) {
                "RSS feed exceeds three megabytes"
            }
            body.charStream().use { parseRss(LimitedNewsReader(it, 3_000_000L)) }
        }

    /** Reader-level limit also protects chunked HTTP bodies with unknown Content-Length. */
    private class LimitedNewsReader(private val input: Reader, private val maxChars: Long) : Reader() {
        private var seen = 0L
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (seen >= maxChars) throw IllegalStateException("RSS char limit reached")
            val got = input.read(cbuf, off, minOf(len.toLong(), maxChars - seen).toInt())
            if (got > 0) seen += got
            return got
        }
        override fun close() = input.close()
    }

    private fun fetch(url: String): String = http.newCall(Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 MultifyForecastNews/1.0")
        .header("Accept", "text/csv,*/*")
        .build()).execute().use { response ->
        if (!response.isSuccessful) error("Source returned HTTP ${response.code}")
        val body = response.body ?: error("Missing NSE catalog response")
        val result = StringBuilder()
        body.charStream().use { reader ->
            val limited = LimitedNewsReader(reader, 3_000_000L)
            val chunk = CharArray(8192)
            while (true) {
                val count = limited.read(chunk, 0, chunk.size)
                if (count <= 0) break
                result.append(chunk, 0, count)
            }
        }
        require(result.isNotEmpty()) { "Empty NSE catalog" }
        result.toString()
    }

    private fun parseCsv(text: String): List<String> {
        val result = ArrayList<String>(); var quote = false; val sb = StringBuilder(); var i = 0
        while (i < text.length) {
            when (text[i]) {
                '"' -> if (quote && i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else quote = !quote
                ',' -> if (quote) sb.append(',') else { result += sb.toString(); sb.setLength(0) }
                else -> sb.append(text[i])
            }
            i++
        }
        result += sb.toString()
        return result
    }

    private fun normalize(text: String): String = text.uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val RSS_FEEDS = listOf(
            "NSE_ANNOUNCEMENTS" to "https://nsearchives.nseindia.com/content/RSS/Online_announcements.xml",
            "NSE_FINANCIAL_RESULTS" to "https://nsearchives.nseindia.com/content/RSS/Financial_Results.xml"
        )
    }
}

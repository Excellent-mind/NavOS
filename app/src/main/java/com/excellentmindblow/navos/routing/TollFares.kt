package com.excellentmindblow.navos.routing

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * The LLM toll table, read once in the background. Null until loaded or when
 * no table can be read - then route cards just count the tolls.
 *
 * Where it comes from, newest first:
 *  1. filesDir/tolls/llm_rates.json - the last copy downloaded from Supabase
 *  2. assets/tolls/llm_rates.json   - the copy built into the app
 *
 * About once a week it asks Supabase (rpc toll_fares_doc) for the current
 * table, so new LLM fares reach phones without an app update. A download is
 * only kept when it parses, looks complete and is not older than what we have.
 */
object TollFares {
    private const val TAG = "NavOSTolls"
    private const val ASSET = "tolls/llm_rates.json"
    private const val CACHE = "tolls/llm_rates.json"
    private const val CHECKED = "tolls/checked"
    private const val REFRESH_MS = 7L * 24 * 60 * 60 * 1000
    private const val TIMEOUT_MS = 15_000

    private const val SUPABASE_URL = "https://mksfocirdngualkahuqi.supabase.co"

    // Publishable key: safe to ship, only allows what the tables' public-read rules allow.
    private const val SUPABASE_KEY = "sb_publishable_L9X5RO2x2aVTICl-st9PbQ_gf_E87We"

    /** Fewer plazas than this means a broken or partial download. */
    internal const val MIN_PLAZAS = 100

    @Volatile
    var pricer: TollPricer? = null
        private set

    /** The table's "fetched" date (yyyy-mm-dd), for comparing versions. */
    @Volatile
    private var fetched: String = ""

    @Volatile
    private var started = false

    fun load(
        context: Context,
        onReady: () -> Unit,
    ) {
        if (started) return
        started = true
        val app = context.applicationContext
        Executors.newSingleThreadExecutor().execute {
            val cache = File(app.filesDir, CACHE)
            val local =
                runCatching { if (cache.exists()) parse(cache.readText()) else null }
                    .onFailure { Log.w(TAG, "cached toll table: $it") }
                    .getOrNull()
                    ?: runCatching { app.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }
                        .onFailure { Log.w(TAG, "toll table: $it") }
                        .getOrNull()
            if (local != null) {
                pricer = local.first
                fetched = local.second
                onReady()
            }
            refresh(app, onReady)
        }
    }

    private fun refresh(
        app: Context,
        onReady: () -> Unit,
    ) {
        val checked = File(app.filesDir, CHECKED)
        if (System.currentTimeMillis() - checked.lastModified() < REFRESH_MS) return
        runCatching {
            val text = download()
            val remote = accept(text, fetched)
            checked.parentFile?.mkdirs()
            checked.writeText(System.currentTimeMillis().toString())
            if (remote != null) {
                val cache = File(app.filesDir, CACHE)
                val tmp = File(cache.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(cache)) throw IOException("could not save toll table")
                pricer = remote.first
                fetched = remote.second
                Log.i(TAG, "toll table updated to ${remote.second}")
                onReady()
            }
        }.onFailure { Log.w(TAG, "toll refresh: $it") }
    }

    private fun download(): String {
        val conn = URL("$SUPABASE_URL/rest/v1/rpc/toll_fares_doc").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("tolls ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Reads a table; the "fetched" date rides along for version checks. */
    internal fun parse(text: String): Pair<TollPricer, String> {
        val json = JSONObject(text)
        return TollPricer.fromJson(json) to json.optString("fetched", "")
    }

    /**
     * A downloaded table, if it is worth keeping: it must parse, know at least
     * [MIN_PLAZAS] plazas and some fares, and not be older than [currentFetched].
     */
    internal fun accept(
        text: String,
        currentFetched: String,
    ): Pair<TollPricer, String>? {
        val (table, date) = runCatching { parse(text) }.getOrNull() ?: return null
        if (table.plazaCount < MIN_PLAZAS || table.pairCount == 0) return null
        // ISO dates compare correctly as text.
        if (currentFetched.isNotEmpty() && date < currentFetched) return null
        return table to date
    }
}

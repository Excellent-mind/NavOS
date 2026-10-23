package com.excellentmindblow.navos.routing

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * The bundled LLM toll table (assets/tolls/llm_rates.json, built by
 * tools/tolls/build_rates.py), read once in the background. Null until loaded
 * or when the table is missing - then route cards just count the tolls.
 */
object TollFares {
    @Volatile
    var pricer: TollPricer? = null
        private set

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
            pricer =
                runCatching {
                    app.assets.open("tolls/llm_rates.json").bufferedReader().use { TollPricer.fromJson(JSONObject(it.readText())) }
                }.onFailure { Log.w("NavOSTolls", "toll table: $it") }.getOrNull()
            if (pricer != null) onReady()
        }
    }
}

package com.excellentmindblow.navos.traffic

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Whether this phone shares anonymous speeds (asked once, changeable in Settings). */
object TrafficPrefs {
    const val PREFS_NAME = "navos_prefs"
    const val KEY_SHARE = "traffic_share"
    const val KEY_ASKED = "traffic_asked"

    fun sharing(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_SHARE, false)

    fun asked(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_ASKED, false)

    fun set(
        prefs: SharedPreferences,
        share: Boolean,
    ) = prefs.edit().putBoolean(KEY_SHARE, share).putBoolean(KEY_ASKED, true).apply()
}

/**
 * Talks to NavOS's live-traffic server (Cloudflare Worker "navos-traffic",
 * source in server/traffic-worker). Blocking: call off the main thread.
 */
object TrafficClient {
    private const val BASE = "https://navos-traffic.synnamer.workers.dev/v1"
    private const val USER_AGENT = "NavOS/0.1 (navigation app)"

    /** Sends road speeds measured on this drive. [session] is random per drive. */
    @Throws(IOException::class)
    fun sendProbes(
        session: String,
        probes: List<Pair<String, Double>>,
    ) {
        if (probes.isEmpty()) return
        val p = JSONArray()
        for ((seg, kmh) in probes) p.put(JSONArray().put(seg).put(kmh))
        post("/probe", JSONObject().put("s", session).put("p", p))
    }

    /** Live speed (km/h) of those [pieces] that 3+ drivers measured in the last 10 minutes. */
    @Throws(IOException::class)
    fun flow(pieces: List<String>): Map<String, Double> {
        if (pieces.isEmpty()) return emptyMap()
        val body = post("/flow", JSONObject().put("segs", JSONArray(pieces)))
        val f = body.optJSONObject("f") ?: return emptyMap()
        val out = HashMap<String, Double>()
        for (k in f.keys()) f.optJSONArray(k)?.let { out[k] = it.optDouble(0) }
        return out
    }

    private fun post(
        path: String,
        body: JSONObject,
    ): JSONObject {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("traffic ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

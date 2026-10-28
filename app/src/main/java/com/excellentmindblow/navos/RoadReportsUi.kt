package com.excellentmindblow.navos

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.traffic.RoadReports
import com.excellentmindblow.navos.traffic.SmartLanes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

/**
 * Road reports on the drive screen: the Report button and its picker,
 * reports on the route ahead (pins, a banner with the distance, one spoken
 * alert each), and "Still there?" right after passing one - the answers
 * keep reports fresh and remove stale ones for everyone. Anonymous.
 */
class RoadReportsUi(
    private val activity: Activity,
    private val host: Host,
) {
    interface Host {
        fun track(): RouteTrack?

        fun progressM(): Double

        fun here(): RoutePoint?

        fun heading(): Double?

        fun announce(
            key: String,
            text: String,
        )

        /** The report pins changed. */
        fun redrawPins()

        /** The Smart Lanes open on the route now (painted green on the map). */
        fun paintSmartLanes(lanes: List<SmartLanes.Lane>)
    }

    /** Smart Lanes the route drives along. */
    private var smartOnRoute: List<SmartLanes.OnRoute> = emptyList()
    private var smartTrack: RouteTrack? = null
    private var smartPainted: List<String> = emptyList()
    private val smartAlerted = HashSet<String>()

    /** Reports on the route being driven, sorted along it. */
    var onRoute: List<RoadReports.OnRoute> = emptyList()
        private set

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var running = false
    private var all: List<RoadReports.Report> = emptyList()
    private val alerted = HashSet<Int>()
    private val mine = HashSet<Int>()
    private val voted = HashSet<Int>()
    private var current: RoadReports.OnRoute? = null
    private var votingUntil = 0L
    private var voteId = -1

    private val banner: View = activity.findViewById(R.id.reportAlert)
    private val icon: ImageView = activity.findViewById(R.id.reportAlertIcon)
    private val title: TextView = activity.findViewById(R.id.reportAlertTitle)
    private val sub: TextView = activity.findViewById(R.id.reportAlertSub)
    private val distance: TextView = activity.findViewById(R.id.reportAlertDistance)
    private val voteRow: View = activity.findViewById(R.id.reportAlertVote)

    private val poll =
        object : Runnable {
            override fun run() {
                if (!running) return
                refresh()
                main.postDelayed(this, POLL_MS)
            }
        }

    init {
        activity.findViewById<View>(R.id.reportStillThere).setOnClickListener { answer(true) }
        activity.findViewById<View>(R.id.reportNotThere).setOnClickListener { answer(false) }
        activity.findViewById<View>(R.id.reportButton).setOnClickListener { showPicker() }
    }

    /** A drive started, or the route changed. */
    fun start() {
        running = true
        main.removeCallbacks(poll)
        main.post(poll)
    }

    fun stop() {
        running = false
        main.removeCallbacks(poll)
        all = emptyList()
        onRoute = emptyList()
        smartOnRoute = emptyList()
        smartTrack = null
        smartAlerted.clear()
        if (smartPainted.isNotEmpty()) {
            smartPainted = emptyList()
            host.paintSmartLanes(emptyList())
        }
        current = null
        votingUntil = 0
        hide()
        host.redrawPins()
    }

    fun reportById(id: Int): RoadReports.Report? = all.firstOrNull { it.id == id }

    private fun refresh() {
        val track = host.track() ?: return
        val box = RoadReports.boxAhead(track, host.progressM().takeIf { !it.isNaN() } ?: 0.0) ?: return
        io.execute {
            val found =
                try {
                    RoadReports.fetch(box)
                } catch (e: Exception) {
                    Log.w(LOG, "reports: ${e.message}")
                    return@execute
                }
            val placed = RoadReports.onRoute(track, found)
            main.post {
                if (!running || host.track() !== track) return@post
                all = found
                onRoute = placed
                host.redrawPins()
            }
        }
    }

    /** Every GPS fix while driving. */
    fun onProgress() {
        if (!running) return
        val now = System.currentTimeMillis()
        if (now < votingUntil) return
        if (votingUntil != 0L) {
            votingUntil = 0
            hide()
        }
        val progress = host.progressM()
        if (progress.isNaN()) return
        val next = RoadReports.nextAhead(onRoute, progress, ALERT_M)
        if (next != null) {
            show(next, progress)
            current = next
            return
        }
        val smart = smartLaneAhead(progress, now)
        if (smart != null) {
            showSmartLane(smart, progress, now)
            return
        }
        val passed = current
        if (passed != null) {
            val behind = progress - passed.atM
            // Alongside it: keep the banner up until it is properly behind.
            if (behind < PASSED_M) {
                show(passed, progress)
                return
            }
            current = null
            if (behind <= ASK_UNTIL_M && passed.report.id !in mine && passed.report.id !in voted) {
                askStillThere(passed.report)
                return
            }
        }
        hide()
    }

    private fun show(
        r: RoadReports.OnRoute,
        progress: Double,
    ) {
        val report = r.report
        voteRow.visibility = View.GONE
        icon.setImageBitmap(MapIcons.reportBadge(activity, report.kind, 40))
        title.text = report.kind.label
        sub.text = RoadReports.detail(report)
        distance.text = Units.short((r.atM - progress).coerceAtLeast(10.0))
        distance.visibility = View.VISIBLE
        Motion.reveal(banner, pivotFraction = 0f)
        if (alerted.add(report.id) && report.id !in mine) host.announce("report-${report.id}", report.kind.spoken)
    }

    /**
     * The Smart Lane on the route from [SMART_ALERT_M] before it to its end,
     * when it is open now: by its schedule, a dated activation, or a driver's
     * "Smart lane open" report along it. Also repaints the open lanes.
     */
    private fun smartLaneAhead(
        progress: Double,
        now: Long,
    ): SmartLanes.OnRoute? {
        val track = host.track() ?: return null
        if (smartTrack !== track) {
            smartTrack = track
            smartOnRoute = SmartLanes.onRoute(track, SmartLanes.lanes)
        }
        val open =
            smartOnRoute.filter { s ->
                SmartLanes.isOpen(s.lane, now) ||
                    onRoute.any { it.report.kind == RoadReports.Kind.SMART_LANE && it.atM in (s.startM - 300.0)..s.endM }
            }
        val ids = open.map { it.lane.id }
        if (ids != smartPainted) {
            smartPainted = ids
            host.paintSmartLanes(open.map { it.lane })
        }
        return open.firstOrNull { progress in (it.startM - SMART_ALERT_M)..it.endM }
    }

    private fun showSmartLane(
        s: SmartLanes.OnRoute,
        progress: Double,
        now: Long,
    ) {
        val lane = s.lane
        voteRow.visibility = View.GONE
        icon.setImageBitmap(MapIcons.reportBadge(activity, RoadReports.Kind.SMART_LANE, 40))
        val on = progress >= s.startM
        title.text = if (on) tr("On the Smart lane", "Atas Smart lane") else RoadReports.Kind.SMART_LANE.label
        val until = SmartLanes.openUntil(lane, now)?.let { m -> String.format(java.util.Locale.US, "%d:%02d", ((m / 60) % 12).let { h -> if (h == 0) 12 else h }, m % 60) }
        sub.text =
            listOfNotNull(
                tr("Left emergency lane", "Lorong kecemasan kiri"),
                tr("max ${lane.speedKmh} km/h", "had ${lane.speedKmh} km/j"),
                until?.let { tr("until $it", "sampai $it") } ?: tr("reported by drivers", "dilapor pemandu"),
            ).joinToString(" · ")
        distance.text = Units.short(((if (on) s.endM else s.startM) - progress).coerceAtLeast(10.0))
        distance.visibility = View.VISIBLE
        Motion.reveal(banner, pivotFraction = 0f)
        if (smartAlerted.add(lane.id)) {
            host.announce(
                "smartlane-${lane.id}",
                tr(
                    "Smart lane open ahead. You may use the left emergency lane, up to ${lane.speedKmh}.",
                    "Smart lane dibuka di depan. Boleh guna lorong kecemasan kiri, had ${lane.speedKmh}.",
                ),
            )
        }
    }

    private fun askStillThere(report: RoadReports.Report) {
        voteId = report.id
        votingUntil = System.currentTimeMillis() + ASK_MS
        icon.setImageBitmap(MapIcons.reportBadge(activity, report.kind, 40))
        title.text = tr("${report.kind.label}: still there?", "${report.kind.label}: masih ada?")
        sub.text = tr("Your answer helps other drivers", "Jawapan anda bantu pemandu lain")
        distance.visibility = View.GONE
        voteRow.visibility = View.VISIBLE
        Motion.reveal(banner, pivotFraction = 0f)
        main.postDelayed({ if (votingUntil != 0L && System.currentTimeMillis() >= votingUntil) onProgress() }, ASK_MS + 50)
    }

    private fun answer(stillThere: Boolean) {
        val id = voteId
        if (id < 0) return
        voted += id
        votingUntil = 0
        hide()
        Toast.makeText(activity, tr("Thanks!", "Terima kasih!"), Toast.LENGTH_SHORT).show()
        io.execute {
            try {
                RoadReports.vote(id, stillThere)
            } catch (e: Exception) {
                Log.w(LOG, "vote: ${e.message}")
            }
        }
        if (!stillThere) {
            all = all.filterNot { it.id == id }
            onRoute = onRoute.filterNot { it.report.id == id }
            host.redrawPins()
        }
    }

    private fun hide() {
        voteRow.visibility = View.GONE
        Motion.dismiss(banner)
    }

    /** The Report button: five big tiles, one tap sends it at where you are. */
    fun showPicker() {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val grid =
            GridLayout(activity).apply {
                columnCount = 2
                setPadding(dp(16), dp(8), dp(16), dp(4))
            }
        var dialog: AlertDialog? = null
        for (kind in RoadReports.Kind.entries) {
            val tile =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = ContextCompat.getDrawable(activity, R.drawable.bg_report_tile)
                    isClickable = true
                    isFocusable = true
                    contentDescription = kind.label
                    addView(
                        ImageView(activity).apply { setImageBitmap(MapIcons.reportBadge(activity, kind, 56)) },
                        LinearLayout.LayoutParams(dp(56), dp(56)),
                    )
                    addView(
                        TextView(activity).apply {
                            text = kind.label
                            setTextAppearance(R.style.TextAppearance_Signal_Body)
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, 0)
                        },
                    )
                    setOnClickListener {
                        dialog?.dismiss()
                        send(kind)
                    }
                }
            // Smart lane is the fifth tile: it takes the whole last row.
            val wide = kind == RoadReports.Kind.SMART_LANE
            grid.addView(
                tile,
                GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, if (wide) 2 else 1, 1f)).apply {
                    width = 0
                    height = dp(if (wide) 104 else 124)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                },
            )
        }
        dialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(tr("What's on the road?", "Apa ada kat jalan?"))
                .setMessage(tr("Reported where you are now, for other drivers.", "Dilapor di tempat anda sekarang, untuk pemandu lain."))
                .setView(grid)
                .setNegativeButton(tr("Cancel", "Batal"), null)
                .show()
    }

    private fun send(kind: RoadReports.Kind) {
        val here = host.here()
        if (here == null) {
            Toast.makeText(activity, tr("Waiting for GPS", "Tunggu GPS"), Toast.LENGTH_SHORT).show()
            return
        }
        val heading = host.heading()
        io.execute {
            val id =
                try {
                    RoadReports.send(kind, here, heading)
                } catch (e: Exception) {
                    Log.w(LOG, "report: ${e.message}")
                    null
                }
            main.post {
                if (id == null) {
                    Toast.makeText(activity, tr("Couldn't send - no internet?", "Tak dapat hantar - tiada internet?"), Toast.LENGTH_SHORT).show()
                    return@post
                }
                mine += id
                Toast.makeText(activity, tr("Reported. Thanks!", "Dah lapor. Terima kasih!"), Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    companion object {
        /** A Smart Lane is announced this far before it starts. */
        private const val SMART_ALERT_M = 2_000.0

        private const val LOG = "NavOSReports"
        private const val POLL_MS = 120_000L
        const val ALERT_M = 800.0
        private const val PASSED_M = 30.0
        private const val ASK_UNTIL_M = 400.0
        private const val ASK_MS = 10_000L
    }
}

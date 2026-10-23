package com.excellentmindblow.navos

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.navigation.TripStrip

/**
 * Draws the route preview (Signal design): destination header, up to
 * [RouteChoices.SHOWN] route cards with "All N routes" for the rest, the
 * typical-time note, and the full list behind it. Which route is chosen,
 * and what Go / Add stop / Back do, stay with [MainActivity].
 */
class RoutePreviewSheet(
    root: View,
    private val onSelect: (index: Int) -> Unit,
    private val onShowAll: () -> Unit,
) {
    private val context = root.context
    private val destination: TextView = root.findViewById(R.id.routePreviewDestination)
    private val address: TextView = root.findViewById(R.id.routePreviewAddress)
    private val cards: LinearLayout = root.findViewById(R.id.routeChoices)
    private val allRoutes: TextView = root.findViewById(R.id.routeAllRoutes)
    private val hint: TextView = root.findViewById(R.id.routePreviewHint)
    private val allList: LinearLayout = root.findViewById(R.id.routeListItems)

    init {
        allRoutes.setOnClickListener { onShowAll() }
    }

    fun render(
        shown: List<RouteChoice>,
        totalRoutes: Int,
        destinationName: String?,
        destinationAddress: String?,
        hintText: String,
    ) {
        destination.text = destinationName.orEmpty()
        destination.visibility = if (destinationName.isNullOrBlank()) View.GONE else View.VISIBLE
        address.text = destinationAddress.orEmpty().trim()
        address.visibility = if (destinationAddress.isNullOrBlank()) View.GONE else View.VISIBLE

        cards.removeAllViews()
        shown.forEach { cards.addView(card(cards, it)) }
        allRoutes.text = context.getString(R.string.all_routes, totalRoutes)
        allRoutes.visibility = if (totalRoutes > shown.size) View.VISIBLE else View.GONE
        hint.text = hintText
    }

    /** Every route, quickest first (the "All routes" screen). */
    fun renderAll(choices: List<RouteChoice>) {
        allList.removeAllViews()
        choices.forEach { allList.addView(card(allList, it)) }
    }

    private fun card(
        parent: LinearLayout,
        choice: RouteChoice,
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.item_route_choice, parent, false)
        val density = context.resources.displayMetrics.density
        row.background =
            GradientDrawable().apply {
                cornerRadius = 18 * density
                if (choice.selected) {
                    setColor(color(R.color.sig_field))
                    setStroke((2 * density).toInt(), color(R.color.sig_ink))
                } else {
                    setColor(color(R.color.sig_sheet))
                    setStroke((1.5f * density).toInt(), color(R.color.sig_outline))
                }
            }
        val duration = NavFormat.formatRouteMinutes(choice.durationSeconds)
        val distance = NavFormat.formatTripDistance(choice.distanceMeters)
        row.findViewById<TextView>(R.id.choiceDuration).apply {
            text = duration
            textSize = if (choice.selected) 30f else 26f
        }
        row.findViewById<TextView>(R.id.choiceDistance).text = distance
        val via = row.findViewById<TextView>(R.id.choiceVia)
        via.text = choice.via?.let { tr("via $it", "ikut $it") }.orEmpty()
        via.visibility = if (choice.via == null) View.GONE else View.VISIBLE
        row.findViewById<View>(R.id.choiceFastest).visibility = if (choice.fastest) View.VISIBLE else View.GONE

        val tolls = row.findViewById<TextView>(R.id.choiceTolls)
        val tollText =
            when (choice.tolls) {
                null -> null
                0 -> context.getString(R.string.no_tolls)
                1 -> context.getString(R.string.one_toll)
                else -> context.getString(R.string.n_tolls, choice.tolls)
            }
        tolls.text = tollText
        tolls.visibility = if (tollText == null) View.GONE else View.VISIBLE
        val noTolls = choice.tolls == 0
        tolls.setTextColor(color(if (noTolls) R.color.sig_success else R.color.sig_text_quiet))
        tolls.paint.isFakeBoldText = noTolls

        val lights = row.findViewById<TextView>(R.id.choiceLights)
        val lightText =
            when (val n = choice.lights) {
                null -> null
                0 -> context.getString(R.string.no_lights)
                else -> context.resources.getQuantityString(R.plurals.n_lights, n, n)
            }
        lights.text = lightText
        lights.visibility = if (lightText == null) View.GONE else View.VISIBLE

        // Other routes say how they differ from the selected one.
        val diff = differenceText(choice)
        if (diff != null) {
            via.text = listOfNotNull(diff, choice.via?.let { tr("via $it", "ikut $it") }).joinToString(" · ")
            via.visibility = View.VISIBLE
        }

        val mini = row.findViewById<TripStripView>(R.id.choiceMiniStrip)
        val trip = row.findViewById<View>(R.id.choiceTrip)
        val strip = choice.strip
        if (choice.selected) {
            mini.visibility = View.GONE
            trip.visibility = View.VISIBLE
            bindTrip(row, choice)
        } else {
            trip.visibility = View.GONE
            mini.mini = true
            mini.strip = strip
            mini.visibility = if (strip == null) View.GONE else View.VISIBLE
        }

        row.contentDescription =
            context.getString(
                R.string.route_card_description,
                duration,
                distance,
                listOfNotNull(choice.via?.let { tr("via $it", "ikut $it") }, tollText, lightText).joinToString(", "),
            )
        row.isSelected = choice.selected
        row.setOnClickListener { onSelect(choice.index) }
        Motion.addPressFeedback(row)
        return row
    }

    /** "+5 min · No tolls" against the selected route, or null for the selected one. */
    private fun differenceText(choice: RouteChoice): String? {
        val delta = choice.deltaSeconds ?: return null
        val minutes = Math.round(delta / 60.0).toInt()
        val time =
            when {
                minutes > 0 -> "+$minutes min"
                minutes < 0 -> "\u2212${-minutes} min"
                else -> tr("Same time", "Masa sama")
            }
        val tolls = choice.tolls
        val selTolls = choice.selectedTolls
        val tollText =
            if (tolls == null || selTolls == null || tolls >= selTolls) {
                null
            } else if (tolls == 0) {
                tr("No tolls", "Tiada tol")
            } else {
                tr("${selTolls - tolls} fewer toll" + if (selTolls - tolls > 1) "s" else "", "${selTolls - tolls} tol kurang")
            }
        return listOfNotNull(time, tollText).joinToString(" · ")
    }

    // ---- The selected route's trip block -------------------------------------

    /** Which kind the chips light up (kept across re-renders of the same route). */
    private var highlight: Any? = null
    private var highlightRoute = -1

    private fun bindTrip(
        row: View,
        choice: RouteChoice,
    ) {
        if (highlightRoute != choice.index) {
            highlight = null
            highlightRoute = choice.index
        }
        val strip = choice.strip
        val stripView = row.findViewById<TripStripView>(R.id.choiceStrip)
        stripView.strip = strip
        stripView.highlight = highlight
        stripView.visibility = if (strip == null) View.GONE else View.VISIBLE

        val traffic = row.findViewById<TextView>(R.id.choiceTraffic)
        val note = choice.traffic
        if (note != null) {
            traffic.text = note.text
            traffic.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(if (note.jam) R.color.sig_limit else R.color.sig_caution), null, null, null)
            traffic.visibility = View.VISIBLE
        } else {
            traffic.visibility = View.GONE
        }

        val clock = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        val now = System.currentTimeMillis()
        row.findViewById<TextView>(R.id.choiceNow).text = timeLabel(tr("Now", "Sekarang"), clock.format(java.util.Date(now)))
        row.findViewById<TextView>(R.id.choiceArrive).text =
            timeLabel(tr("Arrive", "Sampai"), clock.format(java.util.Date(now + (choice.durationSeconds * 1000).toLong())))
        row.findViewById<View>(R.id.choiceNow).visibility = if (strip == null) View.GONE else View.VISIBLE
        row.findViewById<View>(R.id.choiceArrive).visibility = if (strip == null) View.GONE else View.VISIBLE

        val chips = row.findViewById<LinearLayout>(R.id.choiceChips)
        val list = row.findViewById<LinearLayout>(R.id.choiceList)
        chips.removeAllViews()
        if (strip == null) {
            list.visibility = View.GONE
            return
        }
        val speed = if (choice.durationSeconds > 0) choice.distanceMeters / choice.durationSeconds else 15.0
        fun refresh() {
            stripView.highlight = highlight
            for (i in 0 until chips.childCount) chips.getChildAt(i).isSelected = chips.getChildAt(i).tag == highlight
            bindList(list, strip, speed)
        }
        val entries =
            listOf(
                Triple(TripStrip.Kind.TOLL, strip.count(TripStrip.Kind.TOLL), R.drawable.ic_toll),
                Triple(TripStrip.Kind.CAMERA, strip.count(TripStrip.Kind.CAMERA), R.drawable.ic_alert_camera),
                Triple(TripStripView.LIGHTS, strip.lightsM.size, 0),
                Triple(TripStrip.Kind.PETROL, strip.count(TripStrip.Kind.PETROL), R.drawable.ic_fuel),
                Triple(TripStrip.Kind.RNR, strip.count(TripStrip.Kind.RNR), R.drawable.ic_rnr),
                Triple(TripStrip.Kind.EV, strip.count(TripStrip.Kind.EV), R.drawable.ic_ev),
            )
        for ((kind, n, icon) in entries) {
            if (n <= 0) continue
            val chip =
                TextView(context).apply {
                    tag = kind
                    text = chipText(kind, n)
                    setTextAppearance(R.style.TextAppearance_Signal_Label)
                    textSize = 14f
                    setTextColor(ContextCompat.getColorStateList(context, R.color.sig_chip_text))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_sig_chip)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val d = context.resources.displayMetrics.density
                    setPadding((12 * d).toInt(), 0, (14 * d).toInt(), 0)
                    minHeight = (40 * d).toInt()
                    if (icon != 0) {
                        val dr = ContextCompat.getDrawable(context, icon)?.mutate()
                        dr?.setTintList(ContextCompat.getColorStateList(context, R.color.sig_chip_text))
                        dr?.setBounds(0, 0, (16 * d).toInt(), (16 * d).toInt())
                        setCompoundDrawablesRelative(dr, null, null, null)
                        compoundDrawablePadding = (6 * d).toInt()
                    }
                    layoutParams =
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (40 * d).toInt()).apply {
                            marginEnd = (8 * d).toInt()
                        }
                    setOnClickListener {
                        highlight = if (highlight == kind) null else kind
                        refresh()
                    }
                    Motion.addPressFeedback(this)
                }
            chips.addView(chip)
        }
        refresh()
    }

    /** The places of the highlighted kind, in order: name, how far, how long. */
    private fun bindList(
        list: LinearLayout,
        strip: TripStrip.Strip,
        speedMs: Double,
    ) {
        list.removeAllViews()
        val kind = highlight as? TripStrip.Kind
        val marks = if (kind == null) emptyList() else strip.marks.filter { it.kind == kind }
        list.visibility = if (marks.isEmpty()) View.GONE else View.VISIBLE
        val d = context.resources.displayMetrics.density
        for (m in marks) {
            val item = LinearLayout(context)
            item.orientation = LinearLayout.HORIZONTAL
            item.gravity = android.view.Gravity.CENTER_VERTICAL
            item.minimumHeight = (48 * d).toInt()
            val name =
                TextView(context).apply {
                    text = m.name.ifBlank { kindName(m.kind) }
                    setTextAppearance(R.style.TextAppearance_Signal_Body)
                    textSize = 15f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            val minutes = Math.round(m.atM / speedMs / 60.0).toInt().coerceAtLeast(1)
            val where =
                TextView(context).apply {
                    text = "${NavFormat.formatTripDistance(m.atM)} \u00b7 $minutes min"
                    setTextAppearance(R.style.TextAppearance_Signal_Number_Row)
                    textSize = 15f
                    setPadding((12 * d).toInt(), 0, 0, 0)
                }
            item.addView(name)
            item.addView(where)
            list.addView(item)
        }
    }

    private fun chipText(
        kind: Any,
        n: Int,
    ): String =
        when (kind) {
            TripStrip.Kind.TOLL -> tr(if (n == 1) "1 toll" else "$n tolls", "$n tol")
            TripStrip.Kind.CAMERA -> tr(if (n == 1) "1 camera" else "$n cameras", "$n kamera")
            TripStrip.Kind.PETROL -> tr("$n petrol", "$n minyak")
            TripStrip.Kind.RNR -> "$n R&R"
            TripStrip.Kind.EV -> tr("$n EV", "$n cas EV")
            else -> tr(if (n == 1) "1 light" else "$n lights", "$n lampu")
        }

    private fun kindName(kind: TripStrip.Kind): String =
        when (kind) {
            TripStrip.Kind.TOLL -> tr("Toll plaza", "Plaza tol")
            TripStrip.Kind.CAMERA -> tr("Speed camera", "Kamera laju")
            TripStrip.Kind.PETROL -> tr("Petrol station", "Stesen minyak")
            TripStrip.Kind.RNR -> "R&R"
            TripStrip.Kind.EV -> tr("EV charging", "Cas EV")
        }

    /** "Now 5:22 PM" with the time in the number font. */
    private fun timeLabel(
        label: String,
        time: String,
    ): CharSequence =
        android.text.SpannableStringBuilder("$label ").apply {
            val start = length
            append(time)
            setSpan(android.text.style.ForegroundColorSpan(color(R.color.sig_text)), start, length, 0)
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, length, 0)
            setSpan(android.text.style.RelativeSizeSpan(1.15f), start, length, 0)
        }

    private fun dot(colorRes: Int): android.graphics.drawable.Drawable {
        val d = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(colorRes))
            setSize((10 * d).toInt(), (10 * d).toInt())
        }
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)
}

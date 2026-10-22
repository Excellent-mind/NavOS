package com.excellentmindblow.navos

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

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
        via.text = choice.via?.let { "via $it" }.orEmpty()
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

        row.contentDescription =
            context.getString(R.string.route_card_description, duration, distance, listOfNotNull(choice.via?.let { "via $it" }, tollText).joinToString(", "))
        row.isSelected = choice.selected
        row.setOnClickListener { onSelect(choice.index) }
        Motion.addPressFeedback(row)
        return row
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)
}

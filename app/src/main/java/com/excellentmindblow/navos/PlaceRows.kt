package com.excellentmindblow.navos

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.location.Location
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.excellentmindblow.navos.poi.FuelBrand
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService

/** Fills the shared place row (R.layout.item_search_result), Signal style. */
object PlaceRows {
    fun inflate(parent: ViewGroup): View = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)

    /**
     * @param query typed text: its match in the name is bold and the rest
     *   regular, like a search result. Null: the whole name is bold.
     * @param iconRes leading icon, or null for none (search results).
     * @param topMatch first result: grey background, darker distance, "Top match".
     * @param value shown on the right instead of the distance (a drive time).
     */
    fun bind(
        row: View,
        place: GeocodeResult,
        origin: Location?,
        query: String? = null,
        iconRes: Int? = null,
        topMatch: Boolean = false,
        value: String? = null,
    ) {
        val context = row.context
        val holder = row.findViewById<View>(R.id.resultIconHolder)
        val icon = row.findViewById<ImageView>(R.id.resultIcon)
        // A petrol station of a company we know gets its brand badge.
        val brand = if (place.category == context.getString(R.string.chip_petrol)) FuelBrand.of(place.name) else null
        when {
            brand != null -> {
                holder.visibility = View.VISIBLE
                icon.imageTintList = null
                icon.setImageBitmap(MapIcons.brandBadge(context, brand, 40))
                icon.layoutParams = icon.layoutParams.apply { width = holder.layoutParams.width; height = holder.layoutParams.height }
            }
            iconRes != null -> {
                holder.visibility = View.VISIBLE
                icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.sig_text_quiet))
                icon.setImageResource(iconRes)
                val glyph = (18 * context.resources.displayMetrics.density).toInt()
                icon.layoutParams = icon.layoutParams.apply { width = glyph; height = glyph }
            }
            else -> holder.visibility = View.GONE
        }

        val name = row.findViewById<TextView>(R.id.resultName)
        val title = place.name.ifBlank { place.label }
        // A name containing the typed text: that part bold, the rest regular.
        // Otherwise (no query, or a nearby station that doesn't match) all bold.
        val matches = !query.isNullOrBlank() && title.contains(query.trim(), ignoreCase = true)
        name.text = if (matches) highlight(title, query!!) else title
        name.typeface = font(row, R.font.signal_text, bold = !matches)

        val address = row.findViewById<TextView>(R.id.resultAddress)
        val detail = listOfNotNull(place.category, place.address.trim().takeIf { it.isNotEmpty() }).joinToString(" · ")
        address.text = detail
        address.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE

        val distance = row.findViewById<TextView>(R.id.resultDistance)
        val right =
            value ?: origin?.let {
                NavFormat.formatDistance(GeocodingService.distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude))
            }
        distance.text = right
        distance.visibility = if (right == null) View.GONE else View.VISIBLE
        distance.setTextColor(ContextCompat.getColor(context, if (topMatch) R.color.sig_text else R.color.sig_text_quiet))
        distance.typeface = font(row, R.font.signal_numbers, bold = topMatch)
        row.findViewById<View>(R.id.resultTopMatch).visibility = if (topMatch) View.VISIBLE else View.GONE
        if (topMatch) {
            row.setBackgroundResource(R.drawable.bg_sig_card)
        } else {
            row.setBackgroundResource(selectableBackground(row))
        }
    }

    /** [text] with the first case-insensitive match of [query] in bold. */
    fun highlight(
        text: String,
        query: String,
    ): CharSequence {
        val q = query.trim()
        val at = text.indexOf(q, ignoreCase = true)
        if (q.isEmpty() || at < 0) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** The family in its regular or bold weight (re-styling the current typeface can't un-bold it). */
    private fun font(
        row: View,
        family: Int,
        bold: Boolean,
    ): Typeface? {
        val base = ResourcesCompat.getFont(row.context, family) ?: return null
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun selectableBackground(row: View): Int {
        val attrs = row.context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val res = attrs.getResourceId(0, 0)
        attrs.recycle()
        return res
    }

    /** Search results list: top match first, the typed text in bold. */
    class Adapter(
        val results: List<GeocodeResult>,
        private val origin: Location?,
        private val query: String?,
    ) : BaseAdapter() {
        override fun getCount(): Int = results.size

        override fun getItem(position: Int): GeocodeResult = results[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val row = convertView ?: inflate(parent)
            bind(row, results[position], origin, query = query, topMatch = position == 0 && !query.isNullOrBlank())
            // Room inside the top match's rounded grey card.
            val edge = (12 * parent.resources.displayMetrics.density).toInt()
            row.setPadding(edge, row.paddingTop, edge, row.paddingBottom)
            return row
        }
    }
}

package com.excellentmindblow.navos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr

/** The bottom sheet every screen sits in: sheet colour, round top corners, a handle. */
@Composable
fun JalanSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Jalan.colors.sheet,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 5.dp).background(Jalan.colors.line, CircleShape))
            }
            content()
        }
    }
}

/** "Jalan": the go button, a JKR sign plate (green, white rim inset). */
@Composable
fun JalanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Jkr.Green, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(3.dp)
            .border(2.dp, Color.White, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = JalanType.title.copy(fontSize = 20.sp), color = Color.White)
    }
}

/** A road number on its chrome-yellow JKR shield (E33, FT2, 2). */
@Composable
fun RouteShield(
    ref: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(
        modifier
            .background(Jkr.ShieldInk, RoundedCornerShape(5.dp))
            .padding(1.5.dp)
            .background(Jkr.Shield, RoundedCornerShape(4.dp))
            .padding(horizontal = if (small) 5.dp else 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(ref, style = JalanType.label.copy(fontSize = if (small) 12.sp else 14.sp), color = Jkr.ShieldInk)
    }
}

/** Toll price plate (TOL RM 8.50) like the plaza signs; outline "No toll" when free. */
@Composable
fun TollPlate(
    text: String,
    free: Boolean,
    modifier: Modifier = Modifier,
) {
    if (free) {
        Box(modifier.border(1.2.dp, Jalan.colors.line, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(text, style = JalanType.label.copy(fontSize = 12.sp), color = Jalan.colors.inkQuiet)
        }
    } else {
        Box(
            modifier
                .background(Jkr.Green, RoundedCornerShape(6.dp))
                .padding(2.dp)
                .border(1.2.dp, Color.White, RoundedCornerShape(4.5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(text, style = JalanType.label.copy(fontSize = 12.sp), color = Color.White)
        }
    }
}

/** A round 44 dp chip (Home 18 min, Petrol). */
@Composable
fun JalanChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
) {
    val c = Jalan.colors
    Row(
        modifier
            .heightIn(min = 44.dp)
            .background(if (selected) c.ink else c.card, RoundedCornerShape(22.dp))
            .border(1.dp, if (selected) c.ink else c.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) c.onInk else c.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = JalanType.label, color = if (selected) c.onInk else c.ink, maxLines = 1)
    }
}

/** One place in a list: icon tile, name, address, distance. */
@Composable
fun PlaceRow(
    name: String,
    detail: String?,
    distance: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(c.field, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = JalanType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, style = JalanType.caption, color = c.inkQuiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (distance != null) {
            Spacer(Modifier.width(10.dp))
            Text(distance, style = JalanType.caption, color = c.inkQuiet)
        }
    }
}

/** Section label in capitals (RECENT). */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
) = Text(text.uppercase(), style = JalanType.overline, color = Jalan.colors.inkQuiet, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))

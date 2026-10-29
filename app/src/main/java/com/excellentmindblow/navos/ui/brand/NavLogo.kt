package com.excellentmindblow.navos.ui.brand

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.ui.theme.JalanType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.atan2

/**
 * The NavOS logo - a ring, an S-shaped road and the blue arrow - drawn from
 * paths, so it can animate: the ring closes, the road runs out of the ring
 * with the arrow at its tip, and the arrow lands where it belongs.
 * Same paths as res/drawable/ic_launcher_foreground.xml (a 1254 grid).
 */
object NavLogoPaths {
    const val RING_LEFT = "M417.0,948.6A406,406 0 1,1 914.5,317.5"
    const val RING_RIGHT = "M933.3,338.8A406,406 0 0,1 912.1,879.0"

    /** The road from where it leaves the ring (bottom right) to the arrow. */
    const val ROAD =
        "M897.9,893.0A406,406 0 0,1 474.5,976.0C432,958 415,930 415,875C415,830 450,794 505,794" +
            "L808,794A88,88 0 0,0 808,618L445,618A93,93 0 0,1 445,432L752,432"
    const val ARROW = "M893,431L712,358Q704,356 708,364L748,432L708,500Q704,508 712,505Z"

    /** Where the road meets the arrow's back notch. */
    val ARROW_ANCHOR = Offset(752f, 432f)
    val ARROW_CENTRE = Offset(805f, 432f)
    val CENTRE = Offset(620f, 597f)

    /** The square the logo fills (ring and its stroke). */
    const val EXTENT = 880f

    val BLUE = Color(0xFF0A82FE)
}

private class LogoGeometry {
    val ringLeft: Path = PathParser().parsePathString(NavLogoPaths.RING_LEFT).toPath()
    val ringRight: Path = PathParser().parsePathString(NavLogoPaths.RING_RIGHT).toPath()
    val road: Path = PathParser().parsePathString(NavLogoPaths.ROAD).toPath()
    val arrow: Path = PathParser().parsePathString(NavLogoPaths.ARROW).toPath()
    val ringLeftM = PathMeasure().apply { setPath(ringLeft, false) }
    val ringRightM = PathMeasure().apply { setPath(ringRight, false) }
    val roadM = PathMeasure().apply { setPath(road, false) }
    private val tmp = Path()

    fun part(
        m: PathMeasure,
        from: Float,
        to: Float,
    ): Path {
        tmp.reset()
        if (to > from) m.getSegment(m.length * from, m.length * to, tmp, true)
        return tmp
    }
}

/**
 * The logo, drawn [ring], [road] and [arrow] of the way (0..1). [roadStart]
 * erases the road from its start (for the loading loop). While the road is
 * still drawing, the arrow rides at its tip.
 */
@Composable
fun NavLogo(
    modifier: Modifier = Modifier,
    ink: Color = Color.White,
    ring: Float = 1f,
    road: Float = 1f,
    roadStart: Float = 0f,
    arrow: Float = 1f,
    arrowPop: Float = 1f,
) {
    val g = remember { LogoGeometry() }
    Canvas(modifier) {
        val s = size.minDimension / NavLogoPaths.EXTENT
        translate(size.width / 2f, size.height / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                translate(-NavLogoPaths.CENTRE.x, -NavLogoPaths.CENTRE.y) {
                    drawLogo(g, ink, ring, road, roadStart, arrow, arrowPop)
                }
            }
        }
    }
}

private fun DrawScope.drawLogo(
    g: LogoGeometry,
    ink: Color,
    ring: Float,
    road: Float,
    roadStart: Float,
    arrow: Float,
    arrowPop: Float,
) {
    val ringStroke = Stroke(width = 60f, cap = StrokeCap.Butt)
    if (ring > 0f) {
        drawPath(g.part(g.ringLeftM, 0f, ring), ink, style = ringStroke)
        drawPath(g.part(g.ringRightM, 0f, ring), ink, style = ringStroke)
    }
    if (road > roadStart) {
        drawPath(g.part(g.roadM, roadStart, road), ink, style = Stroke(width = 57f, join = StrokeJoin.Round, cap = if (road < 1f) StrokeCap.Round else StrokeCap.Butt))
    }
    if (arrow <= 0f) return
    // The arrow sits at the road's tip, pointing the way the road goes.
    val atEnd = road >= 1f
    val d = (g.roadM.length * road).coerceIn(0f, g.roadM.length)
    val pos = if (atEnd) NavLogoPaths.ARROW_ANCHOR else g.roadM.getPosition(d)
    val tan = if (atEnd) Offset(1f, 0f) else g.roadM.getTangent(d)
    val angle = Math.toDegrees(atan2(tan.y, tan.x).toDouble()).toFloat()
    val k = arrow * arrowPop
    translate(pos.x - NavLogoPaths.ARROW_ANCHOR.x, pos.y - NavLogoPaths.ARROW_ANCHOR.y) {
        rotate(angle, pivot = NavLogoPaths.ARROW_ANCHOR) {
            scale(k, k, pivot = NavLogoPaths.ARROW_ANCHOR) {
                drawPath(g.arrow, NavLogoPaths.BLUE, style = Fill)
                drawPath(g.arrow, Color.White, style = Stroke(width = 10f, join = StrokeJoin.Round))
            }
        }
    }
}

/** Animations are off on the phone (accessibility): show things done, not moving. */
@Composable
private fun animationsOff(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * The opening screen: on black, the ring closes, the road runs out of it with
 * the arrow riding its tip, the arrow lands with a small pop and the name
 * comes in. It stays until [ready] (the map is drawn), then fades away and
 * calls [onDone].
 */
@Composable
fun SplashOverlay(
    ready: Boolean,
    onDone: () -> Unit,
) {
    val ring = remember { Animatable(0f) }
    val road = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val name = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    var readyNow by remember { mutableStateOf(ready) }
    readyNow = ready
    val still = animationsOff()
    LaunchedEffect(Unit) {
        if (still) {
            ring.snapTo(1f); road.snapTo(1f); name.snapTo(1f)
        } else {
            coroutineScope {
                launch { ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
                launch {
                    delay(250)
                    road.animateTo(1f, tween(1000, easing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)))
                    pop.animateTo(1.18f, tween(110, easing = FastOutSlowInEasing))
                    pop.animateTo(1f, tween(240, easing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)))
                }
                launch {
                    delay(950)
                    name.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                }
            }
        }
        // Hold until the map is ready (at most a few seconds more), then go.
        kotlinx.coroutines.withTimeoutOrNull(3_000) { snapshotFlow { readyNow }.first { it } }
        out.animateTo(1f, tween(if (still) 1 else 380, easing = LinearEasing))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out.value }
            .background(Color.Black)
            .semantics { contentDescription = "NavOS" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            NavLogo(
                Modifier.size(156.dp).graphicsLayer {
                    val k = 1f + 0.08f * out.value
                    scaleX = k
                    scaleY = k
                },
                ring = ring.value,
                road = road.value,
                arrow = if (road.value > 0.02f) 1f else 0f,
                arrowPop = pop.value,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "NavOS",
                style = JalanType.title.copy(fontSize = 26.sp, letterSpacing = 0.5.sp),
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = name.value
                    translationY = (1f - name.value) * 12.dp.toPx()
                },
            )
        }
    }
}

/**
 * Loading: the road keeps running out of the ring with the arrow at its tip,
 * then clears from behind, over and over. For "Finding routes…" and the like.
 */
@Composable
fun NavLogoLoader(
    size: Dp,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val head = remember { Animatable(0f) }
    val tail = remember { Animatable(0f) }
    val still = animationsOff()
    LaunchedEffect(Unit) {
        if (still) {
            head.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            tail.snapTo(0f)
            head.snapTo(0f)
            head.animateTo(1f, tween(950, easing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)))
            delay(180)
            tail.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            delay(120)
        }
    }
    NavLogo(
        modifier.size(size),
        ink = ink,
        ring = 1f,
        road = head.value,
        roadStart = tail.value,
        arrow = if (tail.value < 0.98f) 1f else 0f,
    )
}

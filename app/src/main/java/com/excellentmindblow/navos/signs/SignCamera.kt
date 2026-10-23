package com.excellentmindblow.navos.signs

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * The map camera as a pinhole camera, so a sign can stand UP from the ground.
 *
 * MapLibre only projects points on the ground. The sign's feet come from
 * MapLibre itself (exact); this class lifts them to the board's height
 * with the same camera MapLibre draws with - tilted [tiltDeg] from straight
 * down, MapLibre's 36.87 degree field of view, centred on the camera target
 * at ([cx], [cy]) - so the board leans exactly as the tilted map does.
 *
 * Units: screen pixels. Ground positions are in "pixels at the camera
 * target"; [quad] measures metres from the sign's own width on screen, so it
 * never needs the zoom level.
 */
class SignCamera(
    private val cx: Double,
    private val cy: Double,
    viewHeight: Double,
    tiltDeg: Double,
) {
    /** Focal length: MapLibre's fov is 2 * atan(1/3), so f = 1.5 * height. */
    private val f = viewHeight / (2.0 * tan(FOV_RAD / 2.0))
    private val t = Math.toRadians(tiltDeg)
    private val camY = -f * sin(t)
    private val camZ = f * cos(t)
    private val fy = sin(t)
    private val fz = -cos(t)
    private val uy = cos(t)
    private val uz = sin(t)

    /** The ground point under screen pixel ([x], [y]), or null at/above the horizon. */
    fun groundOf(
        x: Double,
        y: Double,
    ): DoubleArray? {
        val a = (x - cx) / f
        val b = -(y - cy) / f
        val dx = a
        val dy = fy + b * uy
        val dz = fz + b * uz
        if (dz >= -1e-6) return null
        val s = -camZ / dz
        return doubleArrayOf(dx * s, camY + dy * s, 0.0)
    }

    /** Screen position of world point ([x], [y], [z]), or null when behind the camera. */
    fun project(
        x: Double,
        y: Double,
        z: Double,
    ): DoubleArray? {
        val vy = y - camY
        val vz = z - camZ
        val depth = vy * fy + vz * fz
        if (depth <= 1.0) return null
        return doubleArrayOf(cx + f * x / depth, cy - f * (vy * uy + vz * uz) / depth)
    }

    /**
     * Corners of an upright board whose feet are the screen points
     * ([lx], [ly]) and ([rx], [ry]) - [widthM] metres apart - running from
     * [bottomM] to [topM] metres above the ground. Returns 8 floats:
     * top-left, top-right, bottom-right, bottom-left. Null if it can't be seen.
     */
    fun quad(
        lx: Double,
        ly: Double,
        rx: Double,
        ry: Double,
        widthM: Double,
        bottomM: Double,
        topM: Double,
    ): FloatArray? {
        val gl = groundOf(lx, ly) ?: return null
        val gr = groundOf(rx, ry) ?: return null
        val unitsPerM = hypot(gr[0] - gl[0], gr[1] - gl[1]) / widthM
        if (unitsPerM <= 0.0) return null
        val tl = project(gl[0], gl[1], topM * unitsPerM) ?: return null
        val tr = project(gr[0], gr[1], topM * unitsPerM) ?: return null
        val br = project(gr[0], gr[1], bottomM * unitsPerM) ?: return null
        val bl = project(gl[0], gl[1], bottomM * unitsPerM) ?: return null
        return floatArrayOf(
            tl[0].toFloat(), tl[1].toFloat(), tr[0].toFloat(), tr[1].toFloat(),
            br[0].toFloat(), br[1].toFloat(), bl[0].toFloat(), bl[1].toFloat(),
        )
    }

    companion object {
        /** MapLibre native's vertical field of view (TransformState). */
        const val FOV_RAD = 0.6435011087932844
    }
}

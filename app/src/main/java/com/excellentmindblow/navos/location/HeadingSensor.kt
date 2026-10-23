package com.excellentmindblow.navos.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which way the phone points (true north, degrees), from the rotation-vector
 * sensor (accelerometer + gyro + magnetometer, fused by Android). Used for
 * the arrow while standing still, when GPS has no heading - like Google
 * Maps' beam. Smoothed, corrected for magnetic declination and for the
 * screen's rotation; NaN when the compass is unreliable or absent.
 */
class HeadingSensor(
    context: Context,
) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    // Smoothed as a unit vector so 359 -> 1 degree doesn't swing through 180.
    private var sx = Double.NaN
    private var sy = Double.NaN
    // Some phones never report this sensor's accuracy: trust it unless told otherwise.
    private var reliable = true
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        sensors.unregisterListener(this)
        running = false
        sx = Double.NaN
    }

    /** True-north heading at ([lat], [lon]), or NaN. */
    fun headingDegrees(
        lat: Double,
        lon: Double,
    ): Double {
        if (!reliable || sx.isNaN()) return Double.NaN
        val magnetic = Math.toDegrees(atan2(sx, sy))
        val declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        return ((magnetic + declination) % 360.0 + 360.0) % 360.0
    }

    @Suppress("DEPRECATION") // defaultDisplay: the screen rotation, on every API level
    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val (ax, ay) =
            when (window.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
        SensorManager.remapCoordinateSystem(rotation, ax, ay, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val az = orientation[0].toDouble()
        val nx = sin(az)
        val ny = cos(az)
        if (sx.isNaN()) {
            sx = nx
            sy = ny
        } else {
            sx += SMOOTHING * (nx - sx)
            sy += SMOOTHING * (ny - sy)
        }
    }

    override fun onAccuracyChanged(
        s: Sensor,
        accuracy: Int,
    ) {
        reliable = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    private companion object {
        const val SMOOTHING = 0.15
    }
}

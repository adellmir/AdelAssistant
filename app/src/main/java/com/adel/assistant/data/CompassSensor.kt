package com.adel.assistant.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2

/**
 * خواندن جهت قطب‌نما (درجه ۰–۳۶۰، ۰ = شمال).
 * از Rotation Vector در صورت وجود، وگرنه accelerometer + magnetometer.
 */
object CompassMath {
    /** اختلاف زاویه هدف نسبت به جهت فعلی: مثبت = بچرخ به راست */
    fun turnToTarget(headingDeg: Double, targetBearingDeg: Double): Double {
        var d = (targetBearingDeg - headingDeg) % 360.0
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    fun normalize(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }
}

@Composable
fun rememberCompassHeading(): State<Float> {
    val context = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasG = false
        var hasM = false
        val R = FloatArray(9)
        val I = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(R, event.values)
                        SensorManager.getOrientation(R, orientation)
                        // azimuth: radians, -π..π → 0..360
                        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (az < 0) az += 360f
                        heading.floatValue = az
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasG = true
                        if (hasG && hasM && SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                            SensorManager.getOrientation(R, orientation)
                            var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            if (az < 0) az += 360f
                            heading.floatValue = az
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasM = true
                        if (hasG && hasM && SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                            SensorManager.getOrientation(R, orientation)
                            var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            if (az < 0) az += 360f
                            heading.floatValue = az
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotation != null) {
            sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        } else {
            accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            mag?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

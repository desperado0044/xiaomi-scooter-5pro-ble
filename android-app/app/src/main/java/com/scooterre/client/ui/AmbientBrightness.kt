package com.scooterre.client.ui

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

// log10(lux) -> window brightness on the same perceptual scale as the brightness slider (0..1).
// Tuned against this phone's own auto-brightness (about 250 lx indoors -> about 0.25) so indoors it
// behaves like the system does, while direct daylight pushes the screen to full brightness.
private val CURVE = listOf(
    0.0 to 0.05f, 0.7 to 0.08f, 1.7 to 0.15f, 2.4 to 0.25f, 3.0 to 0.45f, 3.7 to 0.8f, 4.0 to 1.0f,
)

private fun brightnessForLogLux(x: Double): Float {
    if (x <= CURVE.first().first) return CURVE.first().second
    for (i in 1 until CURVE.size) {
        val (x1, y1) = CURVE[i]
        if (x <= x1) {
            val (x0, y0) = CURVE[i - 1]
            return (y0 + (y1 - y0) * ((x - x0) / (x1 - x0))).toFloat()
        }
    }
    return CURVE.last().second
}

/** While [enabled] and the app is in the foreground, drives this window's brightness from the
 * ambient light sensor and releases it again (back to the system setting) when the app leaves the
 * foreground or the option is turned off. Does nothing on devices without a light sensor.
 *
 * The light sensor only reports when the value changes, so the easing towards the target runs on
 * its own 100 ms clock instead of per sensor event - otherwise a stable reading would leave the
 * screen stuck part-way between the old and the new brightness. */
@Composable
fun AmbientBrightnessEffect(enabled: Boolean, forceMax: Boolean = false) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled, forceMax, lifecycle) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect

        fun apply(value: Float) {
            window.attributes = window.attributes.also { it.screenBrightness = value }
        }

        // The document viewer wants full brightness whatever else is configured.
        if (forceMax) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                apply(1f)
                try {
                    awaitCancellation()
                } finally {
                    apply(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }
            }
            return@LaunchedEffect
        }

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (!enabled || sensor == null) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var latestLogLux: Double? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    latestLogLux = log10(max(event.values[0], 1f).toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            try {
                var current: Float? = null
                while (true) {
                    delay(100)
                    val logLux = latestLogLux ?: continue
                    val target = brightnessForLogLux(logLux)
                    val next = current?.let { it + (target - it) * 0.12f } ?: target
                    if (current == null || abs(next - current) >= 0.004f) {
                        current = next
                        apply(next)
                    }
                }
            } finally {
                manager.unregisterListener(listener)
                apply(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }
    }
}

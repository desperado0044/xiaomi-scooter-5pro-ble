package com.scooterre.client.ui

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

// Brightness curve over log10(lux): ~1 lx (dark room) -> 10%, ~100 lx (living room) -> ~42%,
// ~1000 lx (overcast) -> ~70%, 10000+ lx (daylight/sun) -> 100%.
private const val MIN_BRIGHTNESS = 0.1f
private const val LOG_LUX_FULL = 4.0
private const val SMOOTHING = 0.2
private const val MIN_STEP = 0.03f

private fun brightnessForLogLux(logLux: Double): Float {
    val t = (logLux / LOG_LUX_FULL).coerceIn(0.0, 1.0)
    return (MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS) * t.pow(1.5)).toFloat()
}

/** While [enabled] and the app is in the foreground, overrides this window's brightness from the
 * ambient light sensor (smoothed so passing shadows don't flicker the screen); the override is
 * released again when the app leaves the foreground or the option is turned off, handing control
 * back to the system brightness setting. Does nothing on devices without a light sensor. */
@Composable
fun AmbientBrightnessEffect(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        val window = (context as? Activity)?.window
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (!enabled || window == null || sensor == null) return@DisposableEffect onDispose {}

        fun applyBrightness(value: Float) {
            window.attributes = window.attributes.also { it.screenBrightness = value }
        }

        var smoothedLogLux: Double? = null
        var applied = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val logLux = log10(event.values[0].toDouble().coerceAtLeast(1.0))
                val smoothed = smoothedLogLux?.let { it + SMOOTHING * (logLux - it) } ?: logLux
                smoothedLogLux = smoothed
                val target = brightnessForLogLux(smoothed)
                if (abs(target - applied) >= MIN_STEP) {
                    applied = target
                    applyBrightness(target)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Lifecycle.Event.ON_PAUSE -> {
                    manager.unregisterListener(listener)
                    applied = -1f
                    smoothedLogLux = null
                    applyBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
            applyBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }
}

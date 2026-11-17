package com.example.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.CompassState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CompassSensorManager(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun observeCompass(): Flow<CompassState> = callbackFlow {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            trySend(CompassState(azimuthDegrees = 0f, cardinalDirection = "N", isSensorAvailable = false))
            awaitClose { }
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var smoothedAzimuth = 0f
        var initialized = false

        fun emitAzimuth(rawDegrees: Float) {
            val normalized = ((rawDegrees % 360f) + 360f) % 360f
            smoothedAzimuth = if (!initialized) {
                initialized = true
                normalized
            } else {
                smoothCircularAngle(current = smoothedAzimuth, target = normalized, alpha = 0.18f)
            }
            trySend(
                CompassState(
                    azimuthDegrees = smoothedAzimuth,
                    cardinalDirection = CoordinateFormatter.toCardinalDirection(smoothedAzimuth),
                    isSensorAvailable = true,
                )
            )
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val azimuthRad = orientationAngles[0]
                        emitAzimuth(Math.toDegrees(azimuthRad.toDouble()).toFloat())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            emitAzimuth(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                        if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            emitAzimuth(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun smoothCircularAngle(current: Float, target: Float, alpha: Float): Float {
        val currentRad = Math.toRadians(current.toDouble())
        val targetRad = Math.toRadians(target.toDouble())
        val x = (1f - alpha) * cos(currentRad) + alpha * cos(targetRad)
        val y = (1f - alpha) * sin(currentRad) + alpha * sin(targetRad)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }
}

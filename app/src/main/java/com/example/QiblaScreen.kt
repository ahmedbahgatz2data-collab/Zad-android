package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import kotlin.math.*

@Composable
fun QiblaScreen(settings: AppSettings) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        if (event.values.size >= 3) {
                            val rotationMatrix = FloatArray(9)
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            val orientationAngles = FloatArray(3)
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            var currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                            if (currentAzimuth.isNaN()) currentAzimuth = 0f
                            if (currentAzimuth < 0) currentAzimuth += 360f
                            azimuth = currentAzimuth
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (rotationSensor != null) {
            sensorManager?.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        
        onDispose {
            sensorManager?.unregisterListener(sensorEventListener)
        }
    }

    // Calculate Qibla Bearing manually
    val userLat = Math.toRadians(settings.latitude)
    val userLon = Math.toRadians(settings.longitude)
    val meccaLat = Math.toRadians(21.422487)
    val meccaLon = Math.toRadians(39.826206)

    val dLon = meccaLon - userLon
    val y = sin(dLon) * cos(meccaLat)
    val x = cos(userLat) * sin(meccaLat) - sin(userLat) * cos(meccaLat) * cos(dLon)
    var qiblaBearing = Math.toDegrees(atan2(y, x)).toFloat()
    if (qiblaBearing.isNaN()) qiblaBearing = 0f
    if (qiblaBearing < 0) qiblaBearing += 360f

    // Calculate the difference so the compass points to Qibla
    var targetRotation = qiblaBearing - azimuth
    if (targetRotation.isNaN()) targetRotation = 0f
    if (targetRotation < 0) targetRotation += 360f

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 300),
        label = "compassRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "تحديد القبلة",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "بناءً على موقعك الجغرافي الفعلي\n${settings.locationName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder for a real compass vector graphic
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "مؤشر القبلة",
                modifier = Modifier
                    .size(200.dp)
                    .rotate(animatedRotation),
                tint = MaterialTheme.colorScheme.primary
            )
            // Center inner circle
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "يرجى معايرة البوصلة بتحريك الهاتف بشكل رقم 8 وتجنب المعادن",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

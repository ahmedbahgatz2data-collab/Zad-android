package com.example.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

object PrayerTimesCalculator {

    data class PrayerTimesList(
        val fajr: String,
        val shurouq: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String
    )

    fun calculate(
        latitude: Double,
        longitude: Double,
        calendar: Calendar = Calendar.getInstance()
    ): PrayerTimesList {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        // Calculate the standard timezone for this longitude to display localized times correctly
        // (e.g. Mecca longitude approx 39.8 -> GMT+3) instead of using the emulator's host timezone (e.g. UTC/PST cloud runner)
        val timezone = Math.round(longitude / 15.0).toDouble()

        // Mathematical conversion to angles
        val d = dayOfYear.toDouble()
        val latRad = Math.toRadians(latitude)

        // Simple Solar Declination and Equation of time
        // mean anomaly
        val g = 357.529 + 0.98560028 * d
        val gRad = Math.toRadians(g)
        
        // mean longitude
        val q = 280.459 + 0.98564736 * d
        
        // ecliptic longitude
        val L = q + 1.915 * sin(gRad) + 0.020 * sin(2.0 * gRad)
        val LRad = Math.toRadians(L)
        
        // obliquity of ecliptic
        val e = 23.439 - 0.00000036 * d
        val eRad = Math.toRadians(e)

        // right ascension
        var ra = Math.toDegrees(atan2(cos(eRad) * sin(LRad), cos(LRad)))
        ra = (ra + 360.0) % 360.0

        // declination
        val declination = asin(sin(eRad) * sin(LRad)) // in radians

        // equation of time in hours
        val qDeg = (q + 360.0) % 360.0
        var eqTime = (qDeg - ra) / 15.0 // in hours
        if (eqTime > 20.0) {
            eqTime -= 24.0
        } else if (eqTime < -20.0) {
            eqTime += 24.0
        }
        
        // Noon in hours local time
        // Noon = 12 + timezone - longitude/15 - eqTime
        val noon = 12.0 + timezone - (longitude / 15.0) - eqTime

        // Sunrise and Sunset (depth = -0.833 degrees)
        val riseSetAngle = Math.toRadians(-0.833)
        val shurouqHour = noon - calculateHourAngle(riseSetAngle, latRad, declination)
        val maghribHour = noon + calculateHourAngle(riseSetAngle, latRad, declination)

        // Fajr (angle = -18.0 degrees)
        val fajrAngle = Math.toRadians(-18.0)
        val fajrHour = noon - calculateHourAngle(fajrAngle, latRad, declination)

        // Asr (Standard shadow length coefficient = 1 for Shafi/Hanbali/Maliki, 2 for Hanafi)
        // shadow = 1 + tan(abs(latitude - declination))
        val latitudeDeg = latitude
        val declinationDeg = Math.toDegrees(declination)
        val diffAngleRad = Math.toRadians(abs(latitudeDeg - declinationDeg))
        val asrShadowAngle = atan(1.0 + tan(diffAngleRad))
        val asrAltitude = Math.PI / 2.0 - asrShadowAngle
        val asrHour = noon + calculateHourAngle(asrAltitude, latRad, declination)

        // Isha (angle = -18.0 degrees or standard)
        val ishaAngle = Math.toRadians(-18.0)
        val ishaHour = noon + calculateHourAngle(ishaAngle, latRad, declination)

        // Ensure calculations fall within logical boundaries or fall back gracefully
        return PrayerTimesList(
            fajr = formatTime(fajrHour),
            shurouq = formatTime(shurouqHour),
            dhuhr = formatTime(noon),
            asr = formatTime(asrHour),
            maghrib = formatTime(maghribHour),
            isha = formatTime(ishaHour)
        )
    }

    private fun calculateHourAngle(altitudeRad: Double, latitudeRad: Double, declinationRad: Double): Double {
        val num = sin(altitudeRad) - (sin(latitudeRad) * sin(declinationRad))
        val den = cos(latitudeRad) * cos(declinationRad)
        val valCos = num / den
        return if (valCos in -1.0..1.0) {
            Math.toDegrees(acos(valCos)) / 15.0
        } else {
            // Fallback for extreme latitudes
            6.0
        }
    }

    private fun formatTime(hourWithDecitals: Double): String {
        var normalized = (hourWithDecitals + 24.0) % 24.0
        if (normalized.isNaN()) normalized = 12.0
        
        val totalMinutes = (normalized * 60.0).roundToInt()
        val hours24 = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        
        val period = if (hours24 >= 12) "م" else "ص"
        val hours12 = if (hours24 % 12 == 0) 12 else hours24 % 12
        
        return String.format(Locale("ar"), "%02d:%02d %s", hours12, minutes, period)
    }
}

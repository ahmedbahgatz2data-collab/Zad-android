package com.example

import com.example.data.PrayerTimesCalculator
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPrayerTimes() {
    val cal = Calendar.getInstance()
    cal.set(2026, Calendar.MAY, 22)
    val times = PrayerTimesCalculator.calculate(21.4225, 39.8262, calendar = cal)
    println("--- MECCA PRAYER TIMES ---")
    println("Fajr: ${times.fajr}")
    println("Shurouq: ${times.shurouq}")
    println("Dhuhr: ${times.dhuhr}")
    println("Asr: ${times.asr}")
    println("Maghrib: ${times.maghrib}")
    println("Isha: ${times.isha}")
    println("--------------------------")
  }
}

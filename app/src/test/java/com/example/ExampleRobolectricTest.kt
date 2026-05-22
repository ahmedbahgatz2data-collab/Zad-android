package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("زاد — Zad", appName)
  }

  @Test
  fun `test WorshipViewModel creation`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    try {
      val vm = com.example.viewmodel.WorshipViewModel(app)
      org.junit.Assert.assertNotNull(vm)
    } catch (t: Throwable) {
      t.printStackTrace()
      throw t
    }
  }
}

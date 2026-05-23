package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class WorshipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("WorshipApplication", "Firebase initialization failed: ${e.message}")
        }
    }
}

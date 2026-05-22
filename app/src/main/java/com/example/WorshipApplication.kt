package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class WorshipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}

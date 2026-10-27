package com.rocioplata.familyfinaiapp

import android.app.Application

class FamilyFinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

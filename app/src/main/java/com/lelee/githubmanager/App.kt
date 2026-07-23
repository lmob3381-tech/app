package com.lelee.githubmanager

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}

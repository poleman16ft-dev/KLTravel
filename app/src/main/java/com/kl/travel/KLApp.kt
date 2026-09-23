package com.kl.travel

import android.app.Application
import com.kl.travel.work.AlertScheduler
import com.kl.travel.work.Notifier

class KLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        AlertScheduler.schedule(this)
    }
}

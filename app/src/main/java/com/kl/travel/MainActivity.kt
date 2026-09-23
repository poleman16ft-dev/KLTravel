package com.kl.travel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kl.travel.ui.KLRoot
import com.kl.travel.ui.KLTheme
import com.kl.travel.ui.SplashGate
import com.kl.travel.ui.TravelViewModel
import com.kl.travel.work.Notifier

class MainActivity : ComponentActivity() {
    private val vm: TravelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { KLTheme { SplashGate { KLRoot(vm) } } }
    }

    override fun onStart() {
        super.onStart()
        vm.syncOnOpen()          // refresh from the Google Sheet every time the app opens
    }

    override fun onStop() {
        super.onStop()
        vm.lockVault()           // documents need the screen lock again after the app has been in the background
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i?.getStringExtra(Notifier.EXTRA_ITEM)?.let { vm.open(it) }
    }
}

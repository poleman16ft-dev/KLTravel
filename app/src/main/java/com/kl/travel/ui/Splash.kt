package com.kl.travel.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kl.travel.BuildConfig
import com.kl.travel.R
import kotlinx.coroutines.delay

/** Safety net only: the splash normally ends when the video finishes (10 s), or when you tap anywhere to skip it. */
internal const val SPLASH_MAX_MS = 12000L
internal const val SPLASH_TAP_TAG = "splashTap"

/** The rotating KL crown video (res/raw/kl_splash.mp4) on black, played to the end, with the version number underneath. Shown once per launch. */
@Composable
fun SplashGate(content: @Composable () -> Unit) {
    var showing by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(SPLASH_MAX_MS); showing = false }
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = showing, exit = fadeOut()) {
          Box(Modifier.fillMaxSize()) {
            // The video sits above the version text (not behind it) because a video surface draws over anything stacked on it.
            Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoURI(Uri.parse("android.resource://${c.packageName}/${R.raw.kl_splash}"))
                            setOnPreparedListener { mp -> mp.setVolume(0f, 0f); mp.isLooping = false }
                            setOnCompletionListener { showing = false }
                            setOnErrorListener { _, _, _ -> showing = false; true }   // never block the app on a video problem
                            start()
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
                Text("KL Travel  v${BuildConfig.VERSION_NAME}  -  tap anywhere to skip", color = Color(0xFFB0B0B0), fontSize = 14.sp,
                    modifier = Modifier.navigationBarsPadding().padding(top = 8.dp, bottom = 24.dp))
            }
            // Last child = first to receive touches, so a tap anywhere (even over the video surface) skips the splash.
            Box(Modifier.fillMaxSize().testTag(SPLASH_TAP_TAG).pointerInput(Unit) { detectTapGestures { showing = false } })
          }
        }
    }
}

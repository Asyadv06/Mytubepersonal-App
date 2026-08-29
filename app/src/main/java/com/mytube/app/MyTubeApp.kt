package com.mytube.app

import android.app.Application
import android.webkit.WebView

class MyTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Lets Chrome DevTools (chrome://inspect) attach to the WebView
        // when running a debug build — harmless in release builds too.
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

package com.sifat.pulse

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/** Process-wide handle to the browser WebView so the foreground service can control the page. */
object WebHolder {
    @Volatile var web: WebView? = null
    @Volatile var serviceRunning = false
    private val main = Handler(Looper.getMainLooper())
    fun exec(js: String) { main.post { web?.evaluateJavascript(js, null) } }
    /** Keep the page's renderer + timers alive while the app is in the background. */
    fun keepAlive() {
        web?.let { webView ->
            webView.onResume()
            webView.resumeTimers()
            webView.evaluateJavascript("""
                document.querySelectorAll('video,audio').forEach(function(m){
                    if (typeof m.play === 'function' && m.paused) {
                        m.play().catch(function(){});
                    }
                });
            """.trimIndent(), null)
        }
    }
}

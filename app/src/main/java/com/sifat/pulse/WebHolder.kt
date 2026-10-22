package com.sifat.pulse

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/** Process-wide handle to the browser WebView so the foreground service can control the page. */
object WebHolder {
    @Volatile var web: WebView? = null
    @Volatile var serviceRunning = false
    @Volatile var keepAliveRequested = false
    private val main = Handler(Looper.getMainLooper())

    fun exec(js: String) {
        main.post {
            web?.let { webView ->
                webView.onResume()
                webView.resumeTimers()
                webView.evaluateJavascript(js, null)
            }
        }
    }

    /** Keep the page's renderer + timers alive while the app is in the background. */
    fun keepAlive() {
        main.post {
            web?.let { webView ->
                webView.onResume()
                webView.resumeTimers()
                if (serviceRunning || keepAliveRequested) {
                    webView.evaluateJavascript("""
                        (function(){
                            function getAll(root){
                                root = root || document;
                                var res = Array.from(root.querySelectorAll('video,audio'));
                                var els = root.querySelectorAll('*');
                                for(var i = 0; i < els.length; i++){
                                    if(els[i].shadowRoot) res = res.concat(getAll(els[i].shadowRoot));
                                }
                                return res;
                            }
                            getAll(document).forEach(function(m){
                                if (m && typeof m.play === 'function' && m.paused && !m.ended) {
                                    m.play().catch(function(){});
                                }
                            });
                            try {
                                var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                                if (p && typeof p.playVideo === 'function') {
                                    p.playVideo();
                                }
                            } catch(e){}
                        })();
                    """.trimIndent(), null)
                }
            }
        }
    }
}

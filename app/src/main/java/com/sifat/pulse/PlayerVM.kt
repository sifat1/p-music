package com.sifat.pulse

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val uri: Uri,
    val dateAdded: Long = 0L
) {
    val art: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    fun toItem(): MediaItem = MediaItem.Builder().setMediaId(id.toString()).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(art).build()).build()
}
data class Found(val url: String, val label: String)

class PlayerVM(app: Application) : AndroidViewModel(app) {
    val settings = SettingsManager(app)

    var controller by mutableStateOf<MediaController?>(null)
    var item by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var pos by mutableLongStateOf(0); var dur by mutableLongStateOf(0)
    var tracks by mutableStateOf(emptyList<Track>())
    var sortOption by mutableStateOf(settings.sortOption)

    // browser state
    val found = mutableStateListOf<Found>()
    var blobOnly by mutableStateOf(false)
    var webPlaying by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var currentUrl by mutableStateOf(settings.lastBrowserUrl)
    private val main = Handler(Looper.getMainLooper())
    private val mediaRx = Regex("""\.(mp3|m4a|aac|ogg|opus|wav|flac|mp4|webm|m3u8|mpd)(\?|#|$)""", RegexOption.IGNORE_CASE)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync(player); if (player.isPlaying) pauseWeb() }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({ controller = f.get().also { it.addListener(listener); sync(it) } }, ContextCompat.getMainExecutor(app))
        refresh()
    }

    fun updateSortOption(option: SortOption) {
        sortOption = option
        settings.sortOption = option
    }

    private fun sync(p: Player) {
        item = p.currentMediaItem; playing = p.isPlaying; shuffle = p.shuffleModeEnabled; repeat = p.repeatMode; tick()
    }
    fun tick() { controller?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0) } }

    fun refresh() {
        tracks = try {
            val c = getApplication<Application>().contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED
                ),
                "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>30000", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
            )
            buildList {
                c?.use {
                    while (it.moveToNext()) {
                        add(
                            Track(
                                id = it.getLong(0),
                                title = it.getString(1) ?: "Unknown",
                                artist = it.getString(2) ?: "Unknown artist",
                                albumId = it.getLong(3),
                                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.getLong(0)),
                                dateAdded = it.getLong(4)
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) { emptyList() }
    }

    fun play(list: List<Track>, index: Int, shuffled: Boolean = false) {
        controller?.run { setMediaItems(list.map { it.toItem() }, index, 0); shuffleModeEnabled = shuffled; prepare(); play() }
    }

    // ---- Browser: capture direct media URLs, hand them to the background service ----
    private fun add(u: String) = main.post {
        if (u.startsWith("http") && mediaRx.containsMatchIn(Uri.parse(u).path ?: "") && found.none { it.url == u })
            found.add(Found(u, Uri.parse(u).lastPathSegment ?: u))
    }
    fun scan() = web.evaluateJavascript("""(function(){
        var seen = new Set();
        function report(src){
            if (!src) return;
            var s = String(src).trim();
            if (!s || s.indexOf('blob:') === 0) return;
            if (s.indexOf('http') === 0 && !seen.has(s)) {
                seen.add(s);
                try { Pulse.media(s); } catch (e) {}
            }
        }
        function scanRoot(root) {
            root = root || document;
            root.querySelectorAll('video,audio,source,track').forEach(function(m){
                if (m && m.currentSrc) report(m.currentSrc);
                if (m && m.src) report(m.src);
            });
            root.querySelectorAll('video,audio').forEach(function(m){
                var s = m && (m.currentSrc || m.src || '');
                if (s && s.indexOf('blob:') === 0) { try { Pulse.blob(); } catch (e) {} }
            });
            root.querySelectorAll('*').forEach(function(el){
                if (el.shadowRoot) scanRoot(el.shadowRoot);
            });
        }
        scanRoot(document);
    })();""", null)

    private fun onWebState(playing: Boolean, title: String) {
        webPlaying = playing
        WebHolder.keepAliveRequested = playing
        val app = getApplication<Application>()
        try {
            if (playing) {
                controller?.pause()
                app.startForegroundService(Intent(app, WebAudioService::class.java).putExtra("playing", true).putExtra("title", title))
            } else if (WebHolder.serviceRunning) {
                app.startService(Intent(app, WebAudioService::class.java).putExtra("playing", false).putExtra("title", title))
            }
        } catch (e: Exception) { /* background-start restrictions: ignore */ }
    }
    private fun pauseWeb() { if (webPlaying) WebHolder.exec("(function(){ function getAll(root){ root = root || document; var res = Array.from(root.querySelectorAll('video,audio')); var els = root.querySelectorAll('*'); for(var i=0; i<els.length; i++){ if(els[i].shadowRoot) res = res.concat(getAll(els[i].shadowRoot)); } return res; } getAll(document).forEach(function(m){ if(m && typeof m.pause==='function') m.pause(); }); })();") }

    val web: WebView by lazy {
        WebView(getApplication<Application>()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            addJavascriptInterface(object {
                @JavascriptInterface fun media(u: String) = add(u)
                @JavascriptInterface fun blob() { main.post { blobOnly = true } }
                @JavascriptInterface fun state(p: Boolean, t: String) { main.post { onWebState(p, t) } }
            }, "Pulse")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                WebViewCompat.addDocumentStartJavaScript(this, BG_JS, setOf("*"))
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                    found.clear(); blobOnly = false; currentUrl = u
                    this@PlayerVM.settings.lastBrowserUrl = u
                    v.evaluateJavascript(BG_JS, null)
                    v.postDelayed({ scan() }, 400)
                }
                override fun onPageFinished(v: WebView, u: String) {
                    canBack = v.canGoBack();
                    this@PlayerVM.settings.lastBrowserUrl = u
                    v.evaluateJavascript(BG_JS, null)
                    scan();
                    v.postDelayed({ scan() }, 1000)
                    v.postDelayed({ scan() }, 2500)
                }
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                    add(r.url.toString())
                    return null
                }
            }
            loadUrl(this@PlayerVM.settings.lastBrowserUrl)
        }.also { WebHolder.web = it }
    }

    fun playUrl(f: Found) {
        val title = web.title?.takeIf { it.isNotBlank() } ?: f.label
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause()})", null)
        controller?.run {
            setMediaItem(MediaItem.Builder().setUri(f.url).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(Uri.parse(f.url).host).build()).build())
            prepare(); play()
        }
    }
    override fun onCleared() { controller?.release() }
}

/** Runs at document start: pages think they are always visible (so they don't auto-pause when minimized) and report play state. */
private const val BG_JS = """(function(){
try {
    Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
    Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
    document.hasFocus = function(){ return true; };
} catch(e) {}

var stopEvt = function(e){
    if (e && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
        e.stopImmediatePropagation();
    }
};
['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur', 'freeze'].forEach(function(ev){
    document.addEventListener(ev, stopEvt, true);
    window.addEventListener(ev, stopEvt, true);
});

function getAllMedia(root) {
    root = root || document;
    var media = Array.from(root.querySelectorAll('video,audio'));
    var all = root.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
        if (all[i].shadowRoot) {
            media = media.concat(getAllMedia(all[i].shadowRoot));
        }
    }
    return media;
}

var attached = new WeakSet();
var lastState = null;
var lastTitle = "";

function rep(){
    var list = getAllMedia(document);
    var playing = false;
    list.forEach(function(m){
        if (!attached.has(m)) {
            attached.add(m);
            ['play', 'pause', 'ended', 'timeupdate', 'playing', 'waiting', 'stalled', 'durationchange', 'loadedmetadata'].forEach(function(ev){
                m.addEventListener(ev, function(){ setTimeout(rep, 100); }, true);
            });
        }
        if (!m.paused && !m.ended) {
            playing = true;
        }
    });

    try {
        var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
        if (p && typeof p.getPlayerState === 'function') {
            var st = p.getPlayerState();
            if (st === 1 || st === 3) {
                playing = true;
            }
        }
    } catch(e){}

    var title = document.title || "Browser audio";
    if (playing !== lastState || title !== lastTitle) {
        lastState = playing;
        lastTitle = title;
        try { Pulse.state(playing, title); } catch(e){}
    }
}

setInterval(rep, 1000);
rep();

var observer = new MutationObserver(function(){ rep(); });
if (document.body) {
    observer.observe(document.body, { childList: true, subtree: true });
} else {
    document.addEventListener('DOMContentLoaded', function(){
        if (document.body) observer.observe(document.body, { childList: true, subtree: true });
    });
}
})();"""

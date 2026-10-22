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
    var controller by mutableStateOf<MediaController?>(null)
    var item by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var pos by mutableLongStateOf(0); var dur by mutableLongStateOf(0)
    var tracks by mutableStateOf(emptyList<Track>())

    // browser state
    val found = mutableStateListOf<Found>()
    var blobOnly by mutableStateOf(false)
    var webPlaying by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var currentUrl by mutableStateOf("")
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
    fun scan() = web.evaluateJavascript("""document.querySelectorAll('video,audio').forEach(function(m){
        var s=m.currentSrc||m.src||''; if(s.indexOf('blob:')==0)Pulse.blob(); else if(s)Pulse.media(s);});""", null)

    private fun onWebState(playing: Boolean, title: String) {
        webPlaying = playing
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
    private fun pauseWeb() { if (webPlaying) WebHolder.exec("document.querySelectorAll('video,audio').forEach(function(m){m.pause()})") }

    val web: WebView by lazy {
        WebView(getApplication<Application>()).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(object {
                @JavascriptInterface fun media(u: String) = add(u)
                @JavascriptInterface fun blob() { main.post { blobOnly = true } }
                @JavascriptInterface fun state(p: Boolean, t: String) { main.post { onWebState(p, t) } }
            }, "Pulse")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                WebViewCompat.addDocumentStartJavaScript(this, BG_JS, setOf("*"))
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { found.clear(); blobOnly = false; currentUrl = u; v.evaluateJavascript(BG_JS, null) }
                override fun onPageFinished(v: WebView, u: String) { canBack = v.canGoBack(); scan(); v.postDelayed({ scan() }, 2500) }
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? { add(r.url.toString()); return null }
            }
            loadUrl("https://duckduckgo.com")
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
if(window.__pulse)return;window.__pulse=1;
try{
 Object.defineProperty(document,'hidden',{get:function(){return false},configurable:true});
 Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'},configurable:true});
 Object.defineProperty(document,'webkitHidden',{get:function(){return false},configurable:true});
 Object.defineProperty(document,'webkitVisibilityState',{get:function(){return 'visible'},configurable:true});
}catch(e){}
var stop=function(e){e.stopImmediatePropagation();};
document.addEventListener('visibilitychange',stop,true);
window.addEventListener('visibilitychange',stop,true);
window.addEventListener('pagehide',stop,true);
window.addEventListener('blur',stop,true);
function rep(){var p=false;document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused&&!m.ended)p=true;});
 try{Pulse.state(p,document.title);}catch(e){}}
['play','pause','ended'].forEach(function(ev){document.addEventListener(ev,function(){setTimeout(rep,150)},true);});
})();"""

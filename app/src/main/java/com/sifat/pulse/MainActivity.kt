@file:OptIn(ExperimentalMaterial3Api::class)
package com.sifat.pulse

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onPause() { super.onPause(); WebHolder.keepAlive() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current)
            else darkColorScheme(primary = Color(0xFFB69DFF), background = Color(0xFF0E0B14), surface = Color(0xFF16121F))
            MaterialTheme(colorScheme = scheme) { App() }
        }
    }
}

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
val grad = Brush.linearGradient(listOf(Color(0xFF6D4AFF), Color(0xFF2BD9C5)))

@Composable fun App(vm: PlayerVM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    LaunchedEffect(Unit) { ask.launch(perms) }
    LaunchedEffect(vm.playing) { while (vm.playing) { vm.tick(); delay(500) } }
    BackHandler(full) { full = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(bottomBar = {
            Column {
                if (vm.item != null) MiniPlayer(vm) { full = true }
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Language, null) }, label = { Text("Browser") })
                }
            }
        }) { pad -> Box(Modifier.padding(pad)) { if (tab == 0) Library(vm) else Browser(vm) } }
        AnimatedVisibility(full, enter = slideInVertically { it }, exit = slideOutVertically { it }) { NowPlaying(vm) { full = false } }
    }
}

@Composable fun Art(uri: Uri?, size: Dp, r: Dp = 12.dp, mod: Modifier = Modifier) {
    Box(mod.size(size).clip(RoundedCornerShape(r)).background(grad), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.8f))
        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable fun Library(vm: PlayerVM) {
    var q by remember { mutableStateOf("") }
    val list = remember(vm.tracks, q) { vm.tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) } }
    Column {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), singleLine = true, shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search songs or artists") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (list.isNotEmpty()) vm.play(list, Random.nextInt(list.size), true) }) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Shuffle") }
            FilledTonalButton({ if (list.isNotEmpty()) vm.play(list, 0) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play all") }
        }
        if (list.isEmpty()) Text("No songs found. Grant audio access, or add music to the device.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                val cur = t.id.toString() == vm.item?.mediaId
                ListItem({ Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (cur) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    Modifier.clickable { vm.play(list, i) }, supportingContent = { Text(t.artist, maxLines = 1) },
                    leadingContent = { Art(t.art, 52.dp) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}

@Composable fun MiniPlayer(vm: PlayerVM, open: () -> Unit) {
    val m = vm.item?.mediaMetadata
    Surface(Modifier.padding(8.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = open), tonalElevation = 6.dp) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(m?.artworkUri, 44.dp, 8.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(m?.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(m?.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ if (vm.playing) vm.controller?.pause() else vm.controller?.play() }) { Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
            }
            LinearProgressIndicator({ if (vm.dur > 0) vm.pos / vm.dur.toFloat() else 0f }, Modifier.fillMaxWidth())
        }
    }
}

@Composable fun NowPlaying(vm: PlayerVM, close: () -> Unit) {
    val m = vm.item?.mediaMetadata; val c = vm.controller
    var seek by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B2A7A), MaterialTheme.colorScheme.background)))
        .statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconButton(close) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(32.dp)) } }
        Spacer(Modifier.weight(1f))
        Art(m?.artworkUri, 300.dp, 24.dp, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(32.dp))
        Text(m?.title?.toString() ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(m?.artist?.toString() ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Slider(seek ?: vm.pos.toFloat(), { seek = it }, onValueChangeFinished = { seek?.let { c?.seekTo(it.toLong()); vm.pos = it.toLong() }; seek = null },
            valueRange = 0f..maxOf(vm.dur, 1).toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt((seek ?: vm.pos.toFloat()).toLong())); Text(fmt(vm.dur)) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }) { Icon(Icons.Default.Shuffle, null, tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else LocalContentColorFaded()) }
            IconButton({ c?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(40.dp)) }
            FilledIconButton({ if (vm.playing) c?.pause() else c?.play() }, Modifier.size(72.dp)) { Icon(if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp)) }
            IconButton({ c?.seekToNext() }) { Icon(Icons.Default.SkipNext, null, Modifier.size(40.dp)) }
            IconButton({ c?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }) {
                Icon(if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (vm.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColorFaded())
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
@Composable fun LocalContentColorFaded() = MaterialTheme.colorScheme.onSurface.copy(.5f)

fun normalize(s: String): String {
    val t = s.trim()
    return if (t.startsWith("http")) t else if (!t.contains(' ') && t.contains('.')) "https://$t" else "https://duckduckgo.com/?q=${Uri.encode(t)}"
}

@Composable fun Browser(vm: PlayerVM) {
    var url by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(vm.currentUrl) { url = vm.currentUrl }
    BackHandler(vm.canBack) { vm.web.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(28.dp), placeholder = { Text("Search or enter URL") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { vm.web.loadUrl(normalize(url)) }))
            IconButton({ vm.web.reload() }) { Icon(Icons.Default.Refresh, null) }
        }
        Box(Modifier.weight(1f)) {
            AndroidView({ vm.web }, Modifier.fillMaxSize())
            if (vm.found.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { vm.scan(); sheet = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Default.Headphones, null) },
                text = { Text("Play as audio (${vm.found.size})") }
            )
            else if (vm.blobOnly) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
                Text("Streaming page: it plays in its own player. Press play, then minimize; audio keeps going.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (sheet) ModalBottomSheet({ sheet = false }) {
        Text("Detected media", Modifier.padding(16.dp, 0.dp), style = MaterialTheme.typography.titleMedium)
        vm.found.toList().forEach { f ->
            ListItem({ Text(f.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, Modifier.clickable { vm.playUrl(f); sheet = false },
                supportingContent = { Text(Uri.parse(f.url).host ?: "", maxLines = 1) }, leadingContent = { Icon(Icons.Default.Headphones, null) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
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
    override fun onStop() { super.onStop(); WebHolder.keepAlive() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkScheme = darkColorScheme(
                primary = Color(0xFFC3B0FF),
                onPrimary = Color(0xFF321371),
                primaryContainer = Color(0xFF492F8A),
                onPrimaryContainer = Color(0xFFE7DEFF),
                secondary = Color(0xFF53D2C5),
                onSecondary = Color(0xFF003733),
                secondaryContainer = Color(0xFF00504B),
                onSecondaryContainer = Color(0xFF73F5E7),
                background = Color(0xFF13101A),
                onBackground = Color(0xFFE8E5EE),
                surface = Color(0xFF1C1826),
                onSurface = Color(0xFFE8E5EE),
                surfaceVariant = Color(0xFF2A2438),
                onSurfaceVariant = Color(0xFFCDC6D7),
                surfaceContainerHigh = Color(0xFF2C263A)
            )
            MaterialTheme(colorScheme = darkScheme) { App() }
        }
    }
}

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
val grad = Brush.linearGradient(listOf(Color(0xFF6D4AFF), Color(0xFF2BD9C5)))

enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Date Added (Newest)"),
    DATE_ASC("Date Added (Oldest)")
}

data class Bookmark(val name: String, val url: String, val badgeColor: Color)

val defaultBookmarks = listOf(
    Bookmark("YouTube", "https://m.youtube.com", Color(0xFFFF0000)),
    Bookmark("Dailymotion", "https://www.dailymotion.com", Color(0xFF0066DC)),
    Bookmark("Vimeo", "https://vimeo.com", Color(0xFF1AB7EA)),
    Bookmark("SoundCloud", "https://m.soundcloud.com", Color(0xFFFF5500)),
    Bookmark("Twitch", "https://m.twitch.tv", Color(0xFF9146FF)),
    Bookmark("Bilibili", "https://m.bilibili.com", Color(0xFF00A1D6))
)

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
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val list = remember(vm.tracks, q, sortOption) {
        vm.tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
            .let { filtered ->
                when (sortOption) {
                    SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
                    SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
                    SortOption.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
                    SortOption.DATE_ASC -> filtered.sortedBy { it.dateAdded }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search songs or artists", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (list.isNotEmpty()) vm.play(list, Random.nextInt(list.size), true) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = { if (list.isNotEmpty()) vm.play(list, 0) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play All", style = MaterialTheme.typography.labelLarge)
                }
            }

            Box {
                FilterChip(
                    selected = true,
                    onClick = { sortMenuExpanded = true },
                    label = { Text(sortOption.label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                sortOption = option
                                sortMenuExpanded = false
                            },
                            leadingIcon = if (option == sortOption) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${list.size} ${if (list.size == 1) "song" else "songs"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No songs found. Grant audio access or add music to device.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    val isCurrent = t.id.toString() == vm.item?.mediaId
                    Surface(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.play(list, i) },
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    t.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    t.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Art(t.art, 50.dp) },
                            trailingContent = if (isCurrent && vm.playing) {
                                { Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable fun MiniPlayer(vm: PlayerVM, open: () -> Unit) {
    val m = vm.item?.mediaMetadata
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = open),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(m?.artworkUri, 46.dp, 10.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        m?.title?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        m?.artist?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { if (vm.playing) vm.controller?.pause() else vm.controller?.play() },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (vm.playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (vm.dur > 0) (vm.pos / vm.dur.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable fun NowPlaying(vm: PlayerVM, close: () -> Unit) {
    val m = vm.item?.mediaMetadata
    val c = vm.controller
    var seek by remember { mutableStateOf<Float?>(null) }
    val progress = seek ?: vm.pos.toFloat()

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF38236B), Color(0xFF141218))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = close) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.weight(0.5f))
        Art(
            m?.artworkUri,
            280.dp,
            24.dp,
            Modifier.size(280.dp).aspectRatio(1f)
        )
        Spacer(Modifier.height(32.dp))
        Text(
            m?.title?.toString() ?: "Unknown Title",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            m?.artist?.toString() ?: "Unknown Artist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(24.dp))
        Slider(
            value = progress.coerceIn(0f, maxOf(vm.dur, 1).toFloat()),
            onValueChange = { seek = it },
            onValueChangeFinished = {
                seek?.let {
                    c?.seekTo(it.toLong())
                    vm.pos = it.toLong()
                }
                seek = null
            },
            valueRange = 0f..maxOf(vm.dur, 1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                fmt(progress.toLong()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                fmt(vm.dur),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = { c?.seekToPrevious() }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            FilledIconButton(
                onClick = { if (vm.playing) c?.pause() else c?.play() },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (vm.playing) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { c?.seekToNext() }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                c?.let {
                    it.repeatMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }) {
                Icon(
                    if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (vm.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

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
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val homeUrl = "https://duckduckgo.com"
                url = homeUrl
                vm.web.loadUrl(homeUrl)
            }) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search or enter URL", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.web.loadUrl(normalize(url)) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { vm.web.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(defaultBookmarks) { bookmark ->
                FilterChip(
                    selected = false,
                    onClick = {
                        url = bookmark.url
                        vm.web.loadUrl(bookmark.url)
                    },
                    label = {
                        Text(
                            bookmark.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier.size(10.dp)
                                .clip(CircleShape)
                                .background(bookmark.badgeColor)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = Color.Transparent
                    )
                )
            }
        }

        Box(Modifier.weight(1f)) {
            AndroidView({ vm.web }, Modifier.fillMaxSize())
            if (vm.found.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { vm.scan(); sheet = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
                text = { Text("Play as audio (${vm.found.size})", fontWeight = FontWeight.Bold) }
            )
            else if (vm.blobOnly) Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    "Streaming page: it plays in its own player. Press play, then minimize; audio keeps going.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        Text(
            "Detected media",
            modifier = Modifier.padding(16.dp, 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        vm.found.toList().forEach { f ->
            ListItem(
                headlineContent = {
                    Text(
                        f.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier.clickable { vm.playUrl(f); sheet = false },
                supportingContent = {
                    Text(
                        Uri.parse(f.url).host ?: "",
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

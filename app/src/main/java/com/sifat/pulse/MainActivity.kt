@file:OptIn(ExperimentalMaterial3Api::class)
package com.sifat.pulse

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.random.Random

fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0x3B2C2248),
    borderColor: Color = Color.White.copy(alpha = 0.18f)
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                backgroundColor.copy(alpha = 0.55f),
                backgroundColor.copy(alpha = 0.25f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(borderColor, borderColor.copy(alpha = 0.04f))
        ),
        shape = shape
    )

@Composable fun GlassActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    highlight: Boolean = false
) {
    Box(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = if (highlight) Color(0x55492F8A) else Color(0x332E224A),
                borderColor = if (highlight) Color(0xFFC3B0FF).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.18f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (highlight) Color(0xFFC3B0FF) else Color.White
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

val glassAppBackground = Brush.verticalGradient(
    listOf(
        Color(0xFF1B112E),
        Color(0xFF100B1D),
        Color(0xFF0A0713)
    )
)

val RespectedTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        color = Color(0xFFF4EFFA)
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.25).sp,
        color = Color(0xFFF4EFFA)
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = Color(0xFFF4EFFA)
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = Color(0xFFF4EFFA)
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = Color(0xFFF4EFFA)
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = Color(0xFFE8E2F0)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color(0xFFD2CBDE)
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color(0xFFC0B8CE)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = Color(0xFFF4EFFA)
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Color(0xFFF4EFFA)
    )
)

class MainActivity : ComponentActivity() {
    override fun onPause() { super.onPause(); WebHolder.keepAlive() }
    override fun onStop() { super.onStop(); WebHolder.keepAlive() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val glassColorScheme = darkColorScheme(
                primary = Color(0xFFC3B0FF),
                onPrimary = Color(0xFF2C1368),
                primaryContainer = Color(0xFF492F8A),
                onPrimaryContainer = Color(0xFFE7DEFF),
                secondary = Color(0xFF53D2C5),
                onSecondary = Color(0xFF003733),
                secondaryContainer = Color(0xFF00504B),
                onSecondaryContainer = Color(0xFF73F5E7),
                background = Color(0xFF100B1D),
                onBackground = Color(0xFFF4EFFA),
                surface = Color(0xFF1B112E),
                onSurface = Color(0xFFF4EFFA),
                surfaceVariant = Color(0x3B2C2248),
                onSurfaceVariant = Color(0xFFD2CBDE),
                surfaceContainerHigh = Color(0x52392B60)
            )
            MaterialTheme(colorScheme = glassColorScheme, typography = RespectedTypography) { App() }
        }
    }
}

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
val grad = Brush.linearGradient(listOf(Color(0xFF6D4AFF), Color(0xFF2BD9C5)))

enum class SortOption(val label: String, val shortLabel: String) {
    NAME_ASC("Name (A to Z)", "A-Z"),
    NAME_DESC("Name (Z to A)", "Z-A"),
    DATE_DESC("Date (Newest)", "Newest"),
    DATE_ASC("Date (Oldest)", "Oldest")
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
    var tab by remember { mutableIntStateOf(vm.settings.lastTab) }
    var full by remember { mutableStateOf(false) }
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    LaunchedEffect(Unit) { ask.launch(perms) }
    LaunchedEffect(vm.playing) { while (vm.playing) { vm.tick(); delay(500) } }
    BackHandler(full) { full = false }

    Box(
        modifier = Modifier.fillMaxSize().background(glassAppBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (vm.item != null) MiniPlayer(vm) { full = true }
                    NavigationBar(
                        containerColor = Color(0x6618112B),
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0; vm.settings.lastTab = 0 },
                            icon = { Icon(Icons.Default.LibraryMusic, null) },
                            label = { Text("Library", fontWeight = FontWeight.SemiBold) }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1; vm.settings.lastTab = 1 },
                            icon = { Icon(Icons.Default.Language, null) },
                            label = { Text("Browser", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        ) { pad -> Box(Modifier.padding(pad)) { if (tab == 0) Library(vm, ask, perms) else Browser(vm) } }
        AnimatedVisibility(full, enter = slideInVertically { it }, exit = slideOutVertically { it }) { NowPlaying(vm) { full = false } }
    }
}

@Composable fun Art(uri: Uri?, size: Dp, r: Dp = 14.dp, mod: Modifier = Modifier) {
    Box(
        mod.size(size)
            .clip(RoundedCornerShape(r))
            .background(grad)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(r)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.85f))
        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable fun Library(
    vm: PlayerVM,
    askLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    perms: Array<String>
) {
    val context = LocalContext.current
    val hasAudioPerm = remember(vm.tracks) {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    if (!hasAudioPerm) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp), backgroundColor = Color(0x402A1E4A))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0x55C3B0FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFFC3B0FF)
                        )
                    }
                    Text(
                        "Audio Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Pulse requires audio access permissions to scan and play your local music library. Grant permissions to proceed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { askLauncher.launch(perms) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC3B0FF), contentColor = Color(0xFF230F55))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.3f))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Open App Settings", color = Color.White)
                    }
                }
            }
        }
        return
    }

    var q by remember { mutableStateOf("") }
    val sortOption = vm.sortOption
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

    val currentSortIcon = when (sortOption) {
        SortOption.NAME_ASC, SortOption.NAME_DESC -> Icons.Default.SortByAlpha
        SortOption.DATE_DESC -> Icons.Default.Schedule
        SortOption.DATE_ASC -> Icons.Default.CalendarToday
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glassSurface(RoundedCornerShape(28.dp)),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search songs or artists", color = Color.White.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFC3B0FF)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassActionButton(
                    onClick = { if (list.isNotEmpty()) vm.play(list, Random.nextInt(list.size), true) },
                    icon = Icons.Default.Shuffle,
                    text = "Shuffle",
                    highlight = vm.shuffle
                )
                GlassActionButton(
                    onClick = { if (list.isNotEmpty()) vm.play(list, 0) },
                    icon = Icons.Default.PlayArrow,
                    text = "Play All"
                )
            }

            Box {
                GlassActionButton(
                    onClick = { sortMenuExpanded = true },
                    icon = currentSortIcon,
                    text = sortOption.shortLabel,
                    trailingIcon = Icons.Default.ArrowDropDown,
                    highlight = true
                )

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.glassSurface(RoundedCornerShape(16.dp), backgroundColor = Color(0xEE1E1433))
                ) {
                    SortOption.entries.forEach { option ->
                        val itemIcon = when (option) {
                            SortOption.NAME_ASC, SortOption.NAME_DESC -> Icons.Default.SortByAlpha
                            SortOption.DATE_DESC -> Icons.Default.Schedule
                            SortOption.DATE_ASC -> Icons.Default.CalendarToday
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == sortOption) Color(0xFFC3B0FF) else Color.White
                                )
                            },
                            onClick = {
                                vm.updateSortOption(option)
                                sortMenuExpanded = false
                            },
                            leadingIcon = {
                                Icon(itemIcon, contentDescription = null, tint = if (option == sortOption) Color(0xFFC3B0FF) else Color.White.copy(0.7f))
                            }
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
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No songs found in device storage.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    val isCurrent = t.id.toString() == vm.item?.mediaId
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            .fillMaxWidth()
                            .glassSurface(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = if (isCurrent) Color(0x66492F8A) else Color(0x22241C38),
                                borderColor = if (isCurrent) Color(0xFFC3B0FF).copy(0.5f) else Color.White.copy(0.1f)
                            )
                            .clickable { vm.play(list, i) }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    t.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) Color(0xFFC3B0FF) else Color.White
                                )
                            },
                            supportingContent = {
                                Text(
                                    t.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            },
                            leadingContent = { Art(t.art, 48.dp) },
                            trailingContent = if (isCurrent && vm.playing) {
                                { Icon(Icons.Default.VolumeUp, contentDescription = "Playing", tint = Color(0xFFC3B0FF)) }
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
    Box(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(20.dp), backgroundColor = Color(0x52392B60))
            .clickable(onClick = open)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(m?.artworkUri, 48.dp, 12.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        m?.title?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        m?.artist?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                IconButton(
                    onClick = { if (vm.playing) vm.controller?.pause() else vm.controller?.play() },
                    modifier = Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFFC3B0FF), Color(0xFF9E86FF))),
                        CircleShape
                    )
                ) {
                    Icon(
                        if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (vm.playing) "Pause" else "Play",
                        tint = Color(0xFF230F55)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (vm.dur > 0) (vm.pos / vm.dur.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFC3B0FF),
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable fun NowPlaying(vm: PlayerVM, close: () -> Unit) {
    val m = vm.item?.mediaMetadata
    val c = vm.controller
    var seek by remember { mutableStateOf<Float?>(null) }
    val progress = seek ?: vm.pos.toFloat()

    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF321A63),
                        Color(0xFF170F2B),
                        Color(0xFF0B0715)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = close,
                    modifier = Modifier.glassSurface(CircleShape, backgroundColor = Color(0x33FFFFFF))
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.weight(0.5f))
            Box(
                modifier = Modifier.size(280.dp)
                    .glassSurface(RoundedCornerShape(28.dp), backgroundColor = Color(0x22FFFFFF), borderColor = Color.White.copy(0.3f))
                    .padding(8.dp)
            ) {
                Art(
                    m?.artworkUri,
                    264.dp,
                    22.dp,
                    Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                m?.title?.toString() ?: "Unknown Title",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                m?.artist?.toString() ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
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
                    thumbColor = Color(0xFFC3B0FF),
                    activeTrackColor = Color(0xFFC3B0FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
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
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    fmt(vm.dur),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(32.dp), backgroundColor = Color(0x28FFFFFF)).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { c?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (vm.shuffle) Color(0xFFC3B0FF) else Color.White.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { c?.seekToPrevious() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
                FilledIconButton(
                    onClick = { if (vm.playing) c?.pause() else c?.play() },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFC3B0FF),
                        contentColor = Color(0xFF230F55)
                    )
                ) {
                    Icon(
                        if (vm.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (vm.playing) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { c?.seekToNext() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp),
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
                        tint = if (vm.repeat != Player.REPEAT_MODE_OFF) Color(0xFFC3B0FF) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
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
            IconButton(
                onClick = {
                    val homeUrl = "https://duckduckgo.com"
                    url = homeUrl
                    vm.web.loadUrl(homeUrl)
                },
                modifier = Modifier.glassSurface(CircleShape, backgroundColor = Color(0x33FFFFFF))
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color(0xFFC3B0FF))
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).glassSurface(RoundedCornerShape(28.dp)),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search or enter URL", color = Color.White.copy(alpha = 0.6f)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.web.loadUrl(normalize(url)) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(
                onClick = { vm.web.reload() },
                modifier = Modifier.glassSurface(CircleShape, backgroundColor = Color(0x33FFFFFF))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(defaultBookmarks) { bookmark ->
                Box(
                    modifier = Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = Color(0x333B2E58),
                            borderColor = Color.White.copy(alpha = 0.15f)
                        )
                        .clickable {
                            url = bookmark.url
                            vm.web.loadUrl(bookmark.url)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(bookmark.badgeColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            bookmark.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            AndroidView({ vm.web }, Modifier.fillMaxSize())
            if (vm.found.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { vm.scan(); sheet = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
                text = { Text("Play as audio (${vm.found.size})", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFFC3B0FF),
                contentColor = Color(0xFF230F55)
            )
            else if (vm.blobOnly) Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    .glassSurface(RoundedCornerShape(16.dp), backgroundColor = Color(0x772A1E4A))
            ) {
                Text(
                    "Streaming page: it plays in its own player. Press play, then minimize; audio keeps going.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
    if (sheet) ModalBottomSheet(
        onDismissRequest = { sheet = false },
        containerColor = Color(0xEE1A112E)
    ) {
        Text(
            "Detected media",
            modifier = Modifier.padding(16.dp, 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        vm.found.toList().forEach { f ->
            ListItem(
                headlineContent = {
                    Text(
                        f.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                modifier = Modifier.clickable { vm.playUrl(f); sheet = false },
                supportingContent = {
                    Text(
                        Uri.parse(f.url).host ?: "",
                        maxLines = 1,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                },
                leadingContent = { Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFFC3B0FF)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

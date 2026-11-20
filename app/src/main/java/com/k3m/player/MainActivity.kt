package com.k3m.player

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { K3MApp() }
    }
}

@Composable
fun K3MApp() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var current by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun loadTracks() {
        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += Track(id, cursor.getString(titleCol) ?: "Unknown track", cursor.getString(artistCol) ?: "Unknown artist", ContentUris.withAppendedId(collection, id), cursor.getLong(durationCol))
            }
        }
        tracks = result
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) loadTracks()
    }

    fun playTrack(index: Int) {
        if (index !in tracks.indices) return
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(context, tracks[index].uri)
            setOnCompletionListener { if (index + 1 < tracks.size) playTrack(index + 1) else playing = false }
            prepare()
            start()
        }
        current = index
        playing = true
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB7F36B), secondary = Color(0xFF9BD84B), background = Color(0xFF0D0F0B), surface = Color(0xFF191D16))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0D0F0B)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(0xFFB7F36B), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = Color.Black) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("K3M PLAYER", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("YOUR MUSIC. YOUR RULES.", color = Color(0xFF9AA18F), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Equalizer, null, tint = Color(0xFFB7F36B), modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ТВОЯ МУЗЫКА", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${tracks.size} треков на устройстве", color = Color(0xFF9AA18F), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск трека или исполнителя", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFB7F36B)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB7F36B), unfocusedBorderColor = Color(0xFF343A2E), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            }
            if (!allowed) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.LibraryMusic, null, tint = Color(0xFFB7F36B), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Открой доступ к музыке", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("K3M Player найдёт аудиофайлы на телефоне.", color = Color(0xFF9AA18F))
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { requestPermission.launch(permission) }) { Text("Разрешить доступ") }
                }
            } else {
                LaunchedEffect(Unit) { loadTracks() }
                val filtered = tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (tracks.isEmpty()) "Музыка не найдена" else "Ничего не найдено", color = Color(0xFF9AA18F)) }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
                        items(filtered, key = { it.id }) { track ->
                            val index = tracks.indexOf(track)
                            Row(Modifier.fillMaxWidth().clickable { playTrack(index) }.padding(vertical = 9.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(Color(0xFF20271A), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, null, tint = if (current == index) Color(0xFFB7F36B) else Color(0xFF879078))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = if (current == index) Color(0xFFB7F36B) else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = Color(0xFF92998A), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                Text(formatTime(track.duration), color = Color(0xFF92998A), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (current >= 0 && current < tracks.size) {
                    Row(Modifier.fillMaxWidth().padding(14.dp).background(Color(0xFF20271A), RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tracks[current].title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tracks[current].artist, color = Color(0xFF9AA18F), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { if (current > 0) playTrack(current - 1) }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                        IconButton(onClick = { if (playing) { player?.pause(); playing = false } else { player?.start(); playing = true } }) {
                            Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, null, tint = Color(0xFFB7F36B), modifier = Modifier.size(38.dp))
                        }
                        IconButton(onClick = { if (current + 1 < tracks.size) playTrack(current + 1) }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

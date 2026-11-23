package com.neurasamu.build.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.GeneratedMedia
import com.neurasamu.build.data.MediaKind
import com.neurasamu.build.ui.theme.LocalFontScale
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GeneratedMediaCard(
    media: GeneratedMedia,
    onOpenLightbox: (String, String) -> Unit,
    onDownload: (GeneratedMedia) -> Unit,
    onRegenerate: () -> Unit
) {
    val ctx = LocalContext.current
    when (media.kind) {
        MediaKind.IMAGE -> GeneratedImageCard(
            media = media,
            onPreview = { onOpenLightbox(media.localPath, media.fileName) },
            onDownload = { onDownload(media) },
            onShare = { shareMediaFile(ctx, media.localPath, media.mimeType.ifBlank { "image/png" }) },
            onRegenerate = onRegenerate
        )
        MediaKind.AUDIO -> GeneratedAudioCard(
            media = media,
            onDownload = { onDownload(media) },
            onShare = { shareMediaFile(ctx, media.localPath, media.mimeType.ifBlank { "audio/wav" }) },
            onRegenerate = onRegenerate
        )
        MediaKind.VIDEO -> GeneratedVideoCard(
            media = media,
            onDownload = { onDownload(media) },
            onShare = { shareMediaFile(ctx, media.localPath, media.mimeType.ifBlank { "video/mp4" }) },
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun GeneratedImageCard(
    media: GeneratedMedia,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    var bmp by remember(media.localPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(media.localPath) {
        bmp = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(media.localPath)
            } catch (_: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.md)
            .border(1.dp, colors.divider, SaMuRadius.md)
            .background(colors.background)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = media.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable(onClick = onPreview)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Image unavailable", color = colors.textTertiary, fontSize = (12f * scale).sp)
            }
        }
        MediaFooterBar(
            title = media.fileName,
            subtitle = AtomicStorage.formatBytes(media.sizeBytes),
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun GeneratedAudioCard(
    media: GeneratedMedia,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var sliderVal by remember { mutableFloatStateOf(0f) }

    val player = remember(media.localPath) {
        try {
            MediaPlayer().apply {
                setDataSource(media.localPath)
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(player) {
        durationMs = player?.duration?.coerceAtLeast(1) ?: 0
        player?.setOnCompletionListener {
            isPlaying = false
            positionMs = 0
            sliderVal = 0f
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && player != null) {
            val pos = try { player.currentPosition } catch (_: Exception) { 0 }
            positionMs = pos
            sliderVal = if (durationMs > 0) (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            delay(150L)
        }
    }

    DisposableEffect(player) {
        onDispose {
            try {
                if (player?.isPlaying == true) player.stop()
                player?.release()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.md)
            .border(1.dp, colors.divider, SaMuRadius.md)
            .background(colors.background)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(SaMuRadius.full)
                    .background(colors.primary)
                    .clickable {
                        val p = player ?: return@clickable
                        if (isPlaying) {
                            p.pause()
                            isPlaying = false
                        } else {
                            p.start()
                            isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.fileName,
                    color = colors.onSurface,
                    fontSize = (12.5f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Slider(
                    value = sliderVal,
                    onValueChange = { v ->
                        sliderVal = v
                        val target = (v * durationMs).toInt()
                        positionMs = target
                        try { player?.seekTo(target) } catch (_: Exception) {}
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.surfaceVariant
                    ),
                    modifier = Modifier.height(26.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMillis(positionMs), color = colors.textSecondary, fontSize = (10.5f * scale).sp)
                    Text(formatMillis(durationMs), color = colors.textSecondary, fontSize = (10.5f * scale).sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        MediaFooterBar(
            title = AtomicStorage.formatBytes(media.sizeBytes),
            subtitle = "WAV Audio",
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }
}

@Composable
private fun GeneratedVideoCard(
    media: GeneratedMedia,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val colors = LocalSaMuColors.current
    var thumb by remember(media.localPath) { mutableStateOf<Bitmap?>(null) }
    var showVideoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(media.localPath) {
        thumb = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(media.localPath)
                val frame = retriever.getFrameAtTime(0)
                retriever.release()
                frame
            } catch (_: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.md)
            .border(1.dp, colors.divider, SaMuRadius.md)
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color.Black)
                .clickable { showVideoDialog = true },
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = media.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(SaMuRadius.full)
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        MediaFooterBar(
            title = media.fileName,
            subtitle = AtomicStorage.formatBytes(media.sizeBytes),
            onDownload = onDownload,
            onShare = onShare,
            onRegenerate = onRegenerate
        )
    }

    if (showVideoDialog) {
        InAppVideoPlayerModal(
            videoPath = media.localPath,
            fileName = media.fileName,
            onDismiss = { showVideoDialog = false }
        )
    }
}

@Composable
private fun MediaFooterBar(
    title: String,
    subtitle: String,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onSurface,
                fontSize = (11.5f * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = colors.textTertiary,
                fontSize = (10f * scale).sp
            )
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Download, "Download", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Share, "Share", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Refresh, "Regenerate", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun InAppVideoPlayerModal(
    videoPath: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(SaMuRadius.full)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Close, "Close video", tint = Color.White)
            }
            Text(
                text = fileName,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

fun shareMediaFile(ctx: Context, filePath: String, mimeType: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (_: Exception) {}
}

fun sharePlainText(ctx: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share message"))
    } catch (_: Exception) {}
}

private fun formatMillis(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

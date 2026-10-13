package com.example.iptvplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvplayer.MainViewModel
import com.example.iptvplayer.data.Details
import com.example.iptvplayer.data.MediaEntry
import com.example.iptvplayer.data.MediaType
import com.example.iptvplayer.data.SeriesEpisode

@Composable
fun DetailsScreen(vm: MainViewModel, entry: MediaEntry) {
    BackHandler { vm.detail = null }

    var details by remember(entry.id) { mutableStateOf<Details?>(null) }
    var done by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        details = vm.details(entry)
        done = true
    }
    var seasonIndex by remember(entry.id) { mutableIntStateOf(0) }

    val isSeries = entry.type == MediaType.SERIES
    val seasons = details?.seasons.orEmpty()
    val season = seasons.getOrNull(seasonIndex)
    val inList = entry.id in vm.favorites
    val resumeItem = if (isSeries) {
        vm.resume.firstOrNull { it.request.seriesId == entry.streamId }
    } else {
        vm.resume.firstOrNull { it.request.key == entry.id }
    }

    val primaryLabel = when {
        resumeItem == null -> "Assistir"
        isSeries -> "Continuar " + (resumeItem.request.subtitle?.substringBefore(" •") ?: "")
        else -> "Continuar"
    }.trim()

    val onPrimary: () -> Unit = {
        if (!isSeries) {
            vm.playEntry(entry)
        } else if (resumeItem != null) {
            vm.play(resumeItem.request.copy(startPositionMs = resumeItem.positionMs))
        } else if (season != null && season.episodes.isNotEmpty()) {
            vm.playEpisode(entry, season, season.episodes.first())
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.Background),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            DetailsHeader(
                entry = entry,
                details = details,
                inList = inList,
                primaryLabel = primaryLabel,
                onPrimary = onPrimary,
                onToggleList = { vm.toggleFavorite(entry) },
                onBack = { vm.detail = null },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (!done) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.Accent)
                    Spacer(Modifier.height(10.dp))
                }
                val plot = details?.plot ?: entry.plot
                if (!plot.isNullOrBlank()) {
                    Text(plot, color = Color(0xFFE6E6EA), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                }
                InfoLine("Gênero", details?.genre)
                InfoLine("Elenco", details?.cast)
                InfoLine("Direção", details?.director)
                InfoLine("Duração", details?.duration)
            }
        }

        if (isSeries) {
            if (season != null) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(seasons) { index, s ->
                            Pill(s.name, index == seasonIndex) { seasonIndex = index }
                        }
                    }
                }
                items(season.episodes) { episode ->
                    val saved = vm.resume.firstOrNull { it.request.key == "episode:${episode.id}" }
                    val progress = saved?.let {
                        if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs.toFloat()).coerceIn(0f, 1f) else null
                    }
                    EpisodeRow(episode, entry.poster, progress) {
                        vm.playEpisode(entry, season, episode)
                    }
                }
            } else if (done) {
                item {
                    Text(
                        "Não foi possível carregar os episódios. Verifique a conexão com o servidor.",
                        color = Palette.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(
    entry: MediaEntry,
    details: Details?,
    inList: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onToggleList: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Artwork(
            details?.backdrop ?: entry.poster ?: details?.poster,
            entry.name,
            Modifier.fillMaxWidth().height(340.dp),
            alignment = Alignment.TopCenter,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        0.25f to Color(0x66000000),
                        1f to Palette.Background,
                    )
                )
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp).focusRing(CircleShape, 2.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Artwork(
                entry.poster ?: details?.poster,
                entry.name,
                Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    entry.name,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(detailsMeta(entry, details), color = Palette.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(primaryLabel, onPrimary, primary = true, icon = Icons.Default.PlayArrow)
                    ActionButton(
                        if (inList) "Na lista" else "Minha lista",
                        onToggleList,
                        icon = if (inList) Icons.Default.Check else Icons.Default.Add,
                    )
                }
            }
        }
    }
}

private fun detailsMeta(entry: MediaEntry, details: Details?): String = listOfNotNull(
    (details?.year ?: entry.year)?.takeIf { it.isNotBlank() },
    (details?.rating ?: entry.rating)?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
    entry.category,
).joinToString("  •  ")

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        "$label: $value",
        color = Palette.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun EpisodeRow(
    episode: SeriesEpisode,
    fallbackImage: String?,
    progress: Float?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).height(76.dp).clip(RoundedCornerShape(8.dp))) {
            Artwork(
                episode.image ?: fallbackImage,
                episode.title,
                Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Palette.Accent,
                    trackColor = Color(0x66000000),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.number}. ${episode.title}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!episode.duration.isNullOrBlank()) {
                Text(episode.duration, color = Palette.TextMuted, fontSize = 12.sp)
            }
            if (!episode.plot.isNullOrBlank()) {
                Text(
                    episode.plot,
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

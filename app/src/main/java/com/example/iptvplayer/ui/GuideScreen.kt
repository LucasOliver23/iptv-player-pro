package com.example.iptvplayer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvplayer.MainViewModel
import com.example.iptvplayer.data.EpgProgram
import com.example.iptvplayer.data.MediaEntry
import com.example.iptvplayer.data.Reminders
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOUR_MS = 3_600_000L
private const val WINDOW_HOURS = 30
private const val BACK_HOURS = 6
private val HOUR_WIDTH = 170.dp
private val NAME_WIDTH = 112.dp
private val ROW_HEIGHT = 58.dp

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/**
 * Grade de programação (linha do tempo): canais nas linhas, horas nas colunas.
 * Toque num programa para assistir ao vivo, ver do início (catch-up) ou criar um lembrete.
 */
@Composable
fun GuideScreen(vm: MainViewModel) {
    BackHandler { vm.showGuide = false }

    val channels = remember { vm.guideChannels() }
    val now = System.currentTimeMillis()
    val windowStart = remember { ((now - BACK_HOURS * HOUR_MS) / HOUR_MS) * HOUR_MS }
    val windowEnd = windowStart + WINDOW_HOURS * HOUR_MS
    val totalWidth = HOUR_WIDTH * WINDOW_HOURS
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        val target = ((now - 30 * 60_000L - windowStart).toFloat() / HOUR_MS) * with(density) { HOUR_WIDTH.toPx() }
        scroll.scrollTo(target.toInt().coerceAtLeast(0))
    }

    var selected by remember { mutableStateOf<Pair<MediaEntry, EpgProgram>?>(null) }

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showGuide = false }, modifier = Modifier.focusRing(CircleShape, 2.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text("Guia de programação", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Cabeçalho das horas
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(NAME_WIDTH))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(totalWidth).height(26.dp)) {
                    for (i in 0 until WINDOW_HOURS) {
                        Text(
                            clock(windowStart + i * HOUR_MS),
                            color = Palette.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = HOUR_WIDTH * i).padding(start = 6.dp, top = 4.dp),
                        )
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            Text(
                "Nenhum canal na lista. Volte e escolha uma categoria na aba Ao vivo.",
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(channels, key = { it.id }) { channel ->
                GuideRow(vm, channel, channels, windowStart, windowEnd, totalWidth, scroll, now) { program ->
                    selected = channel to program
                }
            }
        }
    }

    selected?.let { (channel, program) ->
        ProgramDialog(vm, channel, program, channels) { selected = null }
    }
}

@Composable
private fun GuideRow(
    vm: MainViewModel,
    channel: MediaEntry,
    channels: List<MediaEntry>,
    windowStart: Long,
    windowEnd: Long,
    totalWidth: androidx.compose.ui.unit.Dp,
    scroll: androidx.compose.foundation.ScrollState,
    now: Long,
    onProgram: (EpgProgram) -> Unit,
) {
    var programs by remember(channel.id) { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var loaded by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        delay(250) // rolando rápido, o item sai da tela e a busca é cancelada
        programs = vm.fullEpg(channel.streamId)
        loaded = true
    }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 1.dp)) {
        val nameShape = RoundedCornerShape(6.dp)
        Column(
            Modifier
                .width(NAME_WIDTH)
                .fillMaxHeight()
                .focusRing(nameShape, 2.dp)
                .clip(nameShape)
                .background(Palette.Surface)
                .clickable { vm.playLiveFrom(channel, channels) }
                .padding(horizontal = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (channel.archiveDays > 0) {
                Text("⟲ ${channel.archiveDays}d", color = Palette.Accent, fontSize = 10.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                if (loaded && programs.isEmpty()) {
                    Text(
                        "Sem programação",
                        color = Palette.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = HOUR_WIDTH * 5).padding(start = 8.dp),
                    )
                }
                for (p in programs) {
                    if (p.endMs <= windowStart || p.startMs >= windowEnd) continue
                    val s = maxOf(p.startMs, windowStart)
                    val e = minOf(p.endMs, windowEnd)
                    val x = HOUR_WIDTH * ((s - windowStart).toFloat() / HOUR_MS)
                    val w = HOUR_WIDTH * ((e - s).toFloat() / HOUR_MS) - 3.dp
                    if (w < 6.dp) continue
                    val isNow = now in p.startMs until p.endMs
                    val past = p.endMs <= now
                    val shape = RoundedCornerShape(6.dp)
                    Column(
                        Modifier
                            .offset(x = x)
                            .width(w)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .focusRing(shape, 2.dp)
                            .clip(shape)
                            .background(if (isNow) Color(0xFF3A1216) else if (past) Color(0xFF1B1B20) else Palette.SurfaceHigh)
                            .clickable { onProgram(p) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            p.title.ifBlank { "Sem título" },
                            color = if (past) Palette.TextSecondary else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            clock(p.startMs) + " – " + clock(p.endMs) +
                                if (past && channel.archiveDays > 0 && p.hasArchive) "  ⟲" else "",
                            color = Palette.TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
                // Linha do "agora"
                if (now in windowStart until windowEnd) {
                    Box(
                        Modifier
                            .offset(x = HOUR_WIDTH * ((now - windowStart).toFloat() / HOUR_MS))
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Palette.Accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramDialog(
    vm: MainViewModel,
    channel: MediaEntry,
    program: EpgProgram,
    channels: List<MediaEntry>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val future = program.startMs > now
    val canCatchup = channel.archiveDays > 0 && program.hasArchive && program.startMs < now &&
        program.startMs > now - channel.archiveDays * 24L * HOUR_MS
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(program.title.ifBlank { "Sem título" }, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "${channel.name} • ${clock(program.startMs)} – ${clock(program.endMs)}",
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                )
                if (program.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(program.description, color = Color(0xFFE6E6EA), fontSize = 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                if (!future) {
                    ActionButton(
                        "Assistir ao vivo",
                        { onDismiss(); vm.playLiveFrom(channel, channels) },
                        Modifier.fillMaxWidth(),
                        primary = !canCatchup,
                        icon = Icons.Default.PlayArrow,
                    )
                }
                if (canCatchup) {
                    Spacer(Modifier.height(8.dp))
                    ActionButton(
                        "Assistir do início (catch-up)",
                        { onDismiss(); vm.playCatchup(channel, program) },
                        Modifier.fillMaxWidth(),
                        primary = true,
                        icon = Icons.Default.PlayArrow,
                    )
                } else if (!future && channel.archiveDays <= 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Este canal não guarda programas passados.", color = Palette.TextMuted, fontSize = 12.sp)
                }
                if (future) {
                    val rid = Reminders.idFor(channel.streamId, program.startMs)
                    ActionButton(
                        "Lembrar deste programa",
                        {
                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val ok = Reminders.add(context, Reminders.Item(rid, channel.name, program.title, program.startMs))
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    "Você será avisado às ${clock(program.startMs - Reminders.LEAD_MS)}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            onDismiss()
                        },
                        Modifier.fillMaxWidth(),
                        primary = true,
                        icon = Icons.Default.Notifications,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                "Fechar",
                color = Palette.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

package com.example.iptvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.iptvplayer.data.Reminders
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptvplayer.NavTab
import com.example.iptvplayer.data.EpgProgram
import com.example.iptvplayer.data.MediaEntry
import com.example.iptvplayer.data.MediaType
import com.example.iptvplayer.data.ResumeItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Foco (controle remoto / D-pad): contorno branco no item focado.
// Use ANTES do .clickable() na cadeia de modificadores.
// ---------------------------------------------------------------------------
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(10.dp), width: Dp = 3.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val base = Modifier.onFocusChanged { focused = it.isFocused }
    if (focused) base.border(width, Color.White, shape) else base
}

// ---------------------------------------------------------------------------
// Imagem com fallback (iniciais) quando não há URL ou o carregamento falha.
// ---------------------------------------------------------------------------
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank() || failed) {
            Text(
                title.trim().take(2).uppercase(),
                color = Palette.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Campo de busca compacto (usado em Ao vivo, Filmes e Séries)
// ---------------------------------------------------------------------------
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(Palette.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = Palette.TextMuted, fontSize = 14.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Limpar busca",
                tint = Palette.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Botões / chips
// ---------------------------------------------------------------------------
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(if (selected) (if (accent) Palette.Accent else Color.White) else Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected && !accent) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .focusRing(shape)
            .clip(shape)
            .background(if (primary) Color.White else Color(0x66FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (primary) Color.Black else Color.White)
        }
        Text(
            text,
            color = if (primary) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------
@Composable
fun TopNav(current: NavTab, onSelect: (NavTab) -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "VORTEX PLAY",
            color = Palette.Accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(20.dp))
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.filter { it != NavTab.SEARCH }.forEach { tab ->
                NavItem(tab.label, tab == current) { onSelect(tab) }
            }
        }
        IconButton(onClick = { onSelect(NavTab.SEARCH) }, modifier = Modifier.focusRing(CircleShape, 2.dp)) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Buscar",
                tint = if (current == NavTab.SEARCH) Palette.Accent else Color.White,
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.focusRing(CircleShape, 2.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Color.White)
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        label,
        color = if (selected) Color.White else Palette.TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 15.sp,
        maxLines = 1,
        modifier = Modifier
            .focusRing(shape, 2.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun PosterCard(
    entry: MediaEntry,
    modifier: Modifier = Modifier.width(108.dp),
    favorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Artwork(
                entry.poster,
                entry.name,
                Modifier
                    .fillMaxSize()
                    .focusRing(shape)
                    .clip(shape)
                    .clickable(onClick = onClick),
            )
            if (onFavorite != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .clickable(onClick = onFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (favorite) Color(0xFFFF3B4A) else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            entry.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun ChannelCard(entry: MediaEntry, modifier: Modifier = Modifier.width(150.dp), onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .focusRing(shape)
                .clip(shape)
                .background(Palette.Surface)
                .clickable(onClick = onClick)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(entry.poster, entry.name, Modifier.fillMaxSize(), ContentScale.Fit)
        }
        Text(
            entry.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Escolhe o card certo para o tipo do item. */
@Composable
fun MediaCard(
    entry: MediaEntry,
    modifier: Modifier? = null,
    favorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Sem modifier explícito (fileiras): largura fixa. Em grades: o chamador passa fillMaxWidth().
    if (entry.type == MediaType.LIVE) {
        ChannelCard(entry, modifier ?: Modifier.width(150.dp), onClick)
    } else {
        PosterCard(entry, modifier ?: Modifier.width(108.dp), favorite, onFavorite, onClick)
    }
}

@Composable
fun ResumeCard(item: ResumeItem, onRemove: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val fraction = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Column(Modifier.width(180.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .focusRing(shape)
                .clip(shape)
                .background(Palette.SurfaceHigh)
                .clickable(onClick = onClick)
        ) {
            Artwork(
                item.request.image,
                item.request.title,
                Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remover de continuar assistindo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = Palette.Accent,
                trackColor = Color(0x66000000),
            )
        }
        Text(
            item.request.title,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val sub = listOfNotNull(item.request.subtitle, "Parou em ${formatPosition(item.positionMs)}").joinToString(" • ")
        Text(sub, color = Palette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Fileiras e filtros
// ---------------------------------------------------------------------------
@Composable
fun MediaRow(
    title: String,
    items: List<MediaEntry>,
    favorites: Set<String> = emptySet(),
    onFavorite: ((MediaEntry) -> Unit)? = null,
    onOpen: (MediaEntry) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 6.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items) { entry ->
                MediaCard(
                    entry,
                    favorite = entry.id in favorites,
                    onFavorite = onFavorite?.let { f -> { f(entry) } },
                ) { onOpen(entry) }
            }
        }
    }
}

@Composable
fun CategoryChips(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    favOnly: Boolean = false,
    onFavOnly: ((Boolean) -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onFavOnly != null) {
            item { Pill("★ Favoritos", favOnly, accent = true) { onFavOnly(!favOnly) } }
        }
        item {
            Pill("Todos", selected == null && !favOnly) {
                onFavOnly?.invoke(false)
                onSelect(null)
            }
        }
        items(categories) { name ->
            Pill(name, selected == name && !favOnly) {
                onFavOnly?.invoke(false)
                onSelect(name)
            }
        }
    }
}

@Composable
fun MessageBox(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Palette.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------------------
// EPG (programação): usado no painel da aba "Ao vivo" e no player.
// ---------------------------------------------------------------------------
@Composable
fun EpgList(
    programs: List<EpgProgram>,
    modifier: Modifier = Modifier,
    maxItems: Int = 4,
    /** Se informado, programas futuros ganham o sino de lembrete. */
    channelName: String? = null,
    streamKey: String = "",
) {
    val context = LocalContext.current
    var reminded by remember { mutableStateOf(Reminders.load(context).map { it.id }.toSet()) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = System.currentTimeMillis()
    val upcoming = programs.filter { it.endMs > now }.take(maxItems)
    Column(modifier) {
        if (upcoming.isEmpty()) {
            Text("Sem programação disponível", color = Palette.TextSecondary, fontSize = 13.sp)
        }
        upcoming.forEach { p ->
            val isNow = now >= p.startMs && now < p.endMs
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isNow) "AGORA" else formatClock(p.startMs),
                        color = if (isNow) Palette.Accent else Palette.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.width(54.dp),
                    )
                    Text(
                        p.title.ifBlank { "Sem título" },
                        color = Color.White,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (channelName != null && p.startMs > now) {
                        val rid = Reminders.idFor(streamKey, p.startMs)
                        val on = rid in reminded
                        val bellShape = RoundedCornerShape(50)
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = if (on) "Remover lembrete" else "Lembrar deste programa",
                            tint = if (on) Palette.Accent else Palette.TextMuted,
                            modifier = Modifier
                                .size(34.dp)
                                .focusRing(bellShape, 2.dp)
                                .clip(bellShape)
                                .clickable {
                                    if (on) {
                                        Reminders.remove(context, rid)
                                        reminded = reminded - rid
                                        Toast.makeText(context, "Lembrete removido", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                            androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, android.Manifest.permission.POST_NOTIFICATIONS,
                                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        val ok = Reminders.add(context, Reminders.Item(rid, channelName, p.title, p.startMs))
                                        if (ok) {
                                            reminded = reminded + rid
                                            Toast.makeText(
                                                context,
                                                "Você será avisado às ${formatClock(p.startMs - Reminders.LEAD_MS)}",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                                .padding(6.dp),
                        )
                    }
                }
                if (isNow) {
                    val total = (p.endMs - p.startMs).coerceAtLeast(1L).toFloat()
                    val fraction = ((now - p.startMs).toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .padding(start = 54.dp, top = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Palette.Accent,
                        trackColor = Color(0x33FFFFFF),
                    )
                    if (p.description.isNotBlank()) {
                        Text(
                            p.description,
                            color = Palette.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 54.dp, top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatPosition(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val sec = total % 60L
    return if (h > 0L) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%d:%02d", m, sec)
}

private fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

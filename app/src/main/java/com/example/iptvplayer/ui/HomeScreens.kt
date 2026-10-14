package com.example.iptvplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvplayer.MainViewModel
import com.example.iptvplayer.NavTab
import com.example.iptvplayer.data.AccountInfo
import com.example.iptvplayer.data.Details
import com.example.iptvplayer.data.EpgProgram
import com.example.iptvplayer.data.MediaEntry
import com.example.iptvplayer.data.MediaType
import com.example.iptvplayer.data.searchNormalize
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Menu lateral estilo Netflix: trilho de ícones que expande com o rótulo
// quando recebe foco (controle remoto) ou ao tocar no botão de menu.
// ---------------------------------------------------------------------------
private val SidebarCollapsed = 64.dp
private val SidebarExpanded = 220.dp


@Composable
fun SideNav(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    val expanded = hasFocus || pinned
    val width by animateDpAsState(if (expanded) SidebarExpanded else SidebarCollapsed, label = "sidebarWidth")

    BackHandler(enabled = pinned) { pinned = false }

    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (expanded) {
                    Brush.horizontalGradient(
                        0f to Palette.Background,
                        0.85f to Palette.Background.copy(alpha = 0.97f),
                        1f to Color.Transparent,
                    )
                } else {
                    Brush.horizontalGradient(0f to Palette.Background, 1f to Palette.Background)
                }
            )
            .onFocusChanged { hasFocus = it.hasFocus }
            .padding(vertical = 12.dp),
    ) {
        SideNavItem(
            icon = { c -> Icon(Icons.Default.Menu, "Menu", tint = c, modifier = Modifier.size(24.dp)) },
            label = "VORTEX PLAY",
            selected = false,
            expanded = expanded,
            labelColor = Palette.Accent,
            bold = true,
            onClick = { pinned = !pinned },
        )
        Spacer(Modifier.height(12.dp))
        NavTab.values().forEach { tab ->
            SideNavItem(
                icon = { c -> NavIcon(tab, c, 24.dp) },
                label = tab.label,
                selected = tab == currentTab,
                expanded = expanded,
                onClick = {
                    pinned = false
                    onTabSelected(tab)
                },
            )
        }
        Spacer(Modifier.weight(1f))
        SideNavItem(
            icon = { c -> Icon(Icons.Default.Settings, "Configurações", tint = c, modifier = Modifier.size(24.dp)) },
            label = "Configurações",
            selected = false,
            expanded = expanded,
            onClick = {
                pinned = false
                onSettingsClick()
            },
        )
    }
}

@Composable
private fun SideNavItem(
    icon: @Composable (Color) -> Unit,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    labelColor: Color? = null,
    bold: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val tint = labelColor ?: if (selected) Color.White else Palette.TextMuted
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                color = tint,
                fontSize = 15.sp,
                fontWeight = if (selected || bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Barra superior (celular): logo + favoritos + configurações
// ---------------------------------------------------------------------------
@Composable
private fun HomeTopBar(onFavorites: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon(Icons.Default.Favorite, "Minha lista", onFavorites)
            TopIcon(Icons.Default.Settings, "Configurações", onSettings)
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = description,
        tint = Palette.TextSecondary,
        modifier = Modifier
            .focusRing(CircleShape, 2.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .size(22.dp),
    )
}

// ---------------------------------------------------------------------------
// Menu embaixo (celular): barra flutuante arredondada
// ---------------------------------------------------------------------------
private val BottomTabs = listOf(NavTab.HOME, NavTab.LIVE, NavTab.MOVIES, NavTab.SERIES, NavTab.SEARCH)

@Composable
fun BottomNav(currentTab: NavTab, onTabSelected: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Color(0xF5262629))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTabs.forEach { tab ->
            val selected = tab == currentTab
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .focusRing(shape, 2.dp)
                    .clip(shape)
                    .background(if (selected) Color(0xFF3A3A42) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NavIcon(tab, if (selected) Color.White else Palette.TextMuted, 22.dp)
                Text(
                    tab.label,
                    color = if (selected) Color.White else Palette.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun TabContent(vm: MainViewModel) {
    when (vm.tab) {
        NavTab.HOME -> HomeScreen(vm)
        NavTab.LIVE -> LiveScreen(vm)
        NavTab.MOVIES -> BrowseScreen(vm, MediaType.MOVIE)
        NavTab.SERIES -> BrowseScreen(vm, MediaType.SERIES)
        NavTab.LIST -> MyListScreen(vm)
        NavTab.SEARCH -> SearchScreen(vm)
    }
}

// ---------------------------------------------------------------------------
// Casca: celular = barra no topo + menu embaixo; tela larga = menu lateral
// ---------------------------------------------------------------------------
@Composable
fun HomeShell(vm: MainViewModel) {
    BackHandler(enabled = vm.tab != NavTab.HOME) { vm.tab = NavTab.HOME }
    val compact = LocalConfiguration.current.screenWidthDp < 600

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (compact) {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(onFavorites = { vm.tab = NavTab.LIST }, onSettings = { vm.showSettings = true })
                Box(Modifier.weight(1f).fillMaxWidth()) { TabContent(vm) }
            }
            BottomNav(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter))
        } else {
            Box(Modifier.fillMaxSize().padding(start = SidebarCollapsed)) { TabContent(vm) }
            SideNav(
                currentTab = vm.tab,
                onTabSelected = { vm.tab = it },
                onSettingsClick = { vm.showSettings = true },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Início: destaque + fileiras
// ---------------------------------------------------------------------------
@Composable
fun HomeScreen(vm: MainViewModel) {
    val home = vm.home
    val favorites = vm.favorites
    val myList = remember(vm.catalog, favorites) {
        vm.catalog.all.filter { it.id in favorites }.take(40)
    }

    LazyColumn(
        state = vm.homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { ExpiryBanner(vm.account) { vm.accessRequest = AccessRequest.RENEW } }
        val hero = home.hero
        if (hero != null) item { HeroBanner(vm, hero) }
        if (vm.resume.isNotEmpty()) item { ContinueRow(vm) }
        if (myList.isNotEmpty()) item { MediaRow("Meus favoritos", myList, favorites, vm::toggleFavorite) { vm.open(it) } }
        items(home.rows) { row -> MediaRow(row.title, row.items, favorites, vm::toggleFavorite) { vm.open(it) } }
    }
}

@Composable
private fun HeroBanner(vm: MainViewModel, entry: MediaEntry) {
    val details by produceState<Details?>(null, entry.id) { value = vm.details(entry) }
    val config = LocalConfiguration.current
    val inList = entry.id in vm.favorites
    val plot = details?.plot ?: entry.plot

    if (config.screenWidthDp < 600) {
        HeroCard(vm, entry, details, inList)
        return
    }
    val height = (config.screenHeightDp * 0.6f).dp

    Box(Modifier.fillMaxWidth().height(height)) {
        Artwork(
            details?.backdrop ?: entry.poster,
            entry.name,
            Modifier.fillMaxSize(),
            alignment = Alignment.TopCenter,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Palette.Background.copy(alpha = 0.95f),
                    0.7f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Palette.Background,
                )
            )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                entry.name,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(metaLine(entry, details), color = Palette.TextSecondary, fontSize = 13.sp)
            if (!plot.isNullOrBlank()) {
                Text(
                    plot,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton("Assistir", { vm.playOrOpen(entry) }, primary = true, icon = Icons.Default.PlayArrow)
                ActionButton(
                    if (inList) "Na lista" else "Minha lista",
                    { vm.toggleFavorite(entry) },
                    icon = if (inList) Icons.Default.Check else Icons.Default.Add,
                )
                ActionButton("Detalhes", { vm.detail = entry }, icon = Icons.Default.Info)
            }
        }
    }
}

/** Destaque compacto (celular), no estilo do app da Netflix: capa arredondada + botões embaixo. */
@Composable
private fun HeroCard(vm: MainViewModel, entry: MediaEntry, details: Details?, inList: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .focusRing(shape)
                .clip(shape)
                .clickable { vm.detail = entry },
        ) {
            Artwork(
                entry.poster ?: details?.backdrop,
                entry.name,
                Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xF2000000),
                    )
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    entry.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(metaLine(entry, details), color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton("Assistir", { vm.playOrOpen(entry) }, Modifier.fillMaxWidth(), primary = true, icon = Icons.Default.PlayArrow)
        Spacer(Modifier.height(6.dp))
        ActionButton(
            if (inList) "Na lista" else "Minha lista",
            { vm.toggleFavorite(entry) },
            Modifier.fillMaxWidth(),
            icon = if (inList) Icons.Default.Check else Icons.Default.Add,
        )
    }
}

// ---------------------------------------------------------------------------
// Aviso de vencimento da assinatura (topo do Início)
// ---------------------------------------------------------------------------
@Composable
private fun ExpiryBanner(account: AccountInfo?, onRenew: () -> Unit) {
    val expSec = account?.expiresAtSec?.takeIf { it > 0L } ?: return
    val expMs = expSec * 1000L
    val leftMs = expMs - System.currentTimeMillis()
    val days = leftMs / 86_400_000L
    val hours = (leftMs % 86_400_000L) / 3_600_000L
    val date = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(expMs))

    val text: String
    val color: Color
    when {
        leftMs <= 0L -> { text = "Assinatura vencida"; color = Palette.Accent }
        days == 0L -> { text = "Vence hoje • faltam ${hours}h"; color = Palette.Accent }
        days <= 3L -> { text = "Vence em $days ${if (days == 1L) "dia" else "dias"}"; color = Palette.Accent }
        days <= 15L -> { text = "Vence em $days dias"; color = Color(0xFFFFB020) }
        else -> { text = "Vence em $days dias"; color = Color(0xFF46D369) }
    }

    // Faltando 5 dias ou menos, o aviso vira um botão "Renovar".
    val showRenew = days <= 5L
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (showRenew) Modifier.focusRing(shape, 2.dp) else Modifier)
            .clip(shape)
            .background(Palette.Surface)
            .then(if (showRenew) Modifier.clickable(onClick = onRenew) else Modifier)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("  •  $date", color = Palette.TextSecondary, fontSize = 12.sp)
        if (showRenew) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Renovar",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(shape)
                    .background(Palette.Accent)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

private fun metaLine(entry: MediaEntry, details: Details?): String = listOfNotNull(
    (details?.year ?: entry.year)?.takeIf { it.isNotBlank() },
    (details?.rating ?: entry.rating)?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
    entry.category,
).joinToString("  •  ")

@Composable
private fun ContinueRow(vm: MainViewModel) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Continuar assistindo")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.resume) { item ->
                ResumeCard(
                    item,
                    onRemove = { vm.removeResume(item.request.key) },
                ) { vm.play(item.request.copy(startPositionMs = item.positionMs)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Filmes / Séries: filtro por categoria + grade de capas
// ---------------------------------------------------------------------------
@Composable
fun BrowseScreen(vm: MainViewModel, type: MediaType) {
    val isMovies = type == MediaType.MOVIE
    val source = if (isMovies) vm.catalog.movies else vm.catalog.series
    val categories = if (isMovies) vm.catalog.movieCategories else vm.catalog.seriesCategories
    val selected = if (isMovies) vm.movieCategory else vm.seriesCategory
    val favOnly = if (isMovies) vm.movieFavOnly else vm.seriesFavOnly
    val favs = vm.favorites
    val favItems = remember(source, favs) { source.filter { it.id in favs } }
    val query = if (isMovies) vm.movieQuery else vm.seriesQuery
    val q = query.trim().searchNormalize()
    val filtered = remember(source, selected, favOnly, favItems, q) {
        when {
            q.isNotEmpty() -> (if (favOnly) favItems else source).filter { it.searchKey.contains(q) }.take(600)
            favOnly -> favItems
            selected == null -> source
            else -> source.filter { it.category == selected }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = query,
            onValueChange = { if (isMovies) vm.movieQuery = it else vm.seriesQuery = it },
            placeholder = if (isMovies) "Buscar filmes" else "Buscar séries",
        )
        CategoryChips(
            categories,
            selected,
            { if (isMovies) vm.movieCategory = it else vm.seriesCategory = it },
            favOnly,
            { if (isMovies) vm.movieFavOnly = it else vm.seriesFavOnly = it },
        )
        if (filtered.isEmpty()) {
            if (q.isNotEmpty()) {
                MessageBox("Nenhum resultado para \"${query.trim()}\"")
            } else if (favOnly) {
                MessageBox(
                    "Nenhum favorito ainda",
                    "Toque no coração em cima da capa para guardar aqui.",
                )
            } else {
                MessageBox("Nada por aqui", "Este servidor não trouxe itens nesta categoria.")
            }
        } else {
            MediaGrid(
                entries = filtered,
                onOpen = { vm.open(it) },
                state = if (isMovies) vm.movieGridState else vm.seriesGridState,
                favorites = favs,
                onFavorite = vm::toggleFavorite,
                strip = if (favOnly || q.isNotEmpty()) emptyList() else favItems.take(30),
                stripTitle = "★ Favoritos",
            )
        }
    }
}

/** Fileira horizontal de favoritos (sem margem própria: quem usa já aplica o padding da tela). */
@Composable
private fun FavoritesStrip(
    title: String,
    entries: List<MediaEntry>,
    favorites: Set<String>,
    onFavorite: (MediaEntry) -> Unit,
    onOpen: (MediaEntry) -> Unit,
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                MediaCard(
                    entry,
                    favorite = entry.id in favorites,
                    onFavorite = { onFavorite(entry) },
                ) { onOpen(entry) }
            }
        }
    }
}

@Composable
private fun MediaGrid(
    entries: List<MediaEntry>,
    onOpen: (MediaEntry) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
    favorites: Set<String> = emptySet(),
    onFavorite: ((MediaEntry) -> Unit)? = null,
    strip: List<MediaEntry> = emptyList(),
    stripTitle: String = "",
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (strip.isNotEmpty() && onFavorite != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FavoritesStrip(stripTitle, strip, favorites, onFavorite, onOpen)
            }
        }
        items(entries) { entry ->
            MediaCard(
                entry,
                Modifier.fillMaxWidth(),
                favorite = entry.id in favorites,
                onFavorite = onFavorite?.let { f -> { f(entry) } },
            ) { onOpen(entry) }
        }
    }
}

// ---------------------------------------------------------------------------
// Minha lista
// ---------------------------------------------------------------------------
@Composable
fun MyListScreen(vm: MainViewModel) {
    val favorites = vm.favorites
    val entries = remember(vm.catalog, favorites) {
        vm.catalog.all.filter { it.id in favorites }
    }
    if (entries.isEmpty()) {
        MessageBox(
            "Sua lista está vazia",
            "Abra um filme, série ou canal e use \"Minha lista\" (ou a estrela nos canais).",
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            SectionTitle("Minha lista • ${entries.size}")
            MediaGrid(entries = entries, onOpen = { vm.open(it) }, favorites = favorites, onFavorite = vm::toggleFavorite)
        }
    }
}

// ---------------------------------------------------------------------------
// Busca
// ---------------------------------------------------------------------------
@Composable
fun SearchScreen(vm: MainViewModel) {
    val query = vm.query
    val results = remember(query, vm.catalog) {
        val q = query.trim().searchNormalize()
        if (q.length < 2) {
            emptyList<MediaEntry>()
        } else {
            vm.catalog.all.filter { it.searchKey.contains(q) }.take(240)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Buscar canais, filmes e séries") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        when {
            query.trim().length < 2 ->
                MessageBox("Digite pelo menos 2 letras", "A busca procura em canais, filmes e séries.")
            results.isEmpty() ->
                MessageBox("Nenhum resultado para \"${query.trim()}\"")
            else -> MediaGrid(entries = results, onOpen = { vm.open(it) }, favorites = vm.favorites, onFavorite = vm::toggleFavorite)
        }
    }
}

// ---------------------------------------------------------------------------
// Ao vivo: lista de canais + painel de programação (EPG)
// ---------------------------------------------------------------------------
@Composable
fun LiveScreen(vm: MainViewModel) {
    val selectedCategory = vm.liveCategory
    val favOnly = vm.liveFavOnly
    val favs = vm.favorites
    val favChannels = remember(vm.catalog, favs) { vm.catalog.live.filter { it.id in favs } }
    val q = vm.liveQuery.trim().searchNormalize()
    val channels = remember(vm.catalog, selectedCategory, favOnly, favChannels, q) {
        when {
            q.isNotEmpty() -> (if (favOnly) favChannels else vm.catalog.live).filter { it.searchKey.contains(q) }.take(600)
            favOnly -> favChannels
            selectedCategory == null -> vm.catalog.live
            else -> vm.catalog.live.filter { it.category == selectedCategory }
        }
    }
    var focused by remember { mutableStateOf<MediaEntry?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 700

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = vm.liveQuery,
            onValueChange = { vm.liveQuery = it },
            placeholder = "Buscar canais",
        )
        CategoryChips(
            vm.catalog.liveCategories,
            selectedCategory,
            { vm.liveCategory = it },
            favOnly,
            { vm.liveFavOnly = it },
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = vm.liveListState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!favOnly && q.isEmpty() && favChannels.isNotEmpty()) {
                    item {
                        FavoritesStrip(
                            "★ Canais favoritos",
                            favChannels.take(30),
                            favs,
                            vm::toggleFavorite,
                        ) { vm.open(it) }
                    }
                }
                if (q.isNotEmpty() && channels.isEmpty()) {
                    item {
                        Text(
                            "Nenhum canal encontrado para \"${vm.liveQuery.trim()}\".",
                            color = Palette.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                if (q.isEmpty() && favOnly && channels.isEmpty()) {
                    item {
                        Text(
                            "Nenhum canal favorito ainda. Toque na estrela de um canal para guardar aqui.",
                            color = Palette.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(channels) { channel ->
                    ChannelRow(
                        channel = channel,
                        isFavorite = channel.id in vm.favorites,
                        onFocus = { focused = channel },
                        onClick = {
                            focused = channel
                            vm.open(channel)
                        },
                        onFavorite = { vm.toggleFavorite(channel) },
                    )
                }
            }
            if (wide) {
                EpgPane(
                    vm,
                    focused,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 24.dp, top = 8.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: MediaEntry,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            channel.poster,
            channel.name,
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                channel.category,
                color = Palette.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.Star,
            contentDescription = "Favoritar",
            tint = if (isFavorite) Palette.Accent else Palette.TextMuted,
            modifier = Modifier
                .focusRing(CircleShape, 2.dp)
                .clip(CircleShape)
                .clickable(onClick = onFavorite)
                .padding(8.dp)
                .size(22.dp),
        )
    }
}

@Composable
private fun EpgPane(vm: MainViewModel, channel: MediaEntry?, modifier: Modifier) {
    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(channel?.id) {
        programs = emptyList()
        if (channel != null) {
            loading = true
            try {
                delay(350)
                programs = vm.shortEpg(channel.streamId, 8)
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(16.dp),
    ) {
        if (channel == null) {
            Text(
                "Navegue pelos canais para ver a programação.",
                color = Palette.TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    channel.poster,
                    channel.name,
                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    ContentScale.Fit,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        channel.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(channel.category, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.Accent)
            } else {
                EpgList(programs, maxItems = 6)
            }
        }
    }
}
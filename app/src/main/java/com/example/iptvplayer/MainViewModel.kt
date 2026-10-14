package com.example.iptvplayer

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptvplayer.data.AccountInfo
import com.example.iptvplayer.data.Catalog
import com.example.iptvplayer.data.Details
import com.example.iptvplayer.data.EpgProgram
import com.example.iptvplayer.data.HomeContent
import com.example.iptvplayer.data.HomeRow
import com.example.iptvplayer.data.MediaEntry
import com.example.iptvplayer.data.MediaType
import com.example.iptvplayer.data.PlayKind
import com.example.iptvplayer.data.PlayRequest
import com.example.iptvplayer.data.ResumeItem
import com.example.iptvplayer.data.SeriesEpisode
import com.example.iptvplayer.data.SeriesSeason
import com.example.iptvplayer.data.Store
import com.example.iptvplayer.data.Updater
import com.example.iptvplayer.data.XtreamApi
import com.example.iptvplayer.data.searchNormalize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class NavTab(val label: String) {
    HOME("Início"),
    LIVE("Ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    LIST("Minha lista"),
    SEARCH("Buscar"),
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store = Store(app)
    private var api: XtreamApi? = null
    private val detailsCache = HashMap<String, Details>()

    // ---------- Sessão / catálogo ----------
    var catalog by mutableStateOf(Catalog())
        private set
    var home by mutableStateOf(HomeContent())
        private set
    var account by mutableStateOf<AccountInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var showLogin by mutableStateOf(!store.hasCredentials)
    var showSettings by mutableStateOf(false)

    /** Pedido aberto (teste / assinar / renovar) e usuário+senha para pré-preencher o login. */
    var accessRequest by mutableStateOf<com.example.iptvplayer.ui.AccessRequest?>(null)
    var loginPrefill by mutableStateOf<Pair<String, String>?>(null)

    // ---------- Navegação ----------
    var tab by mutableStateOf(NavTab.HOME)
    var detail by mutableStateOf<MediaEntry?>(null)
    var playing by mutableStateOf<PlayRequest?>(null)
        private set

    /** true enquanto o app está na janela flutuante (picture-in-picture). */
    var inPip by mutableStateOf(false)

    // Estados de rolagem/filtros ficam aqui para sobreviverem à ida e volta entre telas.
    val homeListState = LazyListState()
    val liveListState = LazyListState()
    val movieGridState = LazyGridState()
    val seriesGridState = LazyGridState()
    var liveCategory by mutableStateOf<String?>(null)
    var liveFavOnly by mutableStateOf(false)
    var liveQuery by mutableStateOf("")
    var movieQuery by mutableStateOf("")
    var seriesQuery by mutableStateOf("")
    var movieFavOnly by mutableStateOf(false)
    var seriesFavOnly by mutableStateOf(false)
    var movieCategory by mutableStateOf<String?>(null)
    var seriesCategory by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    // ---------- Dados do usuário ----------
    var favorites by mutableStateOf(store.favorites)
        private set
    var resume by mutableStateOf(store.loadResume())
        private set
    var liveFormat by mutableStateOf(store.liveFormat)
    var videoScale by mutableStateOf(store.videoScale)
        private set
    var autoNext by mutableStateOf(store.autoNext)
        private set
    var autoNextSeconds by mutableStateOf(store.autoNextSeconds)
        private set
    var decoderMode by mutableStateOf(store.decoderMode)
        private set
    var bufferMode by mutableStateOf(store.bufferMode)
        private set

    // ---------- Controle parental ----------
    /** Catálogo completo (sem filtro). `catalog` é a versão exibida. */
    private var fullCatalog = Catalog()
    var parentalPin by mutableStateOf(store.parentalPin)
        private set
    /** true = conteúdo adulto liberado nesta sessão (volta a bloquear ao fechar o app). */
    var adultUnlocked by mutableStateOf(false)
        private set
    val parentalOn: Boolean get() = parentalPin.isNotEmpty()

    /** Próximo episódio aguardando a contagem regressiva do player. */
    var nextUp by mutableStateOf<PlayRequest?>(null)
        private set

    init {
        if (store.hasCredentials) connect(store.server, store.user, store.password)
    }

    // ---------------------------------------------------------------------
    // Atualização pelo GitHub
    // ---------------------------------------------------------------------
    var update by mutableStateOf<Updater.Info?>(null)
        private set
    var showUpdate by mutableStateOf(false)
    var updateChecking by mutableStateOf(false)
        private set
    /** -1 = não está baixando; 0..1 = progresso do download. */
    var updateProgress by mutableStateOf(-1f)
        private set
    var updateMessage by mutableStateOf<String?>(null)

    val appVersion: String get() = Updater.currentVersion(getApplication<Application>())

    // Precisa vir depois das propriedades acima (a ordem importa na inicialização).
    init {
        checkUpdate(manual = false)
    }

    fun checkUpdate(manual: Boolean) {
        if (updateChecking || updateProgress >= 0f) return
        viewModelScope.launch {
            updateChecking = true
            if (manual) updateMessage = null
            try {
                val info = Updater.check(getApplication<Application>())
                update = info
                if (info != null) {
                    showUpdate = true
                } else if (manual) {
                    updateMessage = "Você já está na versão mais recente ($appVersion)."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) updateMessage = "Não foi possível verificar agora. Confira a internet."
            } finally {
                updateChecking = false
            }
        }
    }

    fun downloadAndInstall() {
        val info = update ?: return
        if (updateProgress >= 0f) return
        val app = getApplication<Application>()
        if (!Updater.ensureInstallPermission(app)) {
            updateMessage = "Permita instalar apps desta origem na tela que abriu e toque em Atualizar de novo."
            return
        }
        viewModelScope.launch {
            updateProgress = 0f
            updateMessage = null
            try {
                val file = Updater.download(app, info) { updateProgress = it }
                updateProgress = -1f
                Updater.install(app, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateProgress = -1f
                updateMessage = "Falha ao baixar a atualização. Tente de novo."
            }
        }
    }

    // ---------------------------------------------------------------------
    // Login / catálogo
    // ---------------------------------------------------------------------

    fun connect(server: String, user: String, pass: String, keepOnError: Boolean = false) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            errorMessage = null
            try {
                // Tenta o servidor informado/salvo e depois os servidores padrão, em ordem.
                val candidates = (listOf(server) + Store.DEFAULT_SERVERS)
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (candidates.isEmpty()) throw IOException("Informe o endereço do servidor.")
                var found: XtreamApi? = null
                var foundAcc: AccountInfo? = null
                var lastError: Exception? = null
                for (candidate in candidates) {
                    try {
                        val c = XtreamApi(candidate, user, pass)
                        foundAcc = c.authenticate()
                        found = c
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                val client = found ?: throw (lastError ?: IOException("Não foi possível conectar."))
                val acc = foundAcc!!
                val cat = client.loadCatalog()
                if (cat.isEmpty) throw IOException("Nenhum conteúdo encontrado nesta conta.")
                fullCatalog = cat
                val shown = filteredCatalog(cat)
                val built = withContext(Dispatchers.Default) { buildHome(shown) }

                api = client
                account = acc
                catalog = shown
                home = built
                detailsCache.clear()
                store.saveLogin(client.baseUrl, user.trim(), pass)
                showLogin = false
                showSettings = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                errorMessage = "Catálogo grande demais para a memória deste aparelho."
                if (!(keepOnError && !catalog.isEmpty)) showLogin = true
            } catch (e: Exception) {
                errorMessage = friendly(e)
                if (!(keepOnError && !catalog.isEmpty)) showLogin = true
            } finally {
                loading = false
            }
        }
    }

    /** Recarrega o catálogo com a conta salva, mantendo o que já está na tela se falhar. */
    fun reload() {
        connect(store.server, store.user, store.password, keepOnError = true)
    }

    fun logout() {
        store.clearPassword()
        api = null
        account = null
        fullCatalog = Catalog()
        catalog = Catalog()
        home = HomeContent()
        adultUnlocked = false
        nextUp = null
        detailsCache.clear()
        detail = null
        playing = null
        showSettings = false
        errorMessage = null
        tab = NavTab.HOME
        showLogin = true
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Servidor não encontrado. Verifique o endereço e a conexão com a internet."
        is SocketTimeoutException -> "Tempo esgotado ao falar com o servidor."
        is ConnectException -> "Não foi possível conectar. Verifique o endereço e a porta."
        is MalformedURLException -> "Endereço do servidor inválido."
        is JSONException -> "Resposta inválida do servidor. Confira a URL e a porta."
        else -> e.message ?: "Falha ao conectar."
    }

    // ---------------------------------------------------------------------
    // Home estilo streaming
    // ---------------------------------------------------------------------

    private fun buildHome(c: Catalog): HomeContent {
        val recentMovies = c.movies.sortedByDescending { it.added }
        val recentSeries = c.series.sortedByDescending { it.added }

        val hero = (recentMovies.take(12) + recentSeries.take(12))
            .filter { !it.poster.isNullOrBlank() }
            .randomOrNull()

        val rows = ArrayList<HomeRow>()
        if (recentMovies.isNotEmpty()) rows.add(HomeRow("Filmes adicionados recentemente", recentMovies.take(30)))
        if (recentSeries.isNotEmpty()) rows.add(HomeRow("Séries adicionadas recentemente", recentSeries.take(30)))
        if (c.live.isNotEmpty()) rows.add(HomeRow("Canais ao vivo", c.live.take(30)))
        rows.addAll(categoryRows("Filmes", c.movies, c.movieCategories))
        rows.addAll(categoryRows("Séries", c.series, c.seriesCategories))
        return HomeContent(hero, rows)
    }

    private fun categoryRows(prefix: String, items: List<MediaEntry>, categories: List<String>): List<HomeRow> {
        val byCategory = items.groupBy { it.category }
        val rows = ArrayList<HomeRow>()
        for (name in categories.take(10)) {
            val list = byCategory[name] ?: continue
            rows.add(HomeRow("$prefix • $name", list.take(30)))
        }
        return rows
    }

    // ---------------------------------------------------------------------
    // Controle parental
    // ---------------------------------------------------------------------

    private fun isAdultName(name: String): Boolean {
        val n = name.searchNormalize()
        return ADULT_REGEX.containsMatchIn(n) || n.contains("+18") || n.contains("18+")
    }

    private fun filteredCatalog(c: Catalog): Catalog {
        if (!parentalOn || adultUnlocked) return c
        return Catalog(
            live = c.live.filter { !isAdultName(it.category) },
            movies = c.movies.filter { !isAdultName(it.category) },
            series = c.series.filter { !isAdultName(it.category) },
            liveCategories = c.liveCategories.filter { !isAdultName(it) },
            movieCategories = c.movieCategories.filter { !isAdultName(it) },
            seriesCategories = c.seriesCategories.filter { !isAdultName(it) },
        )
    }

    /** Reaplica o filtro ao catálogo já carregado (sem baixar nada de novo). */
    private fun applyParental() {
        if (fullCatalog.isEmpty) return
        viewModelScope.launch {
            val shown = filteredCatalog(fullCatalog)
            val built = withContext(Dispatchers.Default) { buildHome(shown) }
            catalog = shown
            home = built
            if (liveCategory?.let { it !in shown.liveCategories } == true) liveCategory = null
            if (movieCategory?.let { it !in shown.movieCategories } == true) movieCategory = null
            if (seriesCategory?.let { it !in shown.seriesCategories } == true) seriesCategory = null
        }
    }

    fun enableParental(pin: String) {
        store.parentalPin = pin
        parentalPin = pin
        adultUnlocked = false
        applyParental()
    }

    fun clearParental() {
        store.parentalPin = ""
        parentalPin = ""
        adultUnlocked = true
        applyParental()
    }

    fun checkPin(pin: String): Boolean = parentalOn && pin == parentalPin

    fun unlockAdult() {
        adultUnlocked = true
        applyParental()
    }

    fun lockAdult() {
        adultUnlocked = false
        applyParental()
    }

    // ---------------------------------------------------------------------
    // Favoritos
    // ---------------------------------------------------------------------

    fun toggleFavorite(entry: MediaEntry) {
        favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
        store.favorites = favorites
    }

    // ---------------------------------------------------------------------
    // Detalhes (cache em memória)
    // ---------------------------------------------------------------------

    suspend fun details(entry: MediaEntry): Details? {
        detailsCache[entry.id]?.let { return it }
        val client = api ?: return null
        val result = when (entry.type) {
            MediaType.MOVIE -> client.vodDetails(entry.streamId)
            MediaType.SERIES -> client.seriesDetails(entry.streamId)
            MediaType.LIVE -> null
        }
        if (result != null) detailsCache[entry.id] = result
        return result
    }

    suspend fun shortEpg(streamId: String, limit: Int = 6): List<EpgProgram> =
        api?.shortEpg(streamId, limit) ?: emptyList()

    // ---------------------------------------------------------------------
    // Abrir / reproduzir
    // ---------------------------------------------------------------------

    /** Canal: toca direto. Filme/série: abre a tela de detalhes. */
    fun open(entry: MediaEntry) {
        if (entry.type == MediaType.LIVE) {
            // Guarda a lista que está na tela (categoria / favoritos / busca) para trocar de canal sem voltar.
            val shown = currentLiveList()
            zapList = if (shown.any { it.id == entry.id }) shown
            else catalog.live.filter { it.category == entry.category }
            playEntry(entry)
        } else {
            detail = entry
        }
    }

    /** Lista de canais ao vivo exatamente como aparece na aba Ao vivo. */
    private fun currentLiveList(): List<MediaEntry> {
        val favIds = favorites
        val q = liveQuery.trim().searchNormalize()
        val base = if (liveFavOnly) catalog.live.filter { it.id in favIds } else catalog.live
        return when {
            q.isNotEmpty() -> base.filter { it.searchKey.contains(q) }.take(600)
            liveFavOnly -> base
            liveCategory == null -> catalog.live
            else -> catalog.live.filter { it.category == liveCategory }
        }
    }

    private var zapList: List<MediaEntry> = emptyList()

    /** Avisa só uma vez por sessão como trocar de canal. */
    var zapHintShown by mutableStateOf(false)

    /** Passa para o canal seguinte (+1) ou anterior (-1) da lista, em círculo. */
    fun zapChannel(delta: Int) {
        val current = playing ?: return
        if (current.kind != PlayKind.LIVE) return
        val list = zapList
        if (list.size < 2) return
        val index = list.indexOfFirst { it.streamId == current.refId }
        if (index < 0) return
        val next = list[((index + delta) % list.size + list.size) % list.size]
        playEntry(next)
    }

    /** Botão "Assistir" do destaque: filme toca direto, série abre detalhes. */
    fun playOrOpen(entry: MediaEntry) {
        if (entry.type == MediaType.MOVIE) playEntry(entry) else open(entry)
    }

    fun playEntry(entry: MediaEntry) {
        val request = requestFor(entry)
        if (request != null) playing = request
    }

    fun play(request: PlayRequest) {
        nextUp = null
        playing = request
    }

    fun playEpisode(series: MediaEntry, season: SeriesSeason, episode: SeriesEpisode) {
        nextUp = null
        playing = episodeRequest(series, season, episode)
    }

    fun stopPlayback() {
        nextUp = null
        playing = null
    }

    /** Contagem regressiva acabou (ou o usuário tocou em "Assistir agora"). */
    fun confirmNext() {
        val next = nextUp ?: return
        nextUp = null
        playing = next
    }

    fun cancelNext() {
        nextUp = null
        playing = null
    }

    fun updateVideoScale(value: Int) {
        videoScale = value
        store.videoScale = value
    }

    fun updateAutoNext(value: Boolean) {
        autoNext = value
        store.autoNext = value
    }

    fun updateAutoNextSeconds(value: Int) {
        autoNextSeconds = value
        store.autoNextSeconds = value
    }

    fun updateDecoderMode(value: Int) {
        decoderMode = value
        store.decoderMode = value
    }

    fun updateBufferMode(value: Int) {
        bufferMode = value
        store.bufferMode = value
    }

    fun updateLiveFormat(value: String) {
        liveFormat = value
        store.liveFormat = value
    }

    fun urlFor(request: PlayRequest): String {
        val client = api ?: return ""
        return when (request.kind) {
            PlayKind.LIVE -> client.liveUrl(request.refId, request.extension)
            PlayKind.MOVIE -> client.movieUrl(request.refId, request.extension)
            PlayKind.EPISODE -> client.episodeUrl(request.refId, request.extension)
        }
    }

    private fun requestFor(entry: MediaEntry): PlayRequest? = when (entry.type) {
        MediaType.LIVE -> PlayRequest(
            key = "live:${entry.streamId}",
            kind = PlayKind.LIVE,
            refId = entry.streamId,
            extension = liveFormat,
            title = entry.name,
            subtitle = entry.category,
            image = entry.poster,
        )
        MediaType.MOVIE -> PlayRequest(
            key = entry.id,
            kind = PlayKind.MOVIE,
            refId = entry.streamId,
            extension = entry.extension.ifBlank { "mp4" },
            title = entry.name,
            subtitle = entry.year,
            image = entry.poster,
            startPositionMs = resumePosition(entry.id),
        )
        MediaType.SERIES -> null
    }

    private fun episodeRequest(series: MediaEntry, season: SeriesSeason, episode: SeriesEpisode): PlayRequest {
        val key = "episode:${episode.id}"
        return PlayRequest(
            key = key,
            kind = PlayKind.EPISODE,
            refId = episode.id,
            extension = episode.extension,
            title = series.name,
            subtitle = "T${season.number} E${episode.number} • ${episode.title}",
            image = episode.image ?: series.poster,
            seriesId = series.streamId,
            startPositionMs = resumePosition(key),
        )
    }

    // ---------------------------------------------------------------------
    // Continuar assistindo / próximo episódio
    // ---------------------------------------------------------------------

    private fun resumePosition(key: String): Long =
        resume.firstOrNull { it.request.key == key }?.positionMs ?: 0L

    fun saveProgress(request: PlayRequest, positionMs: Long, durationMs: Long) {
        // Alguns servidores IPTV não informam a duração (vem negativa): ainda assim guardamos o ponto.
        val duration = if (durationMs > 0L) durationMs else 0L
        if (request.kind == PlayKind.LIVE || positionMs < 5_000L) return
        if (duration > 0L && positionMs > duration * 0.95) {
            removeResume(request.key)
            return
        }
        val item = ResumeItem(
            request = request.copy(startPositionMs = 0L),
            positionMs = positionMs,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
        )
        resume = (listOf(item) + resume.filter { it.request.key != request.key }).take(30)
        store.saveResume(resume)
    }

    fun removeResume(key: String) {
        if (resume.none { it.request.key == key }) return
        resume = resume.filter { it.request.key != key }
        store.saveResume(resume)
    }

    /** Chamado pelo player quando o vídeo termina. */
    fun onPlaybackEnded(request: PlayRequest) {
        if (request.kind == PlayKind.LIVE) return
        removeResume(request.key)
        viewModelScope.launch {
            val next = if (request.kind == PlayKind.EPISODE && autoNext) nextEpisode(request) else null
            // Só mexe se o usuário ainda estiver nesse vídeo.
            if (playing?.key != request.key) return@launch
            if (next != null) nextUp = next else playing = null
        }
    }

    private suspend fun nextEpisode(request: PlayRequest): PlayRequest? {
        val seriesId = request.seriesId ?: return null
        val entry = catalog.series.firstOrNull { it.streamId == seriesId } ?: return null
        val info = details(entry) ?: return null
        val flat = ArrayList<Pair<SeriesSeason, SeriesEpisode>>()
        for (season in info.seasons) {
            for (episode in season.episodes) flat.add(season to episode)
        }
        val index = flat.indexOfFirst { it.second.id == request.refId }
        if (index < 0) return null
        val next = flat.getOrNull(index + 1) ?: return null
        return episodeRequest(entry, next.first, next.second).copy(startPositionMs = 0L)
    }
}
private val ADULT_REGEX = Regex("\\b(adultos?|adult|xxx|porn\\w*|er[o]tic\\w*|sexy|sexo|sex|hentai|playboy)\\b")

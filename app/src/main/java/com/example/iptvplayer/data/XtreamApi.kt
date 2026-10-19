package com.example.iptvplayer.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

const val USER_AGENT = "IPTVPlayerPro/4.0 (Android; Media3)"
private const val OTHER = "Outros"

/** Lê uma string do JSON tratando ausente / null / vazio como null. */
internal fun JSONObject.str(name: String): String? {
    if (isNull(name)) return null
    val s = optString(name).trim()
    return if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
}

/** Primeira URL http de um campo que pode ser array ("backdrop_path") ou string. */
private fun JSONObject.firstUrl(name: String): String? {
    val arr = optJSONArray(name)
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.startsWith("http")) return s
        }
        return null
    }
    return str(name)?.takeIf { it.startsWith("http") }
}

/**
 * Cliente da API Xtream Codes (player_api.php).
 * Use somente serviços e conteúdos para os quais você tenha autorização.
 */
class XtreamApi(server: String, user: String, pass: String) {

    val baseUrl: String = normalizeServer(server)
    private val encUser = Uri.encode(user.trim())
    private val encPass = Uri.encode(pass)

    // ---------- URLs de reprodução ----------

    fun liveUrl(streamId: String, ext: String) = "$baseUrl/live/$encUser/$encPass/$streamId.$ext"
    fun movieUrl(streamId: String, ext: String) = "$baseUrl/movie/$encUser/$encPass/$streamId.$ext"
    fun episodeUrl(episodeId: String, ext: String) = "$baseUrl/series/$encUser/$encPass/$episodeId.$ext"

    /** Fuso do servidor (vem do login). O catch-up pede o horário no fuso do painel. */
    @Volatile
    var serverTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    /** Catch-up (timeshift): .../timeshift/usuario/senha/MINUTOS/AAAA-MM-DD:HH-MM/ID.ts */
    fun timeshiftUrl(streamId: String, startMs: Long, minutes: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        fmt.timeZone = serverTimeZone
        return "$baseUrl/timeshift/$encUser/$encPass/$minutes/${fmt.format(java.util.Date(startMs))}/$streamId.ts"
    }

    // ---------- HTTP ----------

    private fun endpoint(action: String? = null, extra: String? = null): String = buildString {
        append(baseUrl).append("/player_api.php?username=").append(encUser)
        append("&password=").append(encPass)
        if (action != null) append("&action=").append(action)
        if (extra != null) append('&').append(extra)
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                throw IOException(
                    when (code) {
                        401, 403 -> "Acesso negado (HTTP $code). Verifique usuário e senha."
                        404 -> "Endereço não encontrado (HTTP 404). Verifique a URL e a porta."
                        else -> "Erro do servidor (HTTP $code)."
                    }
                )
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Endpoints de lista devem devolver um array; qualquer outra coisa vira lista vazia. */
    private fun array(action: String): JSONArray {
        val raw = httpGet(endpoint(action)).trim()
        return if (raw.startsWith("[")) JSONArray(raw) else JSONArray()
    }

    // ---------- Login ----------

    suspend fun authenticate(): AccountInfo = withContext(Dispatchers.IO) {
        val root = JSONObject(httpGet(endpoint()))
        val info = root.optJSONObject("user_info")
            ?: throw IOException("Resposta inválida do servidor. Confira a URL e a porta.")
        if (info.str("auth") != "1") throw IOException("Usuário ou senha inválidos.")
        root.optJSONObject("server_info")?.str("timezone")?.let { tz ->
            serverTimeZone = TimeZone.getTimeZone(tz)
        }
        val status = info.str("status") ?: "Active"
        if (status.equals("Expired", ignoreCase = true)) {
            throw IOException("Sua assinatura venceu. Toque em \"Assinatura vencida? Renovar\" abaixo.")
        }
        if (!status.equals("Active", ignoreCase = true)) throw IOException("Conta com status: $status.")
        AccountInfo(
            status = status,
            expiresAtSec = info.str("exp_date")?.toLongOrNull(),
            maxConnections = info.str("max_connections"),
        )
    }

    // ---------- Catálogo ----------

    suspend fun loadCatalog(): Catalog = withContext(Dispatchers.IO) {
        coroutineScope {
            val liveCatsD = async { runCatching { categories("get_live_categories") } }
            val vodCatsD = async { runCatching { categories("get_vod_categories") } }
            val serCatsD = async { runCatching { categories("get_series_categories") } }

            val liveD = async {
                runCatching { parseLive(array("get_live_streams"), liveCatsD.await().getOrDefault(emptyMap<String, String>())) }
            }
            val vodD = async {
                runCatching { parseVod(array("get_vod_streams"), vodCatsD.await().getOrDefault(emptyMap<String, String>())) }
            }
            val serD = async {
                runCatching { parseSeries(array("get_series"), serCatsD.await().getOrDefault(emptyMap<String, String>())) }
            }

            val liveR = liveD.await()
            val vodR = vodD.await()
            val serR = serD.await()
            if (liveR.isFailure && vodR.isFailure && serR.isFailure) {
                throw liveR.exceptionOrNull() ?: IOException("Falha ao carregar o catálogo.")
            }

            val live = liveR.getOrDefault(emptyList<MediaEntry>())
            val movies = vodR.getOrDefault(emptyList<MediaEntry>())
            val series = serR.getOrDefault(emptyList<MediaEntry>())

            Catalog(
                live = live,
                movies = movies,
                series = series,
                liveCategories = usedCategories(liveCatsD.await().getOrDefault(emptyMap<String, String>()), live),
                movieCategories = usedCategories(vodCatsD.await().getOrDefault(emptyMap<String, String>()), movies),
                seriesCategories = usedCategories(serCatsD.await().getOrDefault(emptyMap<String, String>()), series),
            )
        }
    }

    /** Categorias na ordem do servidor, sem as vazias. */
    private fun usedCategories(cats: Map<String, String>, items: List<MediaEntry>): List<String> {
        val used = HashSet<String>()
        for (item in items) used.add(item.category)
        return (cats.values + OTHER).distinct().filter { it in used }
    }

    private fun categories(action: String): Map<String, String> {
        val arr = array(action)
        val map = LinkedHashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("category_id") ?: continue
            map[id] = o.str("category_name") ?: OTHER
        }
        return map
    }

    private fun parseLive(arr: JSONArray, cats: Map<String, String>): List<MediaEntry> {
        val out = ArrayList<MediaEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id") ?: continue
            out.add(
                MediaEntry(
                    id = "live:$id",
                    streamId = id,
                    name = o.str("name") ?: "Canal $id",
                    type = MediaType.LIVE,
                    category = o.str("category_id")?.let { cats[it] } ?: OTHER,
                    poster = o.str("stream_icon"),
                    added = o.str("added")?.toLongOrNull() ?: 0L,
                    archiveDays = if (o.str("tv_archive") == "1") (o.str("tv_archive_duration")?.toIntOrNull() ?: 0) else 0,
                )
            )
        }
        return out
    }

    private fun parseVod(arr: JSONArray, cats: Map<String, String>): List<MediaEntry> {
        val out = ArrayList<MediaEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id") ?: continue
            out.add(
                MediaEntry(
                    id = "movie:$id",
                    streamId = id,
                    name = o.str("name") ?: "Filme $id",
                    type = MediaType.MOVIE,
                    category = o.str("category_id")?.let { cats[it] } ?: OTHER,
                    poster = o.str("stream_icon"),
                    year = o.str("year") ?: o.str("releasedate")?.take(4),
                    rating = o.str("rating"),
                    added = o.str("added")?.toLongOrNull() ?: 0L,
                    extension = o.str("container_extension") ?: "mp4",
                )
            )
        }
        return out
    }

    private fun parseSeries(arr: JSONArray, cats: Map<String, String>): List<MediaEntry> {
        val out = ArrayList<MediaEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("series_id") ?: continue
            out.add(
                MediaEntry(
                    id = "series:$id",
                    streamId = id,
                    name = o.str("name") ?: "Série $id",
                    type = MediaType.SERIES,
                    category = o.str("category_id")?.let { cats[it] } ?: OTHER,
                    poster = o.str("cover"),
                    plot = o.str("plot"),
                    year = (o.str("releaseDate") ?: o.str("release_date"))?.take(4),
                    rating = o.str("rating"),
                    added = o.str("last_modified")?.toLongOrNull() ?: 0L,
                )
            )
        }
        return out
    }

    // ---------- Detalhes ----------

    suspend fun vodDetails(streamId: String): Details? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(httpGet(endpoint("get_vod_info", "vod_id=${Uri.encode(streamId)}")))
            val info = root.optJSONObject("info")
            if (info == null) {
                null
            } else {
                Details(
                    plot = info.str("plot") ?: info.str("description"),
                    backdrop = info.firstUrl("backdrop_path"),
                    poster = info.str("movie_image")?.takeIf { it.startsWith("http") },
                    genre = info.str("genre"),
                    cast = info.str("cast") ?: info.str("actors"),
                    director = info.str("director"),
                    duration = info.str("duration"),
                    rating = info.str("rating"),
                    year = (info.str("releasedate") ?: info.str("release_date"))?.take(4),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun seriesDetails(seriesId: String): Details? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(httpGet(endpoint("get_series_info", "series_id=${Uri.encode(seriesId)}")))
            val info = root.optJSONObject("info")

            val names = HashMap<Int, String>()
            val seasonsArr = root.optJSONArray("seasons")
            if (seasonsArr != null) {
                for (i in 0 until seasonsArr.length()) {
                    val s = seasonsArr.optJSONObject(i) ?: continue
                    val n = s.optInt("season_number", -1)
                    val name = s.str("name")
                    if (name != null) names[n] = name
                }
            }

            val seasons = ArrayList<SeriesSeason>()
            val node = root.opt("episodes")
            if (node is JSONObject) {
                // Formato padrão: { "1": [ep, ep...], "2": [...] }
                val keys = node.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                for (k in keys) {
                    val arr = node.optJSONArray(k) ?: continue
                    val num = k.toIntOrNull() ?: (seasons.size + 1)
                    seasons.add(SeriesSeason(num, names[num] ?: "Temporada $num", parseEpisodes(arr)))
                }
            } else if (node is JSONArray) {
                // Alguns painéis devolvem [[ep, ep], [ep]]
                for (i in 0 until node.length()) {
                    val arr = node.optJSONArray(i) ?: continue
                    val num = i + 1
                    seasons.add(SeriesSeason(num, names[num] ?: "Temporada $num", parseEpisodes(arr)))
                }
            }

            Details(
                plot = info?.str("plot"),
                backdrop = info?.firstUrl("backdrop_path"),
                poster = info?.str("cover"),
                genre = info?.str("genre"),
                cast = info?.str("cast"),
                director = info?.str("director"),
                rating = info?.str("rating"),
                year = (info?.str("releaseDate") ?: info?.str("release_date"))?.take(4),
                seasons = seasons.filter { it.episodes.isNotEmpty() },
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEpisodes(arr: JSONArray): List<SeriesEpisode> {
        val list = ArrayList<SeriesEpisode>()
        for (j in 0 until arr.length()) {
            val e = arr.optJSONObject(j) ?: continue
            val id = e.str("id") ?: continue
            val info = e.optJSONObject("info")
            list.add(
                SeriesEpisode(
                    id = id,
                    number = e.optInt("episode_num", j + 1),
                    title = e.str("title") ?: "Episódio ${j + 1}",
                    plot = info?.str("plot"),
                    duration = info?.str("duration"),
                    image = info?.str("movie_image")?.takeIf { it.startsWith("http") },
                    extension = e.str("container_extension") ?: "mp4",
                )
            )
        }
        return list.sortedBy { it.number }
    }

    // ---------- EPG ----------

    /**
     * Programação curta de um canal ao vivo (get_short_epg).
     * O Xtream devolve título e descrição em Base64 dentro de "epg_listings".
     */
    suspend fun shortEpg(streamId: String, limit: Int = 6): List<EpgProgram> = withContext(Dispatchers.IO) {
        try {
            val raw = httpGet(endpoint("get_short_epg", "stream_id=${Uri.encode(streamId)}&limit=$limit"))
            val arr = JSONObject(raw).optJSONArray("epg_listings")
            val out = ArrayList<EpgProgram>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val start = timestampMs(o, "start_timestamp", "start") ?: continue
                    val end = timestampMs(o, "stop_timestamp", "end") ?: continue
                    out.add(EpgProgram(decodeText(o.str("title")), decodeText(o.str("description")), start, end))
                }
            }
            out.sortedBy { it.startMs }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Programação completa do canal, passado e futuro (get_simple_data_table).
     * Traz "has_archive" indicando se o painel guardou a gravação do programa.
     */
    suspend fun fullEpg(streamId: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        try {
            val raw = httpGet(endpoint("get_simple_data_table", "stream_id=${Uri.encode(streamId)}"))
            val arr = JSONObject(raw).optJSONArray("epg_listings")
            val out = ArrayList<EpgProgram>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val start = timestampMs(o, "start_timestamp", "start") ?: continue
                    val end = timestampMs(o, "stop_timestamp", "end") ?: continue
                    out.add(
                        EpgProgram(
                            decodeText(o.str("title")),
                            decodeText(o.str("description")),
                            start,
                            end,
                            hasArchive = o.str("has_archive")?.let { it == "1" } ?: true,
                        )
                    )
                }
            }
            out.sortedBy { it.startMs }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun timestampMs(o: JSONObject, tsKey: String, textKey: String): Long? {
        val ts = o.str(tsKey)?.toLongOrNull()
        if (ts != null) return ts * 1000L
        val text = o.str(textKey) ?: return null
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(text)?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Tenta Base64; se o resultado não for texto legível, devolve o original. */
    private fun decodeText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val text = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            val readable = text.isNotEmpty() &&
                text.none { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }
            if (readable) text.trim() else value
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    private companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return ""
            if (!s.contains("://")) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php")
            if (s.endsWith(":80", ignoreCase = true)) {
                s = s.substring(0, s.length - 3)
            }
            return s.trimEnd('/')
        }
    }
}

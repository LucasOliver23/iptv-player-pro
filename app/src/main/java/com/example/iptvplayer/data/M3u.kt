package com.example.iptvplayer.data

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de ler uma lista M3U: catálogo + endereço de cada item. */
class M3uResult(
    val catalog: Catalog,
    /** refId (id do canal/filme/episódio) -> endereço direto do vídeo. */
    val urls: Map<String, String>,
    /** Séries montadas a partir dos episódios (S01E02...). Chave = id da série. */
    val details: Map<String, Details>,
)

/**
 * Leitor de listas M3U / M3U8 (#EXTINF).
 * Canais ao vivo, filmes e séries são separados pelo endereço e pelo nome:
 *  - ".../movie/..."  ou arquivo de vídeo (.mp4/.mkv/.avi) sem "S01E02" no nome  -> filme
 *  - ".../series/..." ou nome com "S01E02"                                      -> episódio de série
 *  - o resto                                                                    -> canal ao vivo
 */
object M3u {

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val EPISODE = Regex("""^(.*?)[\s._\-]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,3})(.*)$""")
    private val VIDEO_FILE = Regex("""\.(mp4|mkv|avi|mov|wmv|flv)(\?.*)?$""", RegexOption.IGNORE_CASE)
    private const val OTHER = "Outros"

    /** Baixa e lê a lista. Chame fora da thread principal. */
    fun load(url: String): M3uResult {
        val clean = url.trim()
        if (!clean.startsWith("http://", true) && !clean.startsWith("https://", true)) {
            throw IOException("Endereço da lista inválido. Use um link começando com http:// ou https://")
        }
        val c = URL(clean).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 90_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                throw IOException(
                    when (code) {
                        401, 403 -> "Acesso negado à lista (HTTP $code). Confira o link."
                        404 -> "Lista não encontrada (HTTP 404). Confira o link."
                        else -> "Erro do servidor (HTTP $code)."
                    }
                )
            }
            return c.inputStream.bufferedReader().use { parse(it) }
        } finally {
            c.disconnect()
        }
    }

    private class Raw(val name: String, val group: String, val logo: String?, val url: String)

    fun parse(reader: BufferedReader): M3uResult {
        val raws = ArrayList<Raw>()
        var name: String? = null
        var group = OTHER
        var logo: String? = null
        var sawHeader = false

        while (true) {
            val line = (reader.readLine() ?: break).trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U")) { sawHeader = true; continue }
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                sawHeader = true
                val attrs = HashMap<String, String>()
                for (m in ATTR.findAll(line)) attrs[m.groupValues[1].lowercase()] = m.groupValues[2]
                val rest = ATTR.replace(line, "")
                val title = rest.substringAfter(',', "").trim()
                name = title.ifEmpty { attrs["tvg-name"] ?: "" }
                group = attrs["group-title"]?.trim()?.ifEmpty { null } ?: OTHER
                logo = attrs["tvg-logo"]?.trim()?.takeIf { it.startsWith("http") }
                continue
            }
            if (line.startsWith("#")) continue
            // Linha de endereço
            if (line.startsWith("http", ignoreCase = true) || line.startsWith("rtmp", ignoreCase = true) ||
                line.startsWith("rtsp", ignoreCase = true) || line.startsWith("udp", ignoreCase = true)
            ) {
                raws.add(Raw(name?.ifBlank { null } ?: line.substringAfterLast('/').substringBefore('?'), group, logo, line))
            }
            name = null; group = OTHER; logo = null
        }
        if (!sawHeader || raws.isEmpty()) throw IOException("Nenhum canal encontrado nessa lista. Confira se o link é de uma lista M3U.")

        val live = ArrayList<MediaEntry>()
        val movies = ArrayList<MediaEntry>()
        val urls = HashMap<String, String>(raws.size * 2)
        val liveCats = LinkedHashSet<String>()
        val movieCats = LinkedHashSet<String>()
        val seriesCats = LinkedHashSet<String>()

        class Ep(val season: Int, val number: Int, val title: String, val id: String, val ext: String, val logo: String?)
        class Ser(val name: String, val category: String, var logo: String?) { val episodes = ArrayList<Ep>() }
        val seriesByName = LinkedHashMap<String, Ser>()
        val total = raws.size

        for ((index, r) in raws.withIndex()) {
            val lowerUrl = r.url.lowercase()
            val ep = EPISODE.find(r.name)
            val isSeriesUrl = "/series/" in lowerUrl
            val isMovieUrl = "/movie/" in lowerUrl
            val isFile = VIDEO_FILE.containsMatchIn(r.url)

            when {
                (isSeriesUrl || ((isFile || r.group.contains("serie", true)) && ep != null)) && ep != null -> {
                    val serName = ep.groupValues[1].trim().trim('-', '.', '_', ' ').ifEmpty { r.name }
                    val key = serName.searchNormalize()
                    val ser = seriesByName.getOrPut(key) { Ser(serName, r.group, r.logo) }
                    if (ser.logo == null) ser.logo = r.logo
                    val season = ep.groupValues[2].toInt()
                    val number = ep.groupValues[3].toInt()
                    val tail = ep.groupValues[4].trim().trim('-', '.', '_', ' ')
                    val id = stableId("e", r.url, urls)
                    urls[id] = r.url
                    ser.episodes.add(
                        Ep(season, number, tail.ifEmpty { "Episódio $number" }, id, extensionOf(r.url), r.logo)
                    )
                }
                isMovieUrl || isFile -> {
                    val id = stableId("m", r.url, urls)
                    urls[id] = r.url
                    movieCats.add(r.group)
                    movies.add(
                        MediaEntry(
                            id = "movie:$id", streamId = id, name = r.name, type = MediaType.MOVIE,
                            category = r.group, poster = r.logo, added = (total - index).toLong(),
                            extension = extensionOf(r.url),
                        )
                    )
                }
                else -> {
                    val id = stableId("l", r.url, urls)
                    urls[id] = r.url
                    liveCats.add(r.group)
                    live.add(
                        MediaEntry(
                            id = "live:$id", streamId = id, name = r.name, type = MediaType.LIVE,
                            category = r.group, poster = r.logo, added = (total - index).toLong(),
                        )
                    )
                }
            }
        }

        val series = ArrayList<MediaEntry>()
        val details = HashMap<String, Details>()
        var n = 0
        for (ser in seriesByName.values) {
            n++
            val sid = "s$n"
            seriesCats.add(ser.category)
            series.add(
                MediaEntry(
                    id = "series:$sid", streamId = sid, name = ser.name, type = MediaType.SERIES,
                    category = ser.category, poster = ser.logo, added = (total - n).toLong(),
                )
            )
            val seasons = ser.episodes.groupBy { it.season }.toSortedMap().map { (num, list) ->
                SeriesSeason(
                    number = num,
                    name = "Temporada $num",
                    episodes = list.sortedBy { it.number }.map {
                        SeriesEpisode(
                            id = it.id, number = it.number, title = it.title, plot = null,
                            duration = null, image = it.logo, extension = it.ext,
                        )
                    },
                )
            }
            details[sid] = Details(poster = ser.logo, seasons = seasons)
        }

        val catalog = Catalog(
            live = live, movies = movies, series = series,
            liveCategories = liveCats.toList(), movieCategories = movieCats.toList(),
            seriesCategories = seriesCats.toList(),
        )
        return M3uResult(catalog, urls, details)
    }

    private fun extensionOf(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "")
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext.lowercase() else "mp4"
    }

    /** Id estável (não muda se a lista for reordenada), para "continuar assistindo" funcionar. */
    private fun stableId(prefix: String, url: String, used: Map<String, String>): String {
        var h = url.hashCode().toUInt().toString(16)
        var id = "m$prefix$h"
        var i = 0
        while (used.containsKey(id) && used[id] != url) {
            i++
            id = "m$prefix$h$i"
        }
        return id
    }
}

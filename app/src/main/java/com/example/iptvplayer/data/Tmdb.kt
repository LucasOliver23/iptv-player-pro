package com.example.iptvplayer.data

import android.net.Uri
import com.example.iptvplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CastMember(val name: String, val character: String?, val photo: String?)

data class TmdbInfo(
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val rating: String? = null,
    val cast: List<CastMember> = emptyList(),
    /** Id do vídeo no YouTube. */
    val trailerKey: String? = null,
)

/**
 * Capa em alta resolução, trailer e elenco vindos do TMDB (themoviedb.org).
 * O TMDB traz o elenco original com os personagens; não existe lista de dubladores brasileiros.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p"

    val enabled: Boolean get() = BuildConfig.TMDB_KEY.isNotBlank()

    private val NOISE = Regex(
        "\\[[^\\]]*\\]|\\([^)]*\\)|\\b(4k|uhd|fhd|hd|sd|1080p|720p|2160p|dublado|dual|legendado|leg|dub|nacional|web-?dl|bluray|h\\.?26[45])\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun cleanTitle(name: String): String =
        name.replace(NOISE, " ")
            .replace(Regex("^[A-Za-z]{2,3}\\s*[-|:]\\s*"), "") // prefixo "BR - ", "PT | "
            .replace(Regex("[\\s._\\-|]+"), " ")
            .trim()

    private fun get(url: String): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        return try {
            if (c.responseCode !in 200..299) null
            else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    suspend fun lookup(entry: MediaEntry): TmdbInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        try {
            val key = BuildConfig.TMDB_KEY
            val kind = if (entry.type == MediaType.SERIES) "tv" else "movie"
            val title = cleanTitle(entry.name)
            if (title.isBlank()) return@withContext null
            val yearParam = entry.year?.takeIf { it.length == 4 }?.let {
                if (kind == "tv") "&first_air_date_year=$it" else "&year=$it"
            } ?: ""
            fun search(withYear: Boolean): Int? {
                val r = get(
                    "$API/search/$kind?api_key=$key&language=pt-BR&query=${Uri.encode(title)}" +
                        if (withYear) yearParam else ""
                ) ?: return null
                val results = r.optJSONArray("results") ?: return null
                return if (results.length() > 0) results.optJSONObject(0)?.optInt("id") else null
            }
            val id = search(true) ?: (if (yearParam.isNotEmpty()) search(false) else null)
                ?: return@withContext null

            val d = get(
                "$API/$kind/$id?api_key=$key&language=pt-BR&append_to_response=credits,videos" +
                    "&include_video_language=pt,en,null"
            ) ?: return@withContext null

            val cast = ArrayList<CastMember>()
            val castArr = d.optJSONObject("credits")?.optJSONArray("cast")
            if (castArr != null) {
                for (i in 0 until minOf(castArr.length(), 14)) {
                    val o = castArr.optJSONObject(i) ?: continue
                    val name = o.str("name") ?: continue
                    cast.add(
                        CastMember(
                            name,
                            o.str("character"),
                            o.str("profile_path")?.let { "$IMG/w185$it" },
                        )
                    )
                }
            }

            var trailer: String? = null
            val vids = d.optJSONObject("videos")?.optJSONArray("results")
            if (vids != null) {
                val list = (0 until vids.length()).mapNotNull { vids.optJSONObject(it) }
                    .filter { it.optString("site") == "YouTube" }
                val best = list.firstOrNull { it.optString("type") == "Trailer" && it.optString("iso_639_1") == "pt" }
                    ?: list.firstOrNull { it.optString("type") == "Trailer" }
                    ?: list.firstOrNull()
                trailer = best?.str("key")
            }

            val vote = d.optDouble("vote_average", 0.0)
            TmdbInfo(
                poster = d.str("poster_path")?.let { "$IMG/w500$it" },
                backdrop = d.str("backdrop_path")?.let { "$IMG/w1280$it" },
                overview = d.str("overview"),
                rating = if (vote > 0) String.format(java.util.Locale.US, "%.1f", vote) else null,
                cast = cast,
                trailerKey = trailer,
            )
        } catch (e: Exception) {
            null
        }
    }
}

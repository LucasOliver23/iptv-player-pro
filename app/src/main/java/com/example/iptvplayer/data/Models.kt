package com.example.iptvplayer.data

import java.text.Normalizer

enum class MediaType { LIVE, MOVIE, SERIES }

enum class PlayKind { LIVE, MOVIE, EPISODE, CATCHUP }

private val DIACRITICS = Regex("\\p{Mn}+")

/** Minúsculas e sem acentos, para a busca ("acao" encontra "Ação"). */
fun String.searchNormalize(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

/**
 * Item do catálogo (canal, filme ou série).
 * A URL de reprodução NÃO fica aqui: é montada só na hora de assistir (XtreamApi.*Url),
 * o que economiza memória e evita guardar usuário/senha em cada item.
 */
data class MediaEntry(
    val id: String,          // "live:123", "movie:456", "series:789"
    val streamId: String,    // id numérico do servidor
    val name: String,
    val type: MediaType,
    val category: String,
    val poster: String? = null,
    val plot: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val added: Long = 0L,
    val extension: String = "",
    /** Dias de arquivo (catch-up) do canal; 0 = sem arquivo. */
    val archiveDays: Int = 0,
) {
    val searchKey: String = name.searchNormalize()
}

data class Catalog(
    val live: List<MediaEntry> = emptyList(),
    val movies: List<MediaEntry> = emptyList(),
    val series: List<MediaEntry> = emptyList(),
    val liveCategories: List<String> = emptyList(),
    val movieCategories: List<String> = emptyList(),
    val seriesCategories: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()

    val all: List<MediaEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) { live + movies + series }
}

data class SeriesEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val plot: String?,
    val duration: String?,
    val image: String?,
    val extension: String,
)

data class SeriesSeason(
    val number: Int,
    val name: String,
    val episodes: List<SeriesEpisode>,
)

/** Detalhes de um filme ou série (get_vod_info / get_series_info). */
data class Details(
    val plot: String? = null,
    val backdrop: String? = null,
    val poster: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val seasons: List<SeriesSeason> = emptyList(),
)

data class EpgProgram(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
    /** O painel guarda a gravação deste programa (catch-up). */
    val hasArchive: Boolean = false,
)

data class AccountInfo(
    val status: String,
    val expiresAtSec: Long?,
    val maxConnections: String?,
)

/** Pedido de reprodução. A URL é resolvida no momento de tocar. */
data class PlayRequest(
    val key: String,              // "live:1", "movie:2", "episode:3"
    val kind: PlayKind,
    val refId: String,            // stream_id (live/filme) ou id do episódio
    val extension: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val seriesId: String? = null, // preenchido só para episódios
    val startPositionMs: Long = 0L,
    val catchupStartMs: Long = 0L, // catch-up: início do programa
    val catchupMinutes: Int = 0,   // catch-up: duração em minutos
)

/** Item de "Continuar assistindo". */
data class ResumeItem(
    val request: PlayRequest,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class HomeRow(val title: String, val items: List<MediaEntry>)

data class HomeContent(
    val hero: MediaEntry? = null,
    val rows: List<HomeRow> = emptyList(),
)

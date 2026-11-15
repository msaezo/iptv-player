package es.msaezo.iptv.data

enum class ContentType { LIVE, MOVIE, SERIES }

/** One playable stream from the playlist (for movies/series: one *version*). */
data class Entry(
    val title: String,
    val rawName: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
    val type: ContentType,
    val year: Int? = null,
    val showName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val language: String = Tags.UNKNOWN,
    val quality: String? = null,
    val rating: Double? = null,
    /** Pre-computed key used to merge versions of the same movie / show. */
    val key: String = "",
    /** When the provider added it (epoch seconds, 0 = unknown). */
    val added: Long = 0,
    val userAgent: String? = null,
    val referrer: String? = null,
) {
    val id: String get() = url

    val episodeLabel: String?
        get() = if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null

    /** "ES · 4K" */
    val versionLabel: String
        get() = listOfNotNull(Tags.shortLabel(language), quality).joinToString(" · ")
}

/** A movie title merging every version (language / quality) found in the playlist. */
data class Movie(
    val id: String,
    val title: String,
    val year: Int?,
    val poster: String?,
    val versions: List<Entry>,
) {
    val groups: Set<String> by lazy { versions.mapTo(LinkedHashSet()) { it.group } }
    val languages: List<String> by lazy { versions.map { it.language }.distinct() }
    val rating: Double? by lazy { versions.mapNotNull { it.rating }.maxOrNull() }
    val added: Long by lazy { versions.maxOf { it.added } }
}

/** One episode, possibly available in several versions. */
data class Episode(
    val season: Int,
    val number: Int?,
    val title: String,
    val versions: List<Entry>,
) {
    val label: String get() = if (number != null) "S%02dE%02d".format(season, number) else title
}

/** A TV show merging episodes from every category/language it appears in. */
data class Show(
    val id: String,
    val name: String,
    val poster: String?,
    val episodes: List<Episode>,
    /** Xtream series behind this show; episodes are loaded on demand when non-empty. */
    val sources: List<SeriesSource> = emptyList(),
) {
    /** True while episodes still have to be fetched from the server. */
    val needsLoading: Boolean get() = episodes.isEmpty() && sources.isNotEmpty()
    val seasons: List<Int> by lazy { episodes.map { it.season }.distinct().sorted() }
    val groups: Set<String> by lazy {
        episodes.flatMapTo(LinkedHashSet()) { ep -> ep.versions.map { it.group } } + sources.map { it.group }
    }
    val languages: List<String> by lazy {
        (episodes.flatMap { ep -> ep.versions.map { it.language } } + sources.map { it.language }).distinct()
    }
    val rating: Double? by lazy { sources.mapNotNull { it.rating }.maxOrNull() }
    val year: Int? by lazy { sources.firstNotNullOfOrNull { it.year } }
    val added: Long by lazy { sources.maxOfOrNull { it.added } ?: 0L }
    fun episodesOf(season: Int): List<Episode> = episodes.filter { it.season == season }
}

data class Category<T>(val name: String, val items: List<T>)

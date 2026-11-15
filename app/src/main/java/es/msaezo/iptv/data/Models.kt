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
) {
    val seasons: List<Int> by lazy { episodes.map { it.season }.distinct().sorted() }
    val groups: Set<String> by lazy {
        episodes.flatMapTo(LinkedHashSet()) { ep -> ep.versions.map { it.group } }
    }
    val languages: List<String> by lazy {
        episodes.flatMap { ep -> ep.versions.map { it.language } }.distinct()
    }
    fun episodesOf(season: Int): List<Episode> = episodes.filter { it.season == season }
}

data class Category<T>(val name: String, val items: List<T>)

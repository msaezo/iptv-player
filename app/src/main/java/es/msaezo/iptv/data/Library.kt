package es.msaezo.iptv.data

import java.text.Normalizer

/** What the user wants to see. */
data class LibraryFilter(
    /** Languages shown for movies & series (codes from [Tags.languageLabels]). */
    val languages: Set<String> = DEFAULT_LANGUAGES,
    /** Live TV regions listed first, in this order. */
    val liveRegionOrder: List<String> = listOf("ES", "UK", "US"),
) {
    companion object {
        val DEFAULT_LANGUAGES = setOf("es", "en", "multi", "vose", Tags.UNKNOWN)
    }
}

/**
 * The whole playlist, parsed and merged:
 * - live channels as they come,
 * - movie versions merged into [Movie] titles,
 * - episodes merged into [Show]s with [Episode]s holding every version.
 */
class Library(
    entries: List<Entry>,
    val epgUrl: String? = null,
    seriesSources: List<SeriesSource> = emptyList(),
) {

    val live: List<Entry> = entries.filter { it.type == ContentType.LIVE }
    val movies: List<Movie>
    val shows: List<Show>

    /** How many movie/series streams exist per language (for the Settings checklist). */
    val languageCounts: Map<String, Int>

    val totalCount = entries.size + seriesSources.size

    init {
        val movieEntries = entries.filter { it.type == ContentType.MOVIE }
        val episodeEntries = entries.filter { it.type == ContentType.SERIES }
        movies = mergeMovies(movieEntries)
        shows = mergeShows(episodeEntries) + mergeSeriesSources(seriesSources)
        languageCounts = ((movieEntries + episodeEntries).map { it.language } + seriesSources.map { it.language })
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun view(filter: LibraryFilter): LibraryView = LibraryView(this, filter)

    companion object {
        private val marks = Regex("\\p{Mn}+")
        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()

        fun <T> groupInOrder(items: List<T>, key: (T) -> String): List<Category<T>> {
            val map = LinkedHashMap<String, MutableList<T>>()
            for (i in items) map.getOrPut(key(i)) { ArrayList() }.add(i)
            return map.map { Category(it.key, it.value) }
        }

        fun mergeMovies(entries: List<Entry>): List<Movie> {
            val byTitle = LinkedHashMap<String, MutableList<Entry>>()
            for (e in entries) byTitle.getOrPut(e.key.ifEmpty { Tags.mergeKey(e.title) }) { ArrayList() }.add(e)

            val out = ArrayList<Movie>(byTitle.size)
            for ((key, versions) in byTitle) {
                // Same title but different years = different films (remakes).
                val years = versions.mapNotNull { it.year }.distinct()
                val clusters: Map<Int?, List<Entry>> = if (years.size <= 1) {
                    mapOf(years.firstOrNull() to versions)
                } else {
                    val withYear = versions.filter { it.year != null }.groupBy { it.year }
                    val biggest = withYear.maxByOrNull { it.value.size }!!.key
                    val noYear = versions.filter { it.year == null }
                    withYear.mapValues { (y, list) -> if (y == biggest) list + noYear else list }
                }
                for ((year, list) in clusters) {
                    val sorted = sortVersions(list)
                    out += Movie(
                        id = "$key|${year ?: ""}",
                        title = list.first().title,
                        year = year,
                        poster = list.firstNotNullOfOrNull { it.logo },
                        versions = sorted,
                    )
                }
            }
            return out
        }

        fun mergeShows(entries: List<Entry>): List<Show> {
            val byShow = LinkedHashMap<String, MutableList<IndexedValue<Entry>>>()
            entries.forEachIndexed { i, e ->
                val key = e.key.ifEmpty { Tags.mergeKey(e.showName ?: e.title) }
                byShow.getOrPut(key) { ArrayList() }.add(IndexedValue(i, e))
            }
            return byShow.map { (key, list) ->
                val episodes = LinkedHashMap<String, MutableList<IndexedValue<Entry>>>()
                for (iv in list) {
                    val e = iv.value
                    val epKey = if (e.episode != null) "${e.season ?: 1}x${e.episode}" else "u:${e.url}"
                    episodes.getOrPut(epKey) { ArrayList() }.add(iv)
                }
                val eps = episodes.values.map { vs ->
                    val first = vs.first().value
                    vs.first().index to Episode(
                        season = first.season ?: 1,
                        number = first.episode,
                        title = vs.map { it.value.title }.firstOrNull { !it.startsWith("Episode ") }
                            ?: first.title,
                        versions = sortVersions(vs.map { it.value }),
                    )
                }.sortedWith(
                    compareBy({ it.second.season }, { it.second.number ?: Int.MAX_VALUE }, { it.first })
                ).map { it.second }
                Show(
                    id = key,
                    name = list.first().value.showName ?: list.first().value.title,
                    poster = list.firstNotNullOfOrNull { it.value.logo },
                    episodes = eps,
                )
            }
        }

        /** Xtream series: one lazy [Show] per title, merging language versions. */
        fun mergeSeriesSources(sources: List<SeriesSource>): List<Show> {
            val byKey = LinkedHashMap<String, MutableList<SeriesSource>>()
            for (s in sources) byKey.getOrPut(Tags.mergeKey(s.title)) { ArrayList() }.add(s)
            return byKey.map { (key, list) ->
                val sorted = list.sortedBy { langRank(it.language) }
                Show(
                    id = "x:$key",
                    name = sorted.first().title,
                    poster = sorted.firstNotNullOfOrNull { it.cover },
                    episodes = emptyList(),
                    sources = sorted,
                )
            }
        }

        /** Preferred order for picking a version: language preference, then quality. */
        val languagePreference = listOf("es", "multi", "en", "vose", "lat")

        fun langRank(lang: String): Int =
            languagePreference.indexOf(lang).let { if (it < 0) languagePreference.size else it }

        fun sortVersions(list: List<Entry>): List<Entry> =
            list.sortedWith(compareBy({ langRank(it.language) }, { -Tags.qualityRank(it.quality) }))

        /** Best version, optionally constrained to a language. */
        fun bestVersion(versions: List<Entry>, preferredLanguage: String? = null): Entry? {
            if (preferredLanguage != null) {
                versions.firstOrNull { it.language == preferredLanguage }?.let { return it }
            }
            return versions.firstOrNull()
        }
    }
}

/** A filtered, sorted view of a [Library] ready for the UI. */
class LibraryView(val library: Library, val filter: LibraryFilter) {

    val liveCategories: List<Category<Entry>> = run {
        val cats = Library.groupInOrder(library.live) { it.group }
        val order = filter.liveRegionOrder
        cats.withIndex().sortedWith(compareBy({
            val r = Tags.regionOfLiveGroup(it.value.name)
            val idx = if (r == null) -1 else order.indexOf(r)
            if (idx < 0) order.size else idx
        }, { it.index })).map { it.value }
    }

    /** Live channels in display order (used for zapping). */
    val live: List<Entry> = liveCategories.flatMap { it.items }

    val movies: List<Movie> = library.movies.mapNotNull { m ->
        val vs = m.versions.filter { it.language in filter.languages }
        when {
            vs.isEmpty() -> null
            vs.size == m.versions.size -> m
            else -> m.copy(versions = vs, poster = m.poster ?: vs.firstNotNullOfOrNull { it.logo })
        }
    }

    val shows: List<Show> = library.shows.mapNotNull { s ->
        if (s.sources.isNotEmpty()) {
            val src = s.sources.filter { it.language in filter.languages }
            return@mapNotNull when {
                src.isEmpty() -> null
                src.size == s.sources.size -> s
                else -> s.copy(sources = src, poster = src.firstNotNullOfOrNull { it.cover } ?: s.poster)
            }
        }
        val eps = s.episodes.mapNotNull { ep ->
            val vs = ep.versions.filter { it.language in filter.languages }
            when {
                vs.isEmpty() -> null
                vs.size == ep.versions.size -> ep
                else -> ep.copy(versions = vs)
            }
        }
        if (eps.isEmpty()) null else if (eps.size == s.episodes.size &&
            eps.zip(s.episodes).all { it.first === it.second }) s else s.copy(episodes = eps)
    }

    val movieCategories: List<Category<Movie>> = categoriesOf(movies) { it.versions.map { v -> v.group } }
    val showCategories: List<Category<Show>> =
        categoriesOf(shows) { s -> s.episodes.flatMap { ep -> ep.versions.map { it.group } } + s.sources.map { it.group } }

    // --- smart rows (worked out the first time a screen asks for them) -------------------

    val movieGenres: List<Category<Movie>> by lazy {
        byGenre(movies) { m -> m.versions.map { it.group } }
    }
    val showGenres: List<Category<Show>> by lazy {
        byGenre(shows) { s -> s.sources.map { it.group } + s.episodes.flatMap { ep -> ep.versions.map { it.group } } }
    }
    val recentMovies: List<Movie> by lazy {
        movies.filter { it.added > 0 }.sortedByDescending { it.added }.take(60)
    }
    val recentShows: List<Show> by lazy {
        shows.filter { it.added > 0 }.sortedByDescending { it.added }.take(60)
    }
    val thisYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val newMovies: List<Movie> by lazy {
        movies.filter { it.year == thisYear }.sortedByDescending { it.rating ?: 0.0 }.take(60)
    }
    val topRatedMovies: List<Movie> by lazy {
        movies.filter { val r = it.rating; r != null && r >= 7.0 && r <= 9.4 && it.poster != null }
            .sortedByDescending { it.rating }.take(60)
    }
    val topRatedShows: List<Show> by lazy {
        shows.filter { val r = it.rating; r != null && r >= 7.0 && r <= 9.4 && it.poster != null }
            .sortedByDescending { it.rating }.take(60)
    }

    private fun <T> byGenre(items: List<T>, groups: (T) -> List<String>): List<Category<T>> {
        val map = HashMap<String, MutableList<T>>()
        for (item in items) {
            var last: String? = null
            for (g in groups(item)) {
                val genre = Tags.genreOfGroup(g) ?: continue
                if (genre == last) continue
                val list = map.getOrPut(genre) { ArrayList() }
                if (list.lastOrNull() !== item) list.add(item)
                last = genre
            }
        }
        return Tags.genreOrder.mapNotNull { g -> map[g]?.let { Category<T>(g, it) } }
    }

    // Only needed for "Continue watching": built the first time it is asked for.
    private val movieByUrl by lazy {
        HashMap<String, Movie>().apply { for (m in movies) for (v in m.versions) put(v.url, m) }
    }
    private val showByUrl by lazy {
        HashMap<String, Show>().apply { for (s in shows) for (ep in s.episodes) for (v in ep.versions) put(v.url, s) }
    }

    fun movieFor(url: String): Movie? = movieByUrl[url]
    fun showFor(url: String): Show? = showByUrl[url]
    fun movieById(id: String): Movie? = movies.firstOrNull { it.id == id }
    fun showById(id: String): Show? = shows.firstOrNull { it.id == id }

    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && shows.isEmpty()

    private fun <T> categoriesOf(items: List<T>, groups: (T) -> List<String>): List<Category<T>> {
        val map = LinkedHashMap<String, MutableList<T>>()
        for (item in items) {
            for (g in groups(item).distinct()) map.getOrPut(g) { ArrayList() }.add(item)
        }
        val cats = map.map { Category<T>(it.key, it.value) }
        return if (cats.size > 1) listOf(Category(ALL, items)) + cats else cats
    }

    // --- search -----------------------------------------------------------------------

    private val liveKeys by lazy { live.map { Library.normalize(it.title) } }
    private val movieKeys by lazy { movies.map { Library.normalize(it.title) } }
    private val showKeys by lazy { shows.map { Library.normalize(it.name) } }

    data class SearchResults(val live: List<Entry>, val movies: List<Movie>, val shows: List<Show>) {
        val isEmpty get() = live.isEmpty() && movies.isEmpty() && shows.isEmpty()
    }

    fun search(query: String, limit: Int = 60): SearchResults {
        val q = Library.normalize(query).trim()
        if (q.length < 2) return SearchResults(emptyList(), emptyList(), emptyList())
        val terms = q.split(' ').filter { it.isNotBlank() }
        val m: (String) -> Boolean = { key -> terms.all { key.contains(it) } }
        return SearchResults(pick(live, liveKeys, limit, m), pick(movies, movieKeys, limit, m),
            pick(shows, showKeys, limit, m))
    }

    private fun <T> pick(items: List<T>, keys: List<String>, limit: Int, m: (String) -> Boolean): List<T> {
        val out = ArrayList<T>()
        for (i in items.indices) {
            if (m(keys[i])) {
                out += items[i]
                if (out.size >= limit) break
            }
        }
        return out
    }

    companion object {
        const val ALL = "All"
    }
}

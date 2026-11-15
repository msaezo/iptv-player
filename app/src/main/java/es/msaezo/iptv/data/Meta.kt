package es.msaezo.iptv.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class TmdbItem(
    val id: Int,
    val type: String, // "movie" | "tv"
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val vote: Double,
    val voteCount: Int,
) {
    val posterUrl get() = poster?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl get() = backdrop?.let { "https://image.tmdb.org/t/p/w780$it" }
    val backdropLargeUrl get() = backdrop?.let { "https://image.tmdb.org/t/p/w1280$it" }
}

data class Ratings(val imdb: String?, val rottenTomatoes: String?, val metacritic: String?)

data class MetaDetails(
    val item: TmdbItem,
    val runtime: Int?,
    val genres: List<String>,
    val imdbId: String?,
    val seasons: Int?,
    val ratings: Ratings?,
)

data class CollectionDef(
    val key: String,
    val title: String,
    val type: String, // "movie" | "tv"
    val path: String = "",
    val params: Map<String, String> = emptyMap(),
    val pages: Int = 5,
    val titles: List<Pair<String, Int>> = emptyList(),
)

data class ResolvedCollection(
    val def: CollectionDef,
    val movies: List<Movie>,
    val shows: List<Show>,
    val backdrop: String?,
) {
    val size get() = movies.size + shows.size
}

/**
 * Talks to TMDB (collections, posters, synopsis, scores) and OMDb (IMDb, Rotten Tomatoes,
 * Metacritic). Responses are cached on disk.
 */
class MetaRepository(
    context: Context,
    private val store: UserStore,
    private val repo: PlaylistRepository,
    scope: CoroutineScope,
) {
    private val cacheDir = File(context.cacheDir, "meta").apply { mkdirs() }

    private val _collections = MutableStateFlow<List<ResolvedCollection>>(emptyList())
    val collections: StateFlow<List<ResolvedCollection>> = _collections.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** TMDB info known for playlist titles (key = Movie.id / Show.id), for scores on cards. */
    private val _known = MutableStateFlow<Map<String, TmdbItem>>(emptyMap())
    val known: StateFlow<Map<String, TmdbItem>> = _known.asStateFlow()

    private val detailsCache = ConcurrentHashMap<String, MetaDetails>()

    init {
        scope.launch {
            combine(repo.view, store.tmdbKey) { v, k -> v to k }.collectLatest { (view, key) ->
                if (view == null || key.isBlank()) {
                    _collections.value = emptyList()
                } else {
                    resolveAll(view, key)
                }
            }
        }
    }

    // --- collections --------------------------------------------------------------------

    private suspend fun resolveAll(view: LibraryView, key: String) = withContext(Dispatchers.IO) {
        _loading.value = true
        _lastError.value = null
        val matcher = Matcher(view)
        val out = ArrayList<ResolvedCollection>()
        val known = HashMap<String, TmdbItem>()
        for (def in Collections.all) {
            try {
                val items = fetchCollection(def, key)
                val movies = LinkedHashMap<String, Movie>()
                val shows = LinkedHashMap<String, Show>()
                var backdrop: String? = null
                for (it in items) {
                    if (def.type == "movie") {
                        val m = matcher.movie(it) ?: continue
                        if (movies.putIfAbsent(m.id, m) == null) known[m.id] = it
                    } else {
                        val s = matcher.show(it) ?: continue
                        if (shows.putIfAbsent(s.id, s) == null) known[s.id] = it
                    }
                    if (backdrop == null) backdrop = it.backdropUrl
                }
                if (movies.size + shows.size >= 3) {
                    out += ResolvedCollection(def, movies.values.toList(), shows.values.toList(), backdrop)
                    _collections.value = out.toList()
                    _known.value = known.toMap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastError.value = e.message
                if (e is AuthException) break
            }
        }
        _known.value = known.toMap()
        _loading.value = false
    }

    private fun fetchCollection(def: CollectionDef, key: String): List<TmdbItem> {
        if (def.titles.isNotEmpty()) {
            return def.titles.mapNotNull { (title, year) -> searchTmdb(def.type, title, year, key) }
        }
        val out = ArrayList<TmdbItem>()
        for (page in 1..def.pages) {
            val params = def.params + mapOf("page" to page.toString(), "language" to LANG)
            val json = tmdb(def.path, params, key, ttlHours = 12) ?: break
            val results = json.optJSONArray("results") ?: break
            for (i in 0 until results.length()) parseItem(results.getJSONObject(i), def.type)?.let(out::add)
            if (page >= json.optInt("total_pages", 1)) break
        }
        return out
    }

    // --- details ------------------------------------------------------------------------

    suspend fun movieDetails(movie: Movie): MetaDetails? = details(movie.id, "movie", movie.title, movie.year)
    suspend fun showDetails(show: Show): MetaDetails? = details(show.id, "tv", show.name, show.year)

    private suspend fun details(id: String, type: String, title: String, year: Int?): MetaDetails? =
        withContext(Dispatchers.IO) {
            detailsCache[id]?.let { return@withContext it }
            val key = store.tmdbKey.value
            if (key.isBlank()) return@withContext null
            try {
                val item = _known.value[id] ?: searchTmdb(type, title, year, key) ?: return@withContext null
                val d = tmdb("/$type/${item.id}", mapOf("language" to LANG, "append_to_response" to "external_ids"),
                    key, ttlHours = 24 * 7)
                var overview = d?.optString("overview").orEmpty()
                if (overview.isBlank()) {
                    overview = tmdb("/$type/${item.id}", mapOf("language" to "en-US"), key, 24 * 7)
                        ?.optString("overview").orEmpty()
                }
                val genres = d?.optJSONArray("genres")?.let { a ->
                    List(a.length()) { a.getJSONObject(it).optString("name") }
                } ?: emptyList()
                val runtime = if (type == "movie") d?.optInt("runtime")?.takeIf { it > 0 }
                else d?.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 }
                val imdbId = (d?.optString("imdb_id")?.takeIf { it.startsWith("tt") })
                    ?: d?.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.startsWith("tt") }
                val ratings = imdbId?.let { omdb(it) }
                val result = MetaDetails(
                    item = item.copy(overview = overview.ifBlank { item.overview }),
                    runtime = runtime,
                    genres = genres,
                    imdbId = imdbId,
                    seasons = d?.optInt("number_of_seasons")?.takeIf { it > 0 },
                    ratings = ratings,
                )
                detailsCache[id] = result
                _known.value = _known.value + (id to item)
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastError.value = e.message
                null
            }
        }

    private fun omdb(imdbId: String): Ratings? {
        val key = store.omdbKey.value
        if (key.isBlank()) return null
        val url = "https://www.omdbapi.com/?i=$imdbId&apikey=${enc(key)}"
        val json = getJson(url, cacheKey = "omdb:$imdbId", ttlHours = 24 * 7) ?: return null
        if (json.optString("Response") == "False") return null
        var rt: String? = null
        json.optJSONArray("Ratings")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                if (r.optString("Source") == "Rotten Tomatoes") rt = r.optString("Value")
            }
        }
        fun clean(s: String?) = s?.takeIf { it.isNotBlank() && it != "N/A" }
        return Ratings(clean(json.optString("imdbRating")), clean(rt), clean(json.optString("Metascore")))
    }

    // --- TMDB plumbing ------------------------------------------------------------------

    private fun searchTmdb(type: String, title: String, year: Int?, key: String): TmdbItem? {
        fun search(withYear: Boolean): List<TmdbItem> {
            val params = HashMap<String, String>()
            params["query"] = title
            params["language"] = LANG
            if (withYear && year != null) {
                params[if (type == "movie") "primary_release_year" else "first_air_date_year"] = year.toString()
            }
            val json = tmdb("/search/$type", params, key, ttlHours = 24 * 30) ?: return emptyList()
            val a = json.optJSONArray("results") ?: return emptyList()
            return (0 until a.length()).mapNotNull { parseItem(a.getJSONObject(it), type) }
        }
        var results = search(withYear = true)
        if (results.isEmpty() && year != null) results = search(withYear = false)
        if (results.isEmpty()) return null
        val wanted = Tags.mergeKey(title)
        return results.firstOrNull {
            (Tags.mergeKey(it.title) == wanted || Tags.mergeKey(it.originalTitle) == wanted) &&
                (year == null || it.year == null || kotlin.math.abs(it.year - year) <= 1)
        } ?: results.first()
    }

    private fun parseItem(o: JSONObject, type: String): TmdbItem? {
        val id = o.optInt("id", -1).takeIf { it > 0 } ?: return null
        val title = o.optString(if (type == "movie") "title" else "name")
        val original = o.optString(if (type == "movie") "original_title" else "original_name")
        val date = o.optString(if (type == "movie") "release_date" else "first_air_date")
        return TmdbItem(
            id = id, type = type, title = title, originalTitle = original,
            year = date.take(4).toIntOrNull(),
            overview = o.optString("overview"),
            poster = o.optString("poster_path").takeIf { it.startsWith("/") },
            backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") },
            vote = o.optDouble("vote_average", 0.0),
            voteCount = o.optInt("vote_count", 0),
        )
    }

    class AuthException(msg: String) : Exception(msg)

    private fun tmdb(path: String, params: Map<String, String>, key: String, ttlHours: Int): JSONObject? {
        val bearer = key.startsWith("eyJ")
        val query = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val base = "https://api.themoviedb.org/3$path?$query"
        val url = if (bearer) base else "$base&api_key=${enc(key)}"
        return getJson(url, cacheKey = "tmdb:$base", ttlHours = ttlHours,
            headers = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap())
    }

    private fun getJson(
        url: String,
        cacheKey: String,
        ttlHours: Int,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject? {
        val file = File(cacheDir, sha1(cacheKey))
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlHours * 3_600_000L) {
            try {
                return JSONObject(file.readText())
            } catch (_: Exception) {
            }
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code == 401) throw AuthException("TMDB/OMDb key rejected (HTTP 401)")
            if (code == 404) return null
            if (code != 200) throw java.io.IOException("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            file.writeText(text)
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val LANG = "es-ES"
    }
}

/** Matches TMDB results to titles present in the (filtered) playlist. */
class Matcher(view: LibraryView) {
    private val movieIdx: Map<String, List<Movie>> = view.movies.groupBy { it.id.substringBeforeLast('|') }
    private val showIdx: Map<String, List<Show>> = view.shows.groupBy { it.id.removePrefix("x:") }

    fun movie(item: TmdbItem): Movie? {
        val keys = listOf(item.title, item.originalTitle).filter { it.isNotBlank() }.map { Tags.mergeKey(it) }.distinct()
        val candidates = keys.flatMap { movieIdx[it].orEmpty() }
        if (candidates.isEmpty()) return null
        val y = item.year ?: return candidates.first()
        return candidates.firstOrNull { it.year == y }
            ?: candidates.firstOrNull { it.year != null && kotlin.math.abs(it.year - y) <= 1 }
            ?: candidates.firstOrNull { it.year == null }
    }

    fun show(item: TmdbItem): Show? {
        val keys = listOf(item.title, item.originalTitle).filter { it.isNotBlank() }.map { Tags.mergeKey(it) }.distinct()
        return keys.firstNotNullOfOrNull { showIdx[it]?.firstOrNull() }
    }
}

object Collections {
    private const val DISCOVER_MOVIE = "/discover/movie"
    private const val DISCOVER_TV = "/discover/tv"
    private val popular = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "150")

    private fun movieGenre(key: String, title: String, genres: String) =
        CollectionDef(key, title, "movie", DISCOVER_MOVIE, popular + ("with_genres" to genres), pages = 8)

    private fun tvGenre(key: String, title: String, genres: String) =
        CollectionDef(key, title, "tv", DISCOVER_TV, popular + ("with_genres" to genres), pages = 6)

    private fun movieProvider(key: String, title: String, id: Int) = CollectionDef(
        key, title, "movie", DISCOVER_MOVIE,
        mapOf("with_watch_providers" to "$id", "watch_region" to "ES", "sort_by" to "popularity.desc"), pages = 5,
    )

    private fun tvProvider(key: String, title: String, id: Int) = CollectionDef(
        key, title, "tv", DISCOVER_TV,
        mapOf("with_watch_providers" to "$id", "watch_region" to "ES", "sort_by" to "popularity.desc"), pages = 5,
    )

    /** Best Picture winners (film release year). */
    private val bestPicture = listOf(
        "Anora" to 2024, "Oppenheimer" to 2023, "Everything Everywhere All at Once" to 2022,
        "CODA" to 2021, "Nomadland" to 2020, "Parasite" to 2019, "Green Book" to 2018,
        "The Shape of Water" to 2017, "Moonlight" to 2016, "Spotlight" to 2015, "Birdman" to 2014,
        "12 Years a Slave" to 2013, "Argo" to 2012, "The Artist" to 2011, "The King's Speech" to 2010,
        "The Hurt Locker" to 2008, "Slumdog Millionaire" to 2008, "No Country for Old Men" to 2007,
        "The Departed" to 2006, "Crash" to 2005, "Million Dollar Baby" to 2004,
        "The Lord of the Rings: The Return of the King" to 2003, "Chicago" to 2002,
        "A Beautiful Mind" to 2001, "Gladiator" to 2000, "American Beauty" to 1999,
        "Shakespeare in Love" to 1998, "Titanic" to 1997, "The English Patient" to 1996,
        "Braveheart" to 1995, "Forrest Gump" to 1994, "Schindler's List" to 1993,
        "Unforgiven" to 1992, "The Silence of the Lambs" to 1991, "Dances with Wolves" to 1990,
        "Rain Man" to 1988, "Platoon" to 1986, "Amadeus" to 1984, "Rocky" to 1976,
        "One Flew Over the Cuckoo's Nest" to 1975, "The Godfather Part II" to 1974,
        "The Godfather" to 1972, "Casablanca" to 1942,
    )

    private val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    /** Genre rows (named as in [Tags.genreOrder]) → the TMDB collections that feed them. */
    val genreKeys: Map<String, List<String>> = mapOf(
        "Action & Adventure" to listOf("m_action", "t_action"),
        "Comedy" to listOf("m_comedy", "t_comedy"),
        "Drama" to listOf("m_drama", "t_drama"),
        "Thriller & Crime" to listOf("m_thriller", "m_crime", "m_mystery", "t_crime", "t_mystery"),
        "Horror" to listOf("m_horror"),
        "Sci-Fi & Fantasy" to listOf("m_scifi", "m_fantasy", "t_scifi"),
        "Romance" to listOf("m_romance"),
        "Family & Animation" to listOf("m_family", "m_animation", "t_animation"),
        "Documentaries" to listOf("m_docs", "t_docs"),
        "War & Western" to listOf("m_war"),
    )

    val movies: List<CollectionDef> = listOf(
        CollectionDef("m_trending", "Trending this week", "movie", "/trending/movie/week", pages = 5),
        CollectionDef(
            "m_year", "Most watched of $year", "movie", DISCOVER_MOVIE,
            mapOf("primary_release_year" to "$year", "sort_by" to "popularity.desc"), pages = 6,
        ),
        CollectionDef("m_cinema", "In cinemas now", "movie", "/movie/now_playing", mapOf("region" to "ES"), pages = 5),
        CollectionDef("m_popular", "Popular right now", "movie", "/movie/popular", pages = 5),
        CollectionDef("m_top", "All-time classics", "movie", "/movie/top_rated", pages = 10),
        CollectionDef("m_oscars", "Best Picture winners", "movie", titles = bestPicture),
        movieProvider("m_netflix", "On Netflix", 8),
        movieProvider("m_prime", "On Prime Video", 119),
        movieProvider("m_disney", "On Disney+", 337),
        movieProvider("m_max", "On Max", 1899),
        movieProvider("m_movistar", "On Movistar Plus+", 149),
        movieProvider("m_apple", "On Apple TV+", 350),
        movieGenre("m_action", "Action & Adventure", "28|12"),
        movieGenre("m_comedy", "Comedy", "35"),
        movieGenre("m_drama", "Drama", "18"),
        movieGenre("m_scifi", "Science Fiction", "878"),
        movieGenre("m_fantasy", "Epic Fantasy", "14"),
        movieGenre("m_thriller", "Thrillers", "53"),
        movieGenre("m_crime", "Crime", "80"),
        movieGenre("m_horror", "Horror Night", "27"),
        movieGenre("m_romance", "Romance", "10749"),
        movieGenre("m_animation", "Animation", "16"),
        movieGenre("m_family", "Family", "10751"),
        movieGenre("m_mystery", "Mind Twists & Mystery", "9648"),
        movieGenre("m_war", "War & History", "10752|36"),
        movieGenre("m_docs", "Documentaries", "99"),
    )

    val shows: List<CollectionDef> = listOf(
        CollectionDef("t_trending", "Trending series", "tv", "/trending/tv/week", pages = 5),
        CollectionDef(
            "t_year", "Most watched series of $year", "tv", DISCOVER_TV,
            mapOf("first_air_date_year" to "$year", "sort_by" to "popularity.desc"), pages = 6,
        ),
        CollectionDef("t_popular", "Popular series", "tv", "/tv/popular", pages = 5),
        CollectionDef("t_top", "Acclaimed series", "tv", "/tv/top_rated", pages = 8),
        tvProvider("t_netflix", "Netflix series", 8),
        tvProvider("t_prime", "Prime Video series", 119),
        tvProvider("t_disney", "Disney+ series", 337),
        tvProvider("t_max", "HBO / Max series", 1899),
        tvProvider("t_movistar", "Movistar Plus+ series", 149),
        tvProvider("t_apple", "Apple TV+ series", 350),
        tvGenre("t_drama", "Drama series", "18"),
        tvGenre("t_comedy", "Comedy series", "35"),
        tvGenre("t_crime", "Crime series", "80"),
        tvGenre("t_scifi", "Sci-Fi & Fantasy series", "10765"),
        tvGenre("t_action", "Action & Adventure series", "10759"),
        tvGenre("t_mystery", "Mystery series", "9648"),
        tvGenre("t_animation", "Animated series", "16"),
        tvGenre("t_docs", "Docuseries", "99"),
    )

    val all: List<CollectionDef> = movies + shows
}

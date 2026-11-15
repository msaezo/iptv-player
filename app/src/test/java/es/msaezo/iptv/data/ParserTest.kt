package es.msaezo.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParserTest {
    private val sample = """
        #EXTM3U url-tvg="http://epg/x.xml"
        #EXTINF:-1 group-title="UK | ENTERTAINMENT",UK: BBC One HD
        http://h:8080/u/p/1001
        #EXTINF:-1 group-title="ES | GENERALISTAS",La 1 HD
        http://h:8080/live/u/p/1002.ts
        #EXTINF:-1 group-title="US | NEWS",CNN US
        http://h:8080/u/p/1003
        #EXTINF:-1 tvg-logo="http://p/dune.jpg" group-title="ES | PELICULAS",ES - Dune (2021) 4K
        http://h:8080/movie/u/p/2001.mkv
        #EXTINF:-1 group-title="EN | MOVIES",Dune (2021) [EN] FHD
        http://h:8080/movie/u/p/2002.mp4
        #EXTINF:-1 group-title="FR | FILMS",Oppenheimer (2023)
        http://h:8080/movie/u/p/2005.mkv
        #EXTINF:-1 group-title="ES | SERIES",Breaking Bad S01E01
        http://h:8080/series/u/p/3001.mkv
        #EXTINF:-1 group-title="EN | TV SHOWS",Breaking Bad S01E01 - Pilot
        http://h:8080/series/u/p/3003.mkv
    """.trimIndent()

    private val lib = Library(M3uParser.parse(sample.reader().buffered()).entries)
    private val view = lib.view(LibraryFilter())

    @Test fun classifies() {
        assertEquals(3, lib.live.size)
        assertEquals(2, lib.movies.size) // Dune merged + Oppenheimer
        assertEquals(1, lib.shows.size)
    }

    @Test fun mergesVersionsAndFilters() {
        assertEquals(1, view.movies.size)
        val dune = view.movies.first()
        assertEquals("Dune", dune.title)
        assertEquals(listOf("es", "en"), dune.versions.map { it.language })
        assertEquals("4K", dune.versions.first().quality)
    }

    @Test fun mergesEpisodes() {
        val bb = view.shows.first()
        assertEquals(1, bb.episodes.size)
        assertEquals(2, bb.episodes.first().versions.size)
    }

    @Test fun liveOrder() {
        assertEquals(listOf("ES | GENERALISTAS", "UK | ENTERTAINMENT", "US | NEWS"), view.liveCategories.map { it.name })
        assertNotNull(lib.epgUrl)
    }
}

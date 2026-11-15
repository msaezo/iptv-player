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

    private val parsed = M3uParser.parse(sample.reader().buffered())
    private val lib = Library(parsed.entries, parsed.epgUrl)
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

class LanguageTest {
    private fun lang(name: String, group: String) = Tags.language(name, group)

    @Test fun shortWordsInsideCategoriesAreNotLanguages() {
        assertEquals(Tags.UNKNOWN, lang("Dune (2021)", "PELICULAS DE ACCION"))
        assertEquals(Tags.UNKNOWN, lang("Dune (2021)", "MOVIES IN 4K"))
        assertEquals(Tags.UNKNOWN, lang("Us (2019)", "CINE EN CASA"))
        assertEquals(Tags.UNKNOWN, lang("It (2017)", "SCI-FI & NO LIMITS"))
    }

    @Test fun tagsAreLanguages() {
        assertEquals("en", lang("Dune (2021)", "| EN | ACTION"))
        assertEquals("en", lang("Dune (2021)", "UK MOVIES"))
        assertEquals("en", lang("Dune (2021)", "US | MOVIES"))
        assertEquals("es", lang("It (2017)", "|ES| TERROR"))
        assertEquals("de", lang("Dune (2021)", "DE | FILME"))
        assertEquals("lat", lang("Coco (2017)", "ESPAÑOL LATINO"))
        assertEquals("multi", lang("AZ - Road House (2024)", "ORIGINAL AMAZON 4K MULTI"))
    }

    @Test fun serviceCodesAreNotLanguagesAndAreStripped() {
        assertEquals("es", lang("NF - ES - Roma (2018) 4K", "NETFLIX"))
        assertEquals("Road House", Tags.displayTitle(Classifier.splitYear("AZ - Road House (2024)").first))
        assertEquals("Dune: Part Two", Tags.displayTitle(Classifier.splitYear("HB - Dune: Part Two (2024)").first))
        assertEquals("Mission: Impossible", Tags.displayTitle(Classifier.splitYear("Mission: Impossible (1996)").first))
    }

    @Test fun genres() {
        assertEquals("Action & Adventure", Tags.genreOfGroup("PELICULAS DE ACCION"))
        assertEquals("Horror", Tags.genreOfGroup("|ES| TERROR"))
        assertEquals(null, Tags.genreOfGroup("ORIGINAL AMAZON 4K MULTI"))
    }
}

class LiveGuideTest {
    private fun live(name: String, group: String, url: String) =
        Entry(title = name, rawName = name, url = url, logo = null, group = group, tvgId = null, type = ContentType.LIVE)

    private val guide = LiveGuide(listOf(
        live("FR: Canal+ Sport HD", "FR | SPORTS", "u1"),
        live("AR: beIN Sports 1 HD", "AR | BEIN SPORTS", "u2"),
        live("ES | La 1 HD", "ES | GENERALISTAS", "u3"),
        live("ES | La 1 FHD", "ES | GENERALISTAS", "u4"),
        live("ES | La 1 4K (HEVC)", "ES | GENERALISTAS", "u5"),
        live("M+ Liga Endesa HD", "ES | DEPORTES", "u6"),
        live("Movistar Tenis ATP HD", "ES | DEPORTES", "u7"),
        live("EVENTO 01: Real Madrid vs Barcelona 21:00", "ES | EVENTOS", "u8"),
        live("EVENTO 02", "ES | EVENTOS", "u9"),
    ))

    @Test fun qualitiesMergeBestFirst() {
        val la1 = guide.channelOf("u3")!!
        assertEquals("La 1", la1.name)
        assertEquals(listOf("u5", "u4", "u3"), la1.versions.map { it.url })
    }

    @Test fun regionsAndSports() {
        assertEquals("FR", guide.channelOf("u1")!!.region)
        assertEquals("AR", guide.channelOf("u2")!!.region)
        assertEquals("Basketball", guide.channelOf("u6")!!.sport)
        assertEquals("Tennis", guide.channelOf("u7")!!.sport)
        val sports = guide.sections.first { it.name == "Sports" }.filters.first().items
        assertEquals("ES", sports.first().region) // Spain before French / Arabic packs
    }

    @Test fun events() {
        assertEquals(true, guide.channelOf("u8")!!.announced)
        assertEquals(false, guide.channelOf("u9")!!.announced)
    }
}

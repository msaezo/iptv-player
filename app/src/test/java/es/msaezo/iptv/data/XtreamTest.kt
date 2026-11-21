package es.msaezo.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamTest {
    @Test fun parsesAccount() {
        val a = XtreamAccount.fromUrl("http://egg.example:80/get.php?username=u1&password=p2&type=m3u_plus&output=mpegts")!!
        assertEquals("http://egg.example:80", a.base)
        assertEquals("u1", a.username)
        assertEquals("http://egg.example:80/live/u1/p2/5.ts", a.liveUrl("5"))
        assertEquals("http://egg.example:80/movie/u1/p2/7.mkv", a.movieUrl("7", "mkv"))
        assertNull(XtreamAccount.fromUrl("http://example.com/list.m3u"))
    }

    @Test fun readsArrayWithMixedTypes() {
        val json = """[{"num":1,"name":"La 1 HD","stream_id":12,"category_id":"3","x":{"a":1},"y":[1,2],"rating":null,"ok":true},
            {"name":"Dune","stream_id":"99","rating":"7.8"}]"""
        val items = ArrayList<Map<String, String>>()
        XtreamClient.readArray(json.reader()) { items += HashMap(it) }
        assertEquals(2, items.size)
        assertEquals("12", items[0]["stream_id"])
        assertEquals("3", items[0]["category_id"])
        assertNull(items[0]["rating"])
        assertEquals("99", items[1]["stream_id"])
        assertEquals(7.8, XtreamClient.parseRating(items[1]["rating"])!!, 0.001)
    }

    @Test fun emptyObjectInsteadOfArray() {
        var n = 0
        XtreamClient.readArray("{}".reader()) { n++ }
        assertEquals(0, n)
    }

    @Test fun seriesSourcesMergeIntoOneShow() {
        val src = listOf(
            SeriesSource("1", "ES - Breaking Bad", "Breaking Bad", "ES | SERIES", null, 8.9, "es", 2008),
            SeriesSource("2", "EN - Breaking Bad", "Breaking Bad", "EN | SERIES", null, null, "en", 2008),
        )
        val lib = Library(emptyList(), null, src)
        assertEquals(1, lib.shows.size)
        assertEquals(listOf("es", "en"), lib.shows[0].languages)
        assertEquals(true, lib.shows[0].needsLoading)
    }
}

class SyncPathTest {
    @org.junit.Test
    fun pathDropsTheAccountAndComesBack() {
        val acc = XtreamAccount("http://egg-d5.xyz:80", "user1", "pass1")
        val url = acc.movieUrl("12345", "mkv")
        val path = ProgressSync.pathOf(url)
        org.junit.Assert.assertEquals("movie/12345.mkv", path)
        org.junit.Assert.assertFalse(path!!.contains("user1") || path.contains("pass1"))
        org.junit.Assert.assertEquals(url, ProgressSync.urlOf(acc, path))
        org.junit.Assert.assertEquals("series/9.mp4", ProgressSync.pathOf(acc.episodeUrl("9", null)))
        org.junit.Assert.assertNull(ProgressSync.pathOf(acc.liveUrl("7")))
    }
}

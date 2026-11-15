package es.msaezo.iptv.data

/** One TV channel, merging the same channel offered in several qualities. */
data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val region: String?,
    val topic: String,
    val sport: String?,
    val isEvent: Boolean,
    /** For event channels: the name currently announces an event (teams, time…). */
    val announced: Boolean,
    val pack: String,
    /** Best quality first. */
    val versions: List<Entry>,
    val labels: List<String>,
) {
    val best: Entry get() = versions.first()
    /** A version the provider has a programme guide for, if any. */
    val guideEntry: Entry? get() = versions.firstOrNull { it.tvgId != null }
}

class LiveSection(val name: String, val filters: List<Category<LiveChannel>>)

/**
 * Live TV organised by what people look for (sports, a country, events) instead of the
 * provider's hundreds of packs, with duplicate qualities folded into one channel.
 */
class LiveGuide(entries: List<Entry>) {

    val channels: List<LiveChannel>
    val sections: List<LiveSection>
    private val byUrl = HashMap<String, LiveChannel>()

    fun channelOf(url: String): LiveChannel? = byUrl[url]

    init {
        // 1. fold qualities of the same channel together
        val merged = LinkedHashMap<String, MutableList<Entry>>()
        val regions = HashMap<String, String?>()
        for (e in entries) {
            val region = regions.getOrPut(e.group) { Tags.regionOf(e.group) } ?: Tags.regionOf("", e.rawName)
            val key = (region ?: "") + "|" + channelKey(e.rawName).ifEmpty { e.url }
            merged.getOrPut(key) { ArrayList(2) }.add(e)
        }
        channels = merged.map { (key, list) ->
            val sorted = list.sortedByDescending { Tags.qualityRank(Tags.quality(it.rawName, "")) }
            val first = list.first()
            val name = channelName(first.rawName)
            val nameTokens = Tags.tokens(first.rawName)
            val groupTokens = Tags.tokens(first.group)
            val all = groupTokens + nameTokens
            val isEvent = all.any { it in EVENT_WORDS }
            val sport = sportOf(all)
            val topic = when {
                isEvent -> EVENTS
                sport != null || all.any { it in SPORT_WORDS } -> SPORTS
                else -> topicOf(groupTokens) ?: topicOf(nameTokens) ?: GENERAL
            }
            LiveChannel(
                id = key, name = name, logo = sorted.firstNotNullOfOrNull { it.logo },
                region = key.substringBefore('|').ifEmpty { null },
                topic = topic, sport = sport, isEvent = isEvent,
                announced = isEvent && looksAnnounced(first.rawName),
                pack = first.group, versions = sorted, labels = labelsOf(sorted),
            ).also { ch -> for (v in sorted) byUrl[v.url] = ch }
        }

        // 2. sections
        val ordered = channels.withIndex().sortedWith(compareBy({ regionRank(it.value.region) }, { it.index })).map { it.value }
        val out = ArrayList<LiveSection>()

        val sports = ordered.filter { it.topic == SPORTS || (it.isEvent && it.sport != null) }
        if (sports.isNotEmpty()) {
            val filters = ArrayList<Category<LiveChannel>>()
            filters += Category(ALL, sports)
            for (s in SPORT_ORDER) sports.filter { it.sport == s }.takeIf { it.isNotEmpty() }?.let { filters += Category(s, it) }
            sports.filter { it.sport == null }.takeIf { it.isNotEmpty() && filters.size > 1 }?.let { filters += Category("Other sports", it) }
            out += LiveSection(SPORTS, filters)
        }

        val events = ordered.filter { it.isEvent }.sortedByDescending { it.announced }
        if (events.isNotEmpty()) {
            val filters = ArrayList<Category<LiveChannel>>()
            events.filter { it.announced }.takeIf { it.isNotEmpty() }?.let { filters += Category("Announced", it) }
            filters += Category(ALL, events)
            out += LiveSection(EVENTS, filters)
        }

        for ((code, title) in listOf("ES" to "Spain", "UK" to "UK", "US" to "USA")) {
            val list = ordered.filter { it.region == code && !it.isEvent }
            if (list.isNotEmpty()) out += LiveSection(title, byTopic(list))
        }

        val rest = ordered.filter { it.region != "ES" && it.region != "UK" && it.region != "US" && !it.isEvent }
        if (rest.isNotEmpty()) {
            val filters = ArrayList<Category<LiveChannel>>()
            val byRegion = LinkedHashMap<String, MutableList<LiveChannel>>()
            for (c in rest) byRegion.getOrPut(c.region?.let(Tags::regionLabel) ?: "Other") { ArrayList() }.add(c)
            val named = byRegion.entries.filter { it.key != "Other" }.sortedByDescending { it.value.size }
            for ((k, v) in named) filters += Category(k, v)
            byRegion["Other"]?.let { filters += Category("Other", it) }
            out += LiveSection("International", filters)
        }

        val packs = LinkedHashMap<String, MutableList<LiveChannel>>()
        for (c in ordered) packs.getOrPut(c.pack) { ArrayList() }.add(c)
        if (packs.size > 1) out += LiveSection("Provider packs", packs.map { Category(it.key, it.value) })

        sections = out
    }

    private fun byTopic(list: List<LiveChannel>): List<Category<LiveChannel>> {
        val filters = ArrayList<Category<LiveChannel>>()
        filters += Category(ALL, list)
        for (t in TOPIC_ORDER) list.filter { it.topic == t }.takeIf { it.isNotEmpty() }?.let { filters += Category(t, it) }
        return if (filters.size <= 2) filters.take(1) else filters
    }

    companion object {
        const val ALL = "All"
        const val SPORTS = "Sports"
        const val EVENTS = "Events"
        const val GENERAL = "General"

        val SPORT_ORDER = listOf("Football", "Tennis", "Basketball", "Formula 1", "Motorbikes")
        val TOPIC_ORDER = listOf(GENERAL, SPORTS, "Movies & Series", "Kids", "News", "Documentaries", "Music", "Adults")

        private fun regionRank(r: String?) = when (r) { "ES" -> 0; "UK" -> 1; "US" -> 2; else -> 3 }

        private val EVENT_WORDS = setOf("EVENTO", "EVENTOS", "EVENT", "EVENTS", "PPV")
        private val QUALITY_WORDS = setOf("4K", "UHD", "2160P", "FHD", "1080P", "1080", "HD", "720P", "SD", "480P", "576P")
        private val VARIANT_WORDS = setOf(
            "HEVC", "H265", "H264", "X265", "50FPS", "60FPS", "FPS", "RAW", "BACKUP", "LOW", "HQ", "LQ", "HDR", "ALT",
        )

        private val SPORT_WORDS = setOf(
            "DEPORTE", "DEPORTES", "SPORT", "SPORTS", "DAZN", "EUROSPORT", "ESPN", "BEIN", "GOL", "VAMOS", "ELEVEN",
            "SUPERSPORT", "GOLF", "NFL", "NHL", "MLB", "UFC", "WWE", "BOXING", "BOXEO", "RUGBY", "CRICKET", "RACING",
            "CICLISMO", "CYCLING", "TELEDEPORTE", "SPORTV", "SPORTSNET", "ARENA",
        )
        private val SPORTS_BY_WORD: Map<String, String> = buildMap {
            fun add(sport: String, vararg w: String) = w.forEach { put(it, sport) }
            // Basketball first: "LIGA ENDESA" must not be read as football.
            add("Basketball", "NBA", "BALONCESTO", "BASKET", "BASKETBALL", "ACB", "ENDESA", "EUROLEAGUE", "EUROLIGA", "WNBA")
            add("Tennis", "TENIS", "TENNIS", "ATP", "WTA", "WIMBLEDON", "GARROS")
            add("Formula 1", "F1", "FORMULA", "FORMULA1")
            add("Motorbikes", "MOTOGP", "MOTO", "MOTOS", "MOTO2", "MOTO3", "SBK", "SUPERBIKE", "SUPERBIKES", "WSBK")
            add("Football", "FUTBOL", "FOOTBALL", "SOCCER", "LALIGA", "LIGA", "CHAMPIONS", "UEFA", "PREMIER",
                "BUNDESLIGA", "CALCIO", "HYPERMOTION", "LIBERTADORES", "MLS", "FIFA", "COPA", "LIGUE")
        }
        private val SPORT_PRIORITY = listOf("Basketball", "Tennis", "Formula 1", "Motorbikes", "Football")

        private fun sportOf(tokens: List<String>): String? {
            var found: String? = null
            for (t in tokens) {
                val s = SPORTS_BY_WORD[t] ?: continue
                if (found == null || SPORT_PRIORITY.indexOf(s) < SPORT_PRIORITY.indexOf(found)) found = s
            }
            return found
        }

        private val TOPIC_BY_WORD: Map<String, String> = buildMap {
            fun add(topic: String, vararg w: String) = w.forEach { put(it, topic) }
            add("Adults", "XXX", "ADULT", "ADULTS", "ADULTOS", "ADULTO")
            add("Kids", "INFANTIL", "INFANTILES", "KIDS", "NINOS", "CARTOON", "DISNEY", "NICK", "NICKELODEON", "BOING",
                "CLAN", "BABY", "JUNIOR", "CBEEBIES", "CHILDREN")
            add("News", "NOTICIAS", "NEWS", "24H", "INFORMACION", "CNN", "EURONEWS", "INFORMATIVOS")
            add("Documentaries", "DOCUMENTAL", "DOCUMENTALES", "DOCUMENTARY", "DOCUMENTARIES", "DISCOVERY", "GEOGRAPHIC",
                "HISTORIA", "HISTORY", "ODISEA", "VIAJAR", "NATURE", "NATURALEZA", "DOCU", "DOCS")
            add("Music", "MUSICA", "MUSIC", "MTV", "RADIO", "RADIOS", "VEVO")
            add("Movies & Series", "CINE", "MOVIES", "PELICULAS", "SERIES", "CINEMA", "FILM", "FILMS", "HBO", "AXN", "AMC",
                "HOLLYWOOD", "COSMO", "WARNER", "PARAMOUNT", "TCM", "ESTRENOS")
        }
        private fun topicOf(tokens: List<String>): String? {
            for (t in tokens) TOPIC_BY_WORD[t]?.let { return it }
            return null
        }

        private val clock = Regex("""\b\d{1,2}[:.h]\d{2}\b""")
        private val versus = Regex("""(?i)\s(vs\.?|v|-|–|@|x)\s""")

        /** An event channel whose name carries a fixture or a time, not just "EVENTO 03". */
        internal fun looksAnnounced(rawName: String): Boolean {
            val body = rawName.substringAfter(':', rawName.substringAfter('|', rawName))
            if (clock.containsMatchIn(rawName)) return true
            val words = Tags.tokens(body).filter { it !in EVENT_WORDS && it !in QUALITY_WORDS && it.any { c -> c.isLetter() } }
            return words.size >= 3 && versus.containsMatchIn(body)
        }

        /** Name without the provider's country tag and without quality / variant words. */
        internal fun channelName(rawName: String): String {
            val shown = Tags.displayTitle(rawName)
            val words = shown.split(' ').filter { it.isNotEmpty() }.toMutableList()
            while (words.size > 1) {
                val w = Tags.plain(words.last()).trim('(', ')', '[', ']', '*', '-')
                if (w.isEmpty() || w in QUALITY_WORDS || w in VARIANT_WORDS) words.removeAt(words.size - 1) else break
            }
            return words.joinToString(" ").ifEmpty { rawName.trim() }
        }

        /** Identity of a channel regardless of quality: "La 1 HD" and "LA 1 FHD (HEVC)" match. */
        internal fun channelKey(rawName: String): String =
            Tags.tokens(Tags.displayTitle(rawName))
                .filter { it !in QUALITY_WORDS && it !in VARIANT_WORDS }
                .joinToString(" ").lowercase()

        /** One label per version; identical ones are numbered so they can be told apart. */
        internal fun labelsOf(versions: List<Entry>): List<String> {
            val raw = versions.map { versionLabel(it.rawName) }
            if (raw.size < 2) return raw
            val seen = HashMap<String, Int>()
            val repeated = raw.groupingBy { it }.eachCount()
            return raw.map { l ->
                if (repeated.getValue(l) == 1) l
                else {
                    val n = (seen[l] ?: 0) + 1
                    seen[l] = n
                    if (l == "Standard") "Source $n" else "$l ($n)"
                }
            }
        }

        internal fun versionLabel(rawName: String): String {
            val parts = Tags.tokens(rawName).filter { it in QUALITY_WORDS || it in VARIANT_WORDS }.distinct()
            return if (parts.isEmpty()) "Standard" else parts.joinToString(" ")
        }
    }
}

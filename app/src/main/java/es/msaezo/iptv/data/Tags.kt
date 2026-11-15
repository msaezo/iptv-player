package es.msaezo.iptv.data

import java.text.Normalizer

/**
 * Detects language / region / quality tags that IPTV providers embed in names and
 * category titles, e.g. "ES | ACCIÓN", "[EN] Dune 4K", "Dune (2021) MULTI", "UK: SPORTS".
 */
object Tags {

    /** Language codes used throughout the app. */
    val languageLabels: Map<String, String> = linkedMapOf(
        "es" to "Spanish (Spain)",
        "lat" to "Spanish (Latin America)",
        "en" to "English",
        "multi" to "Multi-language",
        "vose" to "Original + Spanish subs (VOSE)",
        "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese",
        "nl" to "Dutch", "pl" to "Polish", "ru" to "Russian", "tr" to "Turkish",
        "ar" to "Arabic", "hi" to "Hindi", "al" to "Albanian", "ro" to "Romanian",
        "gr" to "Greek", "sv" to "Swedish", "other" to "Other",
        UNKNOWN to "Untagged (usually English)",
    )
    const val UNKNOWN = "unknown"

    val shortLabels: Map<String, String> = mapOf(
        "es" to "ES", "lat" to "LAT", "en" to "EN", "multi" to "MULTI", "vose" to "VOSE",
        UNKNOWN to "?",
    )

    fun shortLabel(lang: String) = shortLabels[lang] ?: lang.uppercase()

    private val languageTokens: Map<String, String> = buildMap {
        listOf("ES", "ESP", "SPA", "SPAIN", "SPANISH", "ESPANA", "CASTELLANO", "CAST", "ESPANOL",
            "CASTILIAN", "ESPANHOL").forEach { put(it, "es") }
        listOf("LAT", "LATINO", "LATAM", "LATINOAMERICA", "MX", "MEX", "MEXICO", "ARG")
            .forEach { put(it, "lat") }
        listOf("EN", "ENG", "ENGLISH", "UK", "US", "USA", "GB", "INGLES", "CA", "AU")
            .forEach { put(it, "en") }
        listOf("MULTI", "MULTISUB", "MULTIAUDIO", "DUAL").forEach { put(it, "multi") }
        listOf("VOSE", "VOS", "VO", "SUBTITULADO", "SUBTITULADA", "SUBTITULADOS", "SUBTITULADAS", "SUBTITLED")
            .forEach { put(it, "vose") }
        listOf("FR", "FRA", "FRE", "FRENCH", "FRANCE").forEach { put(it, "fr") }
        listOf("DE", "GER", "GERMAN", "DEU", "DEUTSCH", "GERMANY").forEach { put(it, "de") }
        listOf("IT", "ITA", "ITALIAN", "ITALIA", "ITALY").forEach { put(it, "it") }
        listOf("PT", "POR", "PORTUGUESE", "BR", "BRA", "BRAZIL", "PORTUGAL").forEach { put(it, "pt") }
        listOf("NL", "DUTCH", "NED", "NEDERLAND", "BE").forEach { put(it, "nl") }
        listOf("PL", "POL", "POLISH", "POLSKA").forEach { put(it, "pl") }
        listOf("RU", "RUS", "RUSSIAN").forEach { put(it, "ru") }
        listOf("TR", "TUR", "TURK", "TURKISH", "TURKIYE").forEach { put(it, "tr") }
        listOf("AR", "ARA", "ARAB", "ARABIC").forEach { put(it, "ar") }
        listOf("IN", "HIN", "HINDI", "INDIA", "PUNJABI", "TAMIL").forEach { put(it, "hi") }
        listOf("AL", "ALB", "ALBANIA").forEach { put(it, "al") }
        listOf("RO", "ROM", "ROMANIA").forEach { put(it, "ro") }
        listOf("GR", "GRE", "GREEK").forEach { put(it, "gr") }
        listOf("SE", "SWE", "SWEDISH", "NORDIC", "DK", "NO", "FI").forEach { put(it, "sv") }
    }

    private val qualityTokens: Map<String, String> = mapOf(
        "4K" to "4K", "UHD" to "4K", "2160P" to "4K",
        "FHD" to "1080p", "1080P" to "1080p", "1080" to "1080p",
        "HD" to "720p", "720P" to "720p",
        "SD" to "SD", "480P" to "SD", "576P" to "SD",
    )
    private val qualityRank = mapOf("4K" to 4, "1080p" to 3, "720p" to 2, "SD" to 1)
    fun qualityRank(q: String?): Int = qualityRank[q] ?: 0

    /** Tokens that are noise when building a merge key for titles. */
    private val noiseTokens: Set<String> = qualityTokens.keys + setOf(
        "HEVC", "H265", "X265", "H264", "X264", "HDR", "HDR10", "ATMOS",
        "BLURAY", "WEBRIP", "WEBDL", "DUBBED", "SUBBED",
    )

    private val marks = Regex("\\p{Mn}+")

    private fun fold(s: String): String {
        var ascii = true
        for (c in s) if (c.code >= 128) { ascii = false; break }
        return if (ascii) s.uppercase()
        else Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").uppercase()
    }

    /** Upper-case, accent-free words made of letters, digits and '+'. */
    private fun tokens(s: String): List<String> {
        val f = fold(s)
        val out = ArrayList<String>(8)
        var start = -1
        for (i in f.indices) {
            val c = f[i]
            val ok = (c in 'A'..'Z') || (c in '0'..'9') || c == '+'
            if (ok) { if (start < 0) start = i } else if (start >= 0) { out.add(f.substring(start, i)); start = -1 }
        }
        if (start >= 0) out.add(f.substring(start))
        return out
    }

    // Category titles repeat for thousands of items: remember what was worked out for each.
    private val groupLanguage = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val groupQuality = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Streaming-service tags some providers put in front of titles (AZ = Amazon, HB = HBO…). */
    private val serviceTokens = setOf(
        "AZ", "AMZ", "AMZN", "NF", "NFX", "NFLX", "HB", "HBO", "HMAX", "DP", "DSN", "DNP",
        "AP", "ATV", "ATVP", "SKY", "PRM", "PMT", "VIP", "VOD",
    )

    /** Short codes that are safe to trust at the start or end of a category with no separator. */
    private val safeEdgeCodes = setOf("ES", "ESP", "ENG", "UK", "USA", "LAT", "GER", "ITA", "FRA")
    private val castilianWords = setOf("CASTELLANO", "CAST", "CASTILIAN")

    /** Pieces of an (already folded) category title between separators such as | [ ] ( ) : / */
    private fun segments(f: String): List<String> {
        val out = ArrayList<String>(4)
        val sb = StringBuilder()
        for (i in f.indices) {
            val c = f[i]
            val word = (c in 'A'..'Z') || (c in '0'..'9') || c == '+' || c == ' '
            val joiner = (c == '-' || c == '.' || c == '\'') && i > 0 && i < f.length - 1 &&
                f[i - 1].isLetterOrDigit() && f[i + 1].isLetterOrDigit()
            when {
                word -> sb.append(c)
                joiner -> sb.append(' ')
                else -> { if (sb.isNotBlank()) out.add(sb.toString()); sb.setLength(0) }
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    /**
     * Language from a category title. Full words ("CASTELLANO", "ENGLISH") count anywhere.
     * Short codes ("ES", "DE", "IN", "NO"…) only count where providers put tags: alone between
     * separators ("ES | ACCIÓN", "[EN]"), because inside a sentence they are ordinary words
     * ("PELICULAS DE ACCION", "MOVIES IN 4K").
     */
    fun languageOfGroup(group: String): String? {
        val cached = groupLanguage[group]
        if (cached != null) return cached.ifEmpty { null }
        val found = detectGroupLanguage(group)
        if (groupLanguage.size < 20_000) groupLanguage[group] = found ?: ""
        return found
    }

    private fun detectGroupLanguage(group: String): String? {
        val f = fold(group)
        val found = ArrayList<String>(2)
        var castilian = false
        for (seg in segments(f)) {
            val toks = seg.split(' ').filter { it.isNotEmpty() }
            val tagSegment = toks.size <= 3 &&
                toks.all { it in languageTokens || it in noiseTokens || it in serviceTokens }
            for (t in toks) {
                val l = languageTokens[t] ?: continue
                if (t.length >= 4 || tagSegment) {
                    found += l
                    if (t in castilianWords) castilian = true
                }
            }
        }
        if (found.isEmpty()) {
            val all = tokens(f)
            all.firstOrNull()?.takeIf { it in safeEdgeCodes }?.let { return languageTokens[it] }
            all.lastOrNull()?.takeIf { it in safeEdgeCodes }?.let { return languageTokens[it] }
            return null
        }
        if ("lat" in found && !castilian) return "lat"
        return found.first()
    }

    private fun qualityOfGroup(group: String): String? {
        val cached = groupQuality[group]
        if (cached != null) return cached.ifEmpty { null }
        var best: String? = null
        for (t in tokens(group)) {
            val q = qualityTokens[t] ?: continue
            if (qualityRank(q) > qualityRank(best)) best = q
        }
        if (groupQuality.size < 20_000) groupQuality[group] = best ?: ""
        return best
    }

    private val bracketRegex = Regex("""[\[(]([^\])]{1,20})[\])]""")
    private val prefixRegex = Regex("""^\s*\|?\s*([A-Za-z0-9+]{2,10})\s*(?:[|:]\s*|[-–]\s+)""")
    private val prefixPipeRegex = Regex("""^\s*\|([A-Za-z]{2,5})\|""")
    private val trailingTagRegex = Regex("""[\s\-_.]([A-Za-z]{2,10})\s*$""")
    private val spaces = Regex("\\s+")
    private val tagSplit = Regex("[^\\p{L}0-9+]+")

    /** Language of one tag as written: words always count, short codes only in capitals. */
    private fun langOfTag(written: String): String? {
        val f = fold(written)
        val l = languageTokens[f] ?: return null
        return if (f.length >= 4 || written.none { it.isLowerCase() }) l else null
    }

    /** True for a tag that carries no title text: language, quality, or a service code. */
    private fun isTag(written: String): Boolean {
        val f = fold(written)
        return f in noiseTokens || langOfTag(written) != null ||
            (f in serviceTokens && written.none { it.isLowerCase() })
    }

    /**
     * Language from an item name. Only looks where providers put tags (short prefixes
     * before a separator, bracketed tags, or a trailing tag) so titles like "Us" or "It"
     * are not mistaken for languages, and service codes like "AZ" or "HB" are skipped.
     */
    fun languageOfName(name: String): String? {
        prefixPipeRegex.find(name)?.let { m -> langOfTag(m.groupValues[1])?.let { return it } }
        var rest = name
        for (i in 0 until 3) {
            val m = prefixRegex.find(rest) ?: break
            val tag = m.groupValues[1]
            langOfTag(tag)?.let { return it }
            if (!isTag(tag)) break
            rest = rest.substring(m.range.last + 1)
        }
        for (m in bracketRegex.findAll(name)) {
            for (t in m.groupValues[1].split(tagSplit)) {
                if (t.isNotEmpty()) langOfTag(t)?.let { return it }
            }
        }
        trailingTagRegex.find(name)?.let { m ->
            val w = m.groupValues[1]
            // A trailing tag must be an explicit language word or a code in capitals.
            if (w.all { it.isUpperCase() } || w.length > 3) langOfTag(w)?.let { return it }
        }
        return null
    }

    fun language(name: String, group: String): String =
        languageOfName(name) ?: languageOfGroup(group) ?: UNKNOWN

    fun quality(name: String, group: String): String? {
        var best: String? = qualityOfGroup(group)
        for (t in tokens(name)) {
            val q = qualityTokens[t] ?: continue
            if (qualityRank(q) > qualityRank(best)) best = q
        }
        return best
    }

    /** Region of a live TV category, used for ordering: ES, UK, US or null. */
    fun regionOfLiveGroup(group: String): String? {
        val toks = tokens(group)
        for (t in toks) when (t) {
            "ES", "ESP", "SPAIN", "ESPANA", "SPANISH", "ESPANOL", "CASTELLANO", "MOVISTAR" -> return "ES"
            "UK", "GB", "BRITISH", "ENGLAND", "SCOTLAND" -> return "UK"
            "US", "USA", "AMERICA", "AMERICAN" -> return "US"
        }
        return null
    }

    /**
     * Key used to merge versions of the same title: lowercase, accents removed, provider
     * prefixes, language and quality tags and punctuation stripped.
     */
    fun mergeKey(title: String): String = keyOfDisplay(displayTitle(title), title)

    /** [mergeKey] for a title that already went through [displayTitle]. */
    fun keyOfDisplay(shown: String, original: String = shown): String =
        tokens(shown)
            .filter { it !in noiseTokens }
            .joinToString(" ")
            .lowercase()
            .ifEmpty { fold(original).lowercase().trim() }

    /** Display title with language / quality / service tags removed. */
    fun displayTitle(title: String): String {
        var s = prefixPipeRegex.replace(title, "")
        for (i in 0 until 3) {
            val m = prefixRegex.find(s) ?: break
            if (!isTag(m.groupValues[1])) break
            s = s.substring(m.range.last + 1)
        }
        // drop bracketed tags that are pure language/quality
        s = bracketRegex.replace(s) { m ->
            val parts = m.groupValues[1].split(tagSplit).filter { it.isNotEmpty() }
            if (parts.isNotEmpty() && parts.all { isTag(it) }) "" else m.value
        }
        // drop trailing quality/language words
        val words = s.trim().split(spaces).toMutableList()
        while (words.size > 1) {
            val w = fold(words.last()).trim('-', '|', '.', ',', ':')
            if (w.isEmpty() || w in noiseTokens || (w in languageTokens && words.last().all { !it.isLowerCase() })) {
                words.removeAt(words.size - 1)
            } else break
        }
        return words.joinToString(" ").trim().trimEnd('-', '|', ':', ',', '.').trim()
            .ifEmpty { title.trim() }
    }

    // --- genres from category names ---------------------------------------------------

    val genreOrder = listOf(
        "Action & Adventure", "Comedy", "Drama", "Thriller & Crime", "Horror", "Sci-Fi & Fantasy",
        "Romance", "Family & Animation", "Documentaries", "War & Western", "Music", "Anime", "Classics",
    )

    private val genreWords: Map<String, String> = buildMap {
        fun add(genre: String, vararg words: String) = words.forEach { put(it, genre) }
        add("Action & Adventure", "ACCION", "ACTION", "AVENTURA", "AVENTURAS", "ADVENTURE")
        add("Comedy", "COMEDIA", "COMEDIAS", "COMEDY", "HUMOR")
        add("Drama", "DRAMA", "DRAMAS")
        add("Thriller & Crime", "THRILLER", "THRILLERS", "SUSPENSE", "SUSPENSO", "INTRIGA", "CRIMEN", "CRIME",
            "POLICIACO", "POLICIACAS", "MISTERIO", "MYSTERY")
        add("Horror", "TERROR", "HORROR", "MIEDO")
        add("Sci-Fi & Fantasy", "FICCION", "SCIFI", "SCI", "FANTASIA", "FANTASY", "FANTASTICO")
        add("Romance", "ROMANCE", "ROMANTICA", "ROMANTICAS", "ROMANTICO", "ROMANTIC")
        add("Family & Animation", "ANIMACION", "ANIMATION", "INFANTIL", "INFANTILES", "KIDS", "NINOS", "FAMILIA",
            "FAMILIAR", "FAMILY", "CHILDREN", "DIBUJOS")
        add("Documentaries", "DOCUMENTAL", "DOCUMENTALES", "DOCUMENTARY", "DOCUMENTARIES", "DOCS", "DOCU")
        add("War & Western", "BELICA", "BELICAS", "BELICO", "WAR", "GUERRA", "WESTERN", "OESTE")
        add("Music", "MUSICAL", "MUSICALES", "MUSIC", "MUSICA", "CONCIERTOS", "CONCERTS")
        add("Anime", "ANIME")
        add("Classics", "CLASICOS", "CLASICAS", "CLASSICS", "CLASSIC")
    }
    private val groupGenre = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Genre a provider category is about ("ES | ACCIÓN" → Action & Adventure), if it says so. */
    fun genreOfGroup(group: String): String? {
        val cached = groupGenre[group]
        if (cached != null) return cached.ifEmpty { null }
        var found: String? = null
        for (t in tokens(group)) { found = genreWords[t]; if (found != null) break }
        if (groupGenre.size < 20_000) groupGenre[group] = found ?: ""
        return found
    }
}

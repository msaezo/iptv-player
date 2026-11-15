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
        UNKNOWN to "Untagged",
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
        listOf("VOSE", "VOS", "VO").forEach { put(it, "vose") }
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

    /** Language from a category title: every token counts ("ES | ACCIÓN", "PELICULAS CASTELLANO"). */
    fun languageOfGroup(group: String): String? {
        val cached = groupLanguage[group]
        if (cached != null) return cached.ifEmpty { null }
        var found: String? = null
        for (t in tokens(group)) { found = languageTokens[t]; if (found != null) break }
        if (groupLanguage.size < 20_000) groupLanguage[group] = found ?: ""
        return found
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
    private val prefixRegex = Regex("""^\s*\|?\s*([A-Za-z\-]{2,12})\s*[|:\-]\s+""")
    private val prefixPipeRegex = Regex("""^\s*\|([A-Za-z]{2,5})\|""")
    private val trailingTagRegex = Regex("""[\s\-_.]([A-Za-z]{2,10})\s*$""")

    /**
     * Language from an item name. Only looks where providers put tags (a short prefix
     * before a separator, bracketed tags, or a trailing tag) so titles like "Us" or "It"
     * are not mistaken for languages.
     */
    fun languageOfName(name: String): String? {
        prefixPipeRegex.find(name)?.let { m -> languageTokens[fold(m.groupValues[1])]?.let { return it } }
        prefixRegex.find(name)?.let { m ->
            for (t in tokens(m.groupValues[1])) languageTokens[t]?.let { return it }
        }
        for (m in bracketRegex.findAll(name)) {
            for (t in tokens(m.groupValues[1])) languageTokens[t]?.let { return it }
        }
        trailingTagRegex.find(name)?.let { m ->
            val t = fold(m.groupValues[1])
            // Trailing two-letter words are risky ("Toy Story 2 - IT"? fine) but only accept
            // explicit language words or uppercase codes as written.
            if (m.groupValues[1].all { it.isUpperCase() } || t.length > 3) {
                languageTokens[t]?.let { return it }
            }
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
    fun mergeKey(title: String): String =
        tokens(displayTitle(title))
            .filter { it !in noiseTokens }
            .joinToString(" ")
            .lowercase()
            .ifEmpty { fold(title).lowercase().trim() }

    /** Display title with language / quality tags removed. */
    fun displayTitle(title: String): String {
        var s = title
        s = prefixPipeRegex.replace(s, "")
        prefixRegex.find(s)?.let { m ->
            if (tokens(m.groupValues[1]).any { it in languageTokens || it in noiseTokens }) {
                s = s.substring(m.range.last + 1)
            }
        }
        // drop bracketed tags that are pure language/quality
        s = bracketRegex.replace(s) { m ->
            val toks = tokens(m.groupValues[1])
            if (toks.isNotEmpty() && toks.all { it in languageTokens || it in noiseTokens }) "" else m.value
        }
        // drop trailing quality/language words
        val words = s.trim().split(Regex("\\s+")).toMutableList()
        while (words.size > 1) {
            val w = fold(words.last()).trim('-', '|', '.', ',', ':')
            if (w.isEmpty() || w in noiseTokens || (w in languageTokens && words.last().all { !it.isLowerCase() })) {
                words.removeAt(words.size - 1)
            } else break
        }
        return words.joinToString(" ").trim().trimEnd('-', '|', ':', ',', '.').trim()
            .ifEmpty { title.trim() }
    }
}

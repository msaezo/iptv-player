package es.msaezo.iptv.data

import java.io.BufferedReader

/**
 * Streaming M3U / M3U8 (extended) parser.
 *
 * Handles #EXTINF attributes (tvg-id, tvg-name, tvg-logo, group-title), #EXTGRP and
 * #EXTVLCOPT user-agent / referrer lines, then classifies each item as live TV,
 * movie or series episode.
 */
object M3uParser {

    data class Result(val entries: List<Entry>, val epgUrl: String?)

    private val fileExtRegex =
        Regex("""\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|ts|m3u8)\s*$""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(reader: BufferedReader): Result {
        val entries = ArrayList<Entry>(8192)
        var epgUrl: String? = null
        var info: String? = null
        var extGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        var first = true

        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            line = line.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: epgUrl
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    extGroup = null
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    extGroup = line.substring(8).trim().ifEmpty { null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substring(11).trim()
                    when {
                        opt.startsWith("http-user-agent=", true) ->
                            userAgent = opt.substringAfter('=').trim().ifEmpty { null }
                        opt.startsWith("http-referrer=", true) ||
                            opt.startsWith("http-referer=", true) ->
                            referrer = opt.substringAfter('=').trim().ifEmpty { null }
                    }
                }
                line.startsWith("#") -> Unit // other directives/comments
                else -> {
                    entries += buildEntry(info, line, extGroup, userAgent, referrer)
                    info = null
                    extGroup = null
                    userAgent = null
                    referrer = null
                }
            }
        }
        return Result(entries, epgUrl)
    }

    fun parseAttributes(line: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (m in attrRegex.findAll(line)) {
            map[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        }
        return map
    }

    internal fun buildEntry(
        info: String?,
        url: String,
        extGroup: String?,
        userAgent: String?,
        referrer: String?,
    ): Entry {
        var header = ""
        var title = ""
        var duration: Double? = null
        if (info != null) {
            val split = splitTitle(info)
            header = split.first
            title = split.second.trim()
            val afterColon = header.substringAfter(':', "")
            duration = afterColon.trimStart().takeWhile { it == '-' || it == '.' || it.isDigit() }
                .toDoubleOrNull()
        }
        val attrs = if (header.isNotEmpty()) parseAttributes(header) else emptyMap()
        val rawName = title.ifEmpty { attrs["tvg-name"].orEmpty() }
            .ifEmpty { fileNameOf(url) }
            .replace(fileExtRegex, "")
        val logo = (attrs["tvg-logo"] ?: attrs["logo"])?.takeIf { it.isNotBlank() }
        val group = (attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup)
            ?.trim()?.ifEmpty { null } ?: "Uncategorized"

        return Classifier.classify(
            rawName = rawName,
            url = url,
            logo = logo,
            group = group,
            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            duration = duration,
            userAgent = userAgent,
            referrer = referrer,
        )
    }

    /** Splits "#EXTINF:-1 a="x,y" b="z",Title, with comma" at the first comma outside quotes. */
    internal fun splitTitle(info: String): Pair<String, String> {
        var inQuotes = false
        for (i in info.indices) {
            val c = info[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return info.substring(0, i) to info.substring(i + 1)
        }
        return info to ""
    }

    private fun fileNameOf(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifEmpty { url }
}

object Classifier {

    private val vodExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "divx", "3gp", "ogv",
    )

    private val episodePatterns = listOf(
        Regex("""(?i)\bS(\d{1,2})\s*[ ._-]?\s*E(\d{1,3})(?!\d)"""),
        Regex("""(?i)\bT(\d{1,2})\s*[ ._-]?\s*E(\d{1,3})(?!\d)"""),
        Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b"""),
        Regex("""(?i)\bSeason\s*(\d{1,2})\s*[,.\-: ]*\s*(?:Episode|Ep\.?)\s*(\d{1,3})"""),
        Regex("""(?i)\bTemporada\s*(\d{1,2})\s*[,.\-: ]*\s*(?:Episodio|Cap[ií]tulo|Cap\.?|Ep\.?)\s*(\d{1,3})"""),
    )

    private val yearRegex = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val trailingYearRegex = Regex("""\s+[-–]\s*((?:19|20)\d{2})\s*$""")
    private val prefixRegex = Regex("""^\s*[A-Za-z0-9+]{2,5}\s*[|:]\s*""")
    private val vodWord = Regex("""(?i)(^|[^a-z])vod([^a-z]|$)""")
    private val seriesWord = Regex("""(?i)(series|serie\b|séries|tv ?shows?|temporada|season)""")
    private val loopWord = Regex("""24\s*/\s*7|24-7|24h""", RegexOption.IGNORE_CASE)

    data class EpisodeMatch(val show: String, val season: Int, val episode: Int)

    fun matchEpisode(name: String): EpisodeMatch? {
        for (p in episodePatterns) {
            val m = p.find(name) ?: continue
            val season = m.groupValues[1].toIntOrNull() ?: continue
            val episode = m.groupValues[2].toIntOrNull() ?: continue
            val show = cleanTitle(name.substring(0, m.range.first))
            return EpisodeMatch(show, season, episode)
        }
        return null
    }

    /** Removes provider prefixes like "ES | " and trailing separators. */
    fun cleanTitle(s: String): String =
        s.replace(prefixRegex, "")
            .trim()
            .trimEnd('-', '–', '_', '.', '|', ':', '(', '[', ',', ' ')
            .trim()

    private fun extension(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        return if (last.contains('.')) last.substringAfterLast('.').lowercase() else ""
    }

    fun classify(
        rawName: String,
        url: String,
        logo: String?,
        group: String,
        tvgId: String?,
        duration: Double?,
        userAgent: String?,
        referrer: String?,
    ): Entry {
        val lowerUrl = url.lowercase()
        val ep = matchEpisode(rawName)
        val isLoopChannel = loopWord.containsMatchIn(group) || loopWord.containsMatchIn(rawName)
        val seriesGroup = seriesWord.containsMatchIn(group) && !isLoopChannel
        val isVodFile = extension(url) in vodExtensions

        val type: ContentType = when {
            "/series/" in lowerUrl -> ContentType.SERIES
            "/movie/" in lowerUrl || "/movies/" in lowerUrl ->
                if (ep != null) ContentType.SERIES else ContentType.MOVIE
            "/live/" in lowerUrl -> ContentType.LIVE
            isLoopChannel -> ContentType.LIVE
            isVodFile || (duration != null && duration > 0) ->
                if (ep != null || seriesGroup) ContentType.SERIES else ContentType.MOVIE
            ep != null -> ContentType.SERIES
            vodWord.containsMatchIn(group) ->
                if (seriesGroup) ContentType.SERIES else ContentType.MOVIE
            else -> ContentType.LIVE
        }

        val language = if (type == ContentType.LIVE) Tags.languageOfGroup(group) ?: Tags.UNKNOWN
        else Tags.language(rawName, group)
        val quality = Tags.quality(rawName, group)

        return when (type) {
            ContentType.LIVE -> Entry(
                title = rawName.trim(), rawName = rawName, url = url, logo = logo, group = group,
                tvgId = tvgId, type = type, language = language, quality = quality,
                userAgent = userAgent, referrer = referrer,
            )
            ContentType.MOVIE -> {
                val (title, year) = splitYear(rawName)
                Entry(
                    title = Tags.displayTitle(title), rawName = rawName, url = url, logo = logo,
                    group = group, tvgId = tvgId, type = type, year = year, language = language,
                    quality = quality, userAgent = userAgent, referrer = referrer,
                )
            }
            ContentType.SERIES -> {
                val show = ep?.show?.takeIf { it.isNotBlank() }
                    ?.let { Tags.displayTitle(splitYear(it).first) }
                    ?: Tags.displayTitle(cleanTitle(rawName)).ifEmpty { group }
                val epTitle = if (ep != null) {
                    val m = episodeTail(rawName)
                    if (m.isNotBlank()) m else "Episode ${ep.episode}"
                } else Tags.displayTitle(cleanTitle(rawName))
                Entry(
                    title = epTitle, rawName = rawName, url = url, logo = logo, group = group,
                    tvgId = tvgId, type = type, showName = show, season = ep?.season ?: 1,
                    episode = ep?.episode, language = language, quality = quality,
                    userAgent = userAgent, referrer = referrer,
                )
            }
        }
    }

    /** Episode title from a raw name: text after the SxxEyy marker, or the whole name. */
    fun episodeTitle(name: String): String {
        for (p in episodePatterns) if (p.containsMatchIn(name)) return episodeTail(name)
        return Tags.displayTitle(cleanTitle(name))
    }

    /** Text after the SxxEyy marker, e.g. "Pilot" in "Show S01E01 - Pilot". */
    private fun episodeTail(name: String): String {
        for (p in episodePatterns) {
            val m = p.find(name) ?: continue
            val tail = name.substring(m.range.last + 1).trim().trimStart('-', '–', ':', '|', '.', ' ')
            return Tags.displayTitle(tail).takeIf { tail.isNotBlank() } ?: ""
        }
        return ""
    }

    fun splitYear(name: String): Pair<String, Int?> {
        val m = yearRegex.find(name) ?: trailingYearRegex.find(name)
        val year = m?.groupValues?.get(1)?.toIntOrNull()
        val stripped = if (m != null) name.removeRange(m.range) else name
        return cleanTitle(stripped).ifEmpty { name.trim() } to year
    }
}

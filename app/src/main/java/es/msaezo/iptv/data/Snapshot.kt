package es.msaezo.iptv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * The organised catalogue saved on disk, so the app opens without downloading or
 * re-classifying anything. A refresh is applied on top of it.
 */
class Snapshot(
    val source: String,
    val savedAt: Long,
    val epgUrl: String?,
    val entries: List<Entry>,
    val series: List<SeriesSource>,
) {
    companion object {
        private const val VERSION = 0x54555602

        fun write(file: File, s: Snapshot) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
                o.writeInt(VERSION)
                o.str(s.source)
                o.writeLong(s.savedAt)
                o.opt(s.epgUrl)
                val groups = LinkedHashMap<String, Int>()
                for (e in s.entries) groups.getOrPut(e.group) { groups.size }
                for (x in s.series) groups.getOrPut(x.group) { groups.size }
                o.writeInt(groups.size)
                for (g in groups.keys) o.str(g)
                o.writeInt(s.entries.size)
                for (e in s.entries) {
                    o.writeByte(e.type.ordinal)
                    o.str(e.rawName)
                    if (e.title == e.rawName) o.writeBoolean(false) else { o.writeBoolean(true); o.str(e.title) }
                    o.str(e.url)
                    o.opt(e.logo)
                    o.writeInt(groups[e.group]!!)
                    o.opt(e.tvgId)
                    o.writeInt(e.year ?: -1)
                    o.opt(e.showName)
                    o.writeInt(e.season ?: -1)
                    o.writeInt(e.episode ?: -1)
                    o.str(e.language)
                    o.opt(e.quality)
                    o.writeDouble(e.rating ?: -1.0)
                    o.str(e.key)
                    o.opt(e.userAgent)
                    o.opt(e.referrer)
                }
                o.writeInt(s.series.size)
                for (x in s.series) {
                    o.str(x.seriesId); o.str(x.rawName)
                    if (x.title == x.rawName) o.writeBoolean(false) else { o.writeBoolean(true); o.str(x.title) }
                    o.writeInt(groups[x.group]!!)
                    o.opt(x.cover)
                    o.writeDouble(x.rating ?: -1.0)
                    o.str(x.language)
                    o.writeInt(x.year ?: -1)
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true); tmp.delete()
            }
        }

        /** Returns null when there is no usable snapshot for [source]. */
        fun read(file: File, source: String): Snapshot? {
            if (!file.exists()) return null
            return try {
                DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { i ->
                    if (i.readInt() != VERSION) return null
                    if (i.readUTF() != source) return null
                    val savedAt = i.readLong()
                    val epg = i.opt()
                    val groups = Array(i.readInt()) { i.readUTF() }
                    val small = HashMap<String, String>() // languages / qualities repeat a lot
                    fun shared(v: String) = small.getOrPut(v) { v }
                    val types = ContentType.entries
                    val n = i.readInt()
                    val entries = ArrayList<Entry>(n)
                    repeat(n) {
                        val type = types[i.readByte().toInt()]
                        val raw = i.readUTF()
                        val title = if (i.readBoolean()) i.readUTF() else raw
                        val url = i.readUTF()
                        val logo = i.opt()
                        val group = groups[i.readInt()]
                        val tvg = i.opt()
                        val year = i.readInt().takeIf { it >= 0 }
                        val show = i.opt()
                        val season = i.readInt().takeIf { it >= 0 }
                        val episode = i.readInt().takeIf { it >= 0 }
                        val lang = shared(i.readUTF())
                        val quality = i.opt()?.let(::shared)
                        val rating = i.readDouble().takeIf { it >= 0 }
                        val key = i.readUTF()
                        val ua = i.opt()
                        val ref = i.opt()
                        entries += Entry(
                            title = title, rawName = raw, url = url, logo = logo, group = group, tvgId = tvg,
                            type = type, year = year, showName = show, season = season, episode = episode,
                            language = lang, quality = quality, rating = rating, key = key,
                            userAgent = ua, referrer = ref,
                        )
                    }
                    val m = i.readInt()
                    val series = ArrayList<SeriesSource>(m)
                    repeat(m) {
                        val id = i.readUTF()
                        val raw = i.readUTF()
                        val title = if (i.readBoolean()) i.readUTF() else raw
                        val group = groups[i.readInt()]
                        val cover = i.opt()
                        val rating = i.readDouble().takeIf { it >= 0 }
                        val lang = shared(i.readUTF())
                        val year = i.readInt().takeIf { it >= 0 }
                        series += SeriesSource(id, raw, title, group, cover, rating, lang, year)
                    }
                    Snapshot(source, savedAt, epg, entries, series)
                }
            } catch (e: Exception) {
                null
            }
        }

        // writeUTF is limited to 64 KB per string; nothing legitimate comes close.
        private fun DataOutputStream.str(v: String) = writeUTF(if (v.length > 8000) v.substring(0, 8000) else v)
        private fun DataOutputStream.opt(v: String?) {
            if (v == null) writeBoolean(false) else { writeBoolean(true); str(v) }
        }
        private fun DataInputStream.opt(): String? = if (readBoolean()) readUTF() else null
    }
}

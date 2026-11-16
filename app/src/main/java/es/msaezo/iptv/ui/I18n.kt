package es.msaezo.iptv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The app's text in the chosen language. English is the source; Spanish is looked up here. */
fun tr(text: String): String = I18n.tr(text)

object I18n {
    /** "en" or "es". Screens that show text re-draw when it changes. */
    var lang by mutableStateOf("en")

    // Every piece of text on screen passes through here, many times a second while
    // scrolling, so each answer (including "no translation") is remembered.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var cachedFor = ""

    fun tr(text: String): String {
        val l = lang
        if (l != "es") return text
        if (cachedFor != l) { cache.clear(); cachedFor = l }
        cache[text]?.let { return it }
        val out = translate(text)
        if (cache.size > 8000) cache.clear()
        cache[text] = out
        return out
    }

    private fun translate(text: String): String {
        val core = text.trim()
        if (core.isEmpty()) return text
        val out = es[core]
            ?: patterns.firstNotNullOfOrNull { (r, f) -> r.matchEntire(core)?.let(f) }
            ?: return text
        return if (core.length == text.length) out else text.replace(core, out)
    }

    private fun p(regex: String, f: (List<String>) -> String): Pair<Regex, (MatchResult) -> String> =
        Regex(regex, RegexOption.DOT_MATCHES_ALL) to { m: MatchResult -> f(m.groupValues) }

    private val patterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        p("""Season (\d+)""") { "Temporada ${it[1]}" },
        p("""(\d+) episodes""") { "${it[1]} episodios" },
        p("""(\d+) titles""") { "${it[1]} títulos" },
        p("""(\d+) seasons?""") { if (it[1] == "1") "1 temporada" else "${it[1]} temporadas" },
        p("""(\d+) seasons? · (\d+) ep""") { "${it[1]} temp. · ${it[2]} ep." },
        p("""(\d+) sources""") { "${it[1]} fuentes" },
        p("""(.+) · (\d+) sources""") { "${it[1]} · ${it[2]} fuentes" },
        p("""Play (S\d+E\d+)""") { "Ver ${it[1]}" },
        p("""Continue (S\d+E\d+)""") { "Continuar ${it[1]}" },
        p("""Next: (.+)""") { "Siguiente: ${it[1]}" },
        p("""Resume (.+)""") { "Reanudar ${it[1]}" },
        p("""Because you watched (.+)""") { "Porque viste ${it[1]}" },
        p("""Most watched of (\d+)""") { "Lo más visto de ${it[1]}" },
        p("""Most watched series of (\d+)""") { "Series más vistas de ${it[1]}" },
        p("""New in (\d+)""") { "Novedades de ${it[1]}" },
        p("""(.+) · Series""") { "${tr(it[1])} · Series" },
        p("""Recently added series""") { "Series añadidas recientemente" },
        p("""Recently updated series""") { "Series actualizadas recientemente" },
        p("""Versions \((\d+)\)""") { "Versiones (${it[1]})" },
        p("""until (.+)""") { "hasta las ${it[1]}" },
        p("""Loading (.+)…""") { "Cargando ${it[1]}…" },
        p("""Watched to (.+)""") { "Visto hasta ${it[1]}" },
        p("""Only in (.+)""") { "Solo en ${it[1]}" },
        p("""Now  (.+?)  ·  until (\S+)(.*)""") { "Ahora  ${it[1]}  ·  hasta las ${it[2]}${it[3].replace("Next  ", "Después  ")}" },
        p("""Now  (.+)""") { "Ahora  ${it[1]}" },
        p("""(.+?)  (\d+)""") { "${tr(it[1])}  ${it[2]}" },
        p("""(.+?)  ×""") { "${it[1]}  ×" },
        p("""Couldn't load the playlist:\n(.*)""") { "No se pudo cargar la lista:\n${it[1]}" },
        p("""Updating catalogue…\s+([\d.,]+) channels · ([\d.,]+) movies · ([\d.,]+) series""") {
            "Actualizando el catálogo…  ${it[1]} canales · ${it[2]} películas · ${it[3]} series"
        },
        p("""Updating catalogue…(.*)""") { "Actualizando el catálogo…${it[1]}" },
        p("""Organising (\d+) items…""") { "Organizando ${it[1]} elementos…" },
        p("""Error: (.*)""") { "Error: ${it[1]}" },
        p("""Loaded (\d+) streams → (\d+) channels, (\d+) movies, (\d+) series \(after filter\)""") {
            "${it[1]} emisiones cargadas → ${it[2]} canales, ${it[3]} películas, ${it[4]} series (con el filtro)"
        },
        p("""Loaded (\d+) streams""") { "${it[1]} emisiones cargadas" },
        p("""Refreshing in the background: (.*)\(you can keep using the app\)""") {
            "Actualizando en segundo plano: ${tr(it[1].trim())}  (puedes seguir usando la app)"
        },
        p("""Refresh failed, still showing the saved catalogue: (.*)""") {
            "La actualización ha fallado; se sigue mostrando el catálogo guardado: ${it[1]}"
        },
        p("""Catalogue saved on this device, updated less than an hour ago\. It refreshes by itself once a day\.""") {
            "Catálogo guardado en este dispositivo, actualizado hace menos de una hora. Se actualiza solo una vez al día."
        },
        p("""Catalogue saved on this device, updated (\d+) h ago\. It refreshes by itself once a day\.""") {
            "Catálogo guardado en este dispositivo, actualizado hace ${it[1]} h. Se actualiza solo una vez al día."
        },
        p("""Catalogue saved on this device, updated (\d+) days ago\. It refreshes by itself once a day\.""") {
            "Catálogo guardado en este dispositivo, actualizado hace ${it[1]} días. Se actualiza solo una vez al día."
        },
        p("""Couldn't check your location(.*)""") { "No se pudo comprobar tu ubicación${it[1]}" },
        p("""Last error: (.*)""") { "Último error: ${it[1]}" },
        p("""No results for (.*)""") { "Sin resultados para ${it[1]}" },
        p("""Address   (.*)""") { "Dirección   ${it[1]}" },
        p("""Code   (.*)""") { "Código   ${it[1]}" },
        p("""(\d+)% from the bottom""") { "${it[1]}% desde abajo" },
        p("""Couldn't copy: (.*?)\. Check the address, the code, and that both are on the same Wi-Fi\.""") {
            "No se pudo copiar: ${it[1]}. Comprueba la dirección, el código y que los dos estén en la misma Wi-Fi."
        },
        p("""\+(\d+) ms  ·  sound later""") { "+${it[1]} ms  ·  sonido más tarde" },
        p("""(-\d+) ms  ·  sound earlier""") { "${it[1]} ms  ·  sonido antes" },
        p("""Audio \+(\d+) ms  ·  sound later""") { "Audio +${it[1]} ms  ·  sonido más tarde" },
        p("""Audio (-\d+) ms  ·  sound earlier""") { "Audio ${it[1]} ms  ·  sonido antes" },
        p("""Reconnecting… \((.*)\)""") { "Reconectando… (${it[1]})" },
        p("""Trying again… \((.*)\)""") { "Reintentando… (${it[1]})" },
        p("""Resuming from (.*)""") { "Reanudando desde ${it[1]}" },
        p("""Next episode   (.*)""") { "Siguiente episodio   ${it[1]}" },
        p("""Skip intro   (.*)""") { "Saltar intro   ${it[1]}" },
        p("""Press (.) again when the intro ends""") { "Pulsa ${it[1]} otra vez cuando acabe la intro" },
        p("""Press (.) when the intro starts to skip it next time""") { "Pulsa ${it[1]} cuando empiece la intro para saltarla la próxima vez" },
        p("""Your provider refused this stream \(HTTP (\d+)\)\..*""") {
            "Tu proveedor ha rechazado esta emisión (HTTP ${it[1]}).\n\nSuele ser su límite de conexiones: otro dispositivo, " +
                "o una emisión recién cerrada, todavía cuenta. Espera unos segundos y reintenta. " +
                "Una VPN que ha cambiado de servidor también puede causarlo."
        },
        p("""The provider answered HTTP (\d+)\.""") { "El proveedor ha respondido HTTP ${it[1]}." },
        p("""(\d+) seconds""") { "${it[1]} segundos" },
        p("""Add to (.+)""") { "Añadir a ${it[1]}" },
        p("""Remove from (.+)""") { "Quitar de ${it[1]}" },
        p("""Added to (.+)""") { "Añadido a ${it[1]}" },
        p("""Removed from (.+)""") { "Quitado de ${it[1]}" },
        p("""(\d+) fixtures found(.*)""") { "${it[1]} partidos encontrados${it[2]}" },
        p("""Couldn't read the fixtures: (.*)""") { "No se pudieron leer los partidos: ${it[1]}" },
    )

    private val es: Map<String, String> = mapOf(
        // navigation
        "Home" to "Inicio", "Movies" to "Películas", "Series" to "Series", "Live TV" to "TV en directo",
        "Search" to "Buscar", "Settings" to "Ajustes",
        // home and rows
        "Continue watching" to "Seguir viendo", "My movies" to "Mis películas", "My series" to "Mis series",
        "My channels" to "Mis canales", "Recent channels" to "Canales recientes", "Following" to "Siguiendo",
        "Recently added" to "Añadido recientemente", "Recently updated" to "Actualizado recientemente",
        "Top rated" to "Mejor valoradas", "More like this" to "Más como esto", "See all ›" to "Ver todo ›",
        "Play" to "Ver", "More info" to "Más información", "SERIES" to "SERIE", "FILM" to "PELÍCULA",
        "Live" to "En directo", "Continue" to "Continuar",
        "Unlock collections & ratings" to "Activa colecciones y valoraciones",
        "Add a free TMDB key (and OMDb for Rotten Tomatoes / IMDb) in Settings to get rows like Trending, Best Picture winners or On Netflix, plus synopses and backdrops."
            to "Añade una clave gratuita de TMDB (y de OMDb para Rotten Tomatoes / IMDb) en Ajustes para tener filas como Tendencias, Ganadoras del Óscar o En Netflix, además de sinopsis e imágenes de fondo.",
        "Finding your collections…" to "Buscando tus colecciones…",
        "Search channels, movies, shows…" to "Busca canales, películas, series…",
        "Type at least 2 letters. Accents don't matter." to "Escribe al menos 2 letras. Los acentos no importan.",
        // TMDB collections
        "Trending this week" to "Tendencias de la semana", "In cinemas now" to "En cines ahora",
        "Popular right now" to "Populares ahora", "All-time classics" to "Clásicos de siempre",
        "Best Picture winners" to "Ganadoras del Óscar a mejor película",
        "On Netflix" to "En Netflix", "On Prime Video" to "En Prime Video", "On Disney+" to "En Disney+",
        "On Max" to "En Max", "On Movistar Plus+" to "En Movistar Plus+", "On Apple TV+" to "En Apple TV+",
        "Trending series" to "Series en tendencia", "Popular series" to "Series populares",
        "Acclaimed series" to "Series aclamadas", "Netflix series" to "Series de Netflix",
        "Prime Video series" to "Series de Prime Video", "Disney+ series" to "Series de Disney+",
        "HBO / Max series" to "Series de HBO / Max", "Movistar Plus+ series" to "Series de Movistar Plus+",
        "Apple TV+ series" to "Series de Apple TV+",
        // genres
        "Action & Adventure" to "Acción y aventura", "Comedy" to "Comedia", "Drama" to "Drama",
        "Thriller & Crime" to "Thriller y crimen", "Horror" to "Terror", "Sci-Fi & Fantasy" to "Ciencia ficción y fantasía",
        "Romance" to "Romance", "Family & Animation" to "Familia y animación", "Documentaries" to "Documentales",
        "War & Western" to "Bélico y western", "Music" to "Música", "Anime" to "Anime", "Classics" to "Clásicos",
        // live tv
        "★ Favorites" to "★ Favoritos", "Recently watched" to "Vistos recientemente", "Sports" to "Deportes",
        "Events" to "Eventos", "Spain" to "España", "UK" to "Reino Unido", "USA" to "EE. UU.",
        "International" to "Internacional", "Provider packs" to "Packs del proveedor", "All" to "Todo",
        "Football" to "Fútbol", "Tennis" to "Tenis", "Basketball" to "Baloncesto", "Formula 1" to "Fórmula 1",
        "Motorbikes" to "Motos", "Other sports" to "Otros deportes", "Announced" to "Anunciados", "Other" to "Otros",
        "General" to "Generalistas", "Movies & Series" to "Cine y series", "Kids" to "Infantil", "News" to "Noticias",
        "Adults" to "Adultos", "No event announced" to "Sin evento anunciado",
        "No programme information" to "Sin información de programación", "Organising channels…" to "Organizando canales…",
        // languages
        "Spanish (Spain)" to "Español (España)", "Spanish (Latin America)" to "Español (Latinoamérica)",
        "English" to "Inglés", "Multi-language" to "Multiidioma", "Original + Spanish subs (VOSE)" to "V.O. subtitulada (VOSE)",
        "French" to "Francés", "German" to "Alemán", "Italian" to "Italiano", "Portuguese" to "Portugués",
        "Dutch" to "Neerlandés", "Polish" to "Polaco", "Russian" to "Ruso", "Turkish" to "Turco", "Arabic" to "Árabe",
        "Hindi" to "Hindi", "Albanian" to "Albanés", "Romanian" to "Rumano", "Greek" to "Griego", "Swedish" to "Sueco",
        "Untagged (usually English)" to "Sin etiqueta (normalmente inglés)",
        // title pages
        "Start over" to "Desde el principio", "Trailer" to "Tráiler", "My list" to "Mi lista", "In my list" to "En mi lista",
        "Audio / version" to "Audio / versión", "Director" to "Dirección", "Created by" to "Creada por", "Cast" to "Reparto",
        "This title is no longer in your filtered playlist" to "Este título ya no está en tu lista filtrada",
        "This series is no longer in your filtered playlist" to "Esta serie ya no está en tu lista filtrada",
        "Collection not available" to "Colección no disponible",
        "Metadata and images from TMDB. Ratings via OMDb. This product uses the TMDB API but is not endorsed or certified by TMDB."
            to "Metadatos e imágenes de TMDB. Valoraciones a través de OMDb. Este producto usa la API de TMDB, pero no está avalado ni certificado por TMDB.",
        // states
        "Add your M3U playlist to get started" to "Añade tu lista M3U para empezar", "Open Settings" to "Abrir Ajustes",
        "Retry" to "Reintentar", "Preparing…" to "Preparando…", "Signing in…" to "Iniciando sesión…",
        "Opening your catalogue…" to "Abriendo tu catálogo…", "Reading playlist…" to "Leyendo la lista…",
        "No movies with your current language filter" to "No hay películas con tu filtro de idiomas",
        "No series with your current language filter" to "No hay series con tu filtro de idiomas",
        "Nothing here with your current language filter" to "No hay nada aquí con tu filtro de idiomas",
        // settings
        "App language" to "Idioma de la app",
        "Your connection" to "Tu conexión", "Checking where you appear to be…" to "Comprobando desde dónde apareces…",
        "This is where websites and your provider see you. With the VPN on it should show the VPN's country."
            to "Aquí es donde te ven las webs y tu proveedor. Con la VPN activada debería salir el país de la VPN.",
        "Checking…" to "Comprobando…", "Check again" to "Comprobar otra vez",
        "Playlist (M3U)" to "Lista (M3U)", "Save & load" to "Guardar y cargar", "Refresh now" to "Actualizar ahora",
        "Open .m3u file" to "Abrir archivo .m3u", "No playlist yet." to "Todavía no hay lista.",
        "Using a local file." to "Usando un archivo local.",
        "Languages for Movies & Series" to "Idiomas de películas y series",
        "Live TV is never filtered. Spanish channels are listed first, then UK, then US."
            to "La TV en directo nunca se filtra. Primero salen los canales españoles, luego Reino Unido y luego EE. UU.",
        "Collections & ratings" to "Colecciones y valoraciones",
        "TMDB (free): themoviedb.org → Settings → API → request a key. Paste the API key or the read access token."
            to "TMDB (gratis): themoviedb.org → Ajustes → API → solicita una clave. Pega la clave de API o el token de acceso de lectura.",
        "OMDb (free, 1000/day): omdbapi.com/apikey.aspx — adds Rotten Tomatoes, IMDb and Metacritic scores."
            to "OMDb (gratis, 1000 al día): omdbapi.com/apikey.aspx — añade las notas de Rotten Tomatoes, IMDb y Metacritic.",
        "TMDB API key" to "Clave de API de TMDB", "OMDb API key" to "Clave de API de OMDb", "Save keys" to "Guardar claves",
        "Playback" to "Reproducción", "Preview on a title's page" to "Avance en la página del título",
        "Plays the opening minutes without sound behind the title. Uses your stream connection while it runs."
            to "Reproduce los primeros minutos sin sonido detrás del título. Usa tu conexión de emisión mientras dura.",
        "Audio sync" to "Sincronía del audio", "Reset" to "Restablecer", "0 ms" to "0 ms",
        "If lips and sound don't match: sound arriving late needs a negative value. While a film is playing, up / down on the remote changes it in 25 ms steps (limit ±500 ms)."
            to "Si los labios y el sonido no coinciden: si el sonido llega tarde, usa un valor negativo. Durante una película, arriba / abajo en el mando lo cambia en pasos de 25 ms (límite ±500 ms).",
        "Live TV buffer" to "Búfer de la TV en directo", "Fast start" to "Arranque rápido",
        "Loads this much ahead before a channel starts, so a busy server (a big match) is less likely to make it stop. The channel takes that long to appear and you watch that far behind live."
            to "Carga este tiempo por adelantado antes de empezar, para que un servidor saturado (un gran partido) tenga menos probabilidad de cortarlo. El canal tarda eso en aparecer y lo ves con ese retraso respecto al directo.",
        "Some providers only accept certain players. Change the user agent if streams refuse to play."
            to "Algunos proveedores solo aceptan ciertos reproductores. Cambia el user agent si las emisiones no arrancan.",
        "User agent" to "User agent", "Save" to "Guardar", "Clear watch history" to "Borrar historial",
        "Subtitles" to "Subtítulos", "This is how subtitles will look" to "Así se verán los subtítulos",
        "Font" to "Tipo de letra", "Serif" to "Con serifa", "Sans" to "Sin serifa", "Size" to "Tamaño",
        "Background" to "Fondo", "None" to "Ninguno", "Soft" to "Suave", "Dark" to "Oscuro",
        "Height on screen" to "Altura en pantalla", "Lower" to "Más abajo", "Higher" to "Más arriba",
        "Applies from the next thing you play." to "Se aplica a partir de lo siguiente que reproduzcas.",
        "Teams and players to look out for. Home shows a Following row with channels and programmes that mention them. Select a name to remove it."
            to "Equipos y deportistas que quieres seguir. En Inicio sale una fila Siguiendo con los canales y programas que los mencionan. Selecciona un nombre para quitarlo.",
        "Team or player" to "Equipo o deportista", "Add" to "Añadir",
        "Other devices" to "Otros dispositivos",
        "Copies everything (playlist, keys, lists, what you were watching, these settings) to TUTV on another device on the same Wi-Fi. It is a one-time copy, not a live sync."
            to "Copia todo (lista, claves, tus listas, lo que estabas viendo, estos ajustes) a TUTV en otro dispositivo de la misma Wi-Fi. Es una copia puntual, no una sincronización continua.",
        "1 · On the device that has everything" to "1 · En el dispositivo que ya lo tiene todo",
        "Share from this device" to "Compartir desde este dispositivo",
        "Couldn't start sharing. Is this device on Wi-Fi?" to "No se pudo empezar a compartir. ¿Está este dispositivo en la Wi-Fi?",
        "Try again" to "Reintentar", "Keep this screen open while the other device copies." to "Deja esta pantalla abierta mientras el otro dispositivo copia.",
        "Stop sharing" to "Dejar de compartir", "2 · On the new device" to "2 · En el dispositivo nuevo",
        "Address shown on the other device" to "Dirección que sale en el otro dispositivo", "Code" to "Código",
        "Copy to this device" to "Copiar a este dispositivo", "Copying…" to "Copiando…", "Done. Restarting…" to "Hecho. Reiniciando…",
        // sports agenda
        "Sports agenda" to "Agenda deportiva", "LIVE" to "EN DIRECTO", "Tomorrow" to "Mañana",
        "World Cup" to "Mundial", "Euro" to "Eurocopa",
        "Home shows the next two days of these competitions. Selecting a fixture opens that sport's channels. The fixtures come from ESPN's public feed, which is free but unofficial, so it can stop working without notice."
            to "En Inicio salen los próximos dos días de estas competiciones. Al elegir un partido se abren los canales de ese deporte. Los partidos vienen del feed público de ESPN, que es gratis pero no oficial, así que puede dejar de funcionar sin aviso.",
        "No fixtures found yet." to "Todavía no se han encontrado partidos.",
        "Checking fixtures…" to "Buscando partidos…",
        // channel options
        "Watch later" to "Ver más tarde", "In Watch later" to "En Ver más tarde",
        "Remove from Watch later" to "Quitar de Ver más tarde",
        "Remove from Continue watching" to "Quitar de Seguir viendo",
        "Add to favourites" to "Añadir a favoritos", "Remove from favourites" to "Quitar de favoritos",
        "New list…" to "Lista nueva…", "Name of the list" to "Nombre de la lista", "Create" to "Crear",
        "Cancel" to "Cancelar", "Added to favourites" to "Añadido a favoritos", "Removed from favourites" to "Quitado de favoritos",
        "Last crash" to "Último cierre inesperado", "Clear" to "Borrar",
        "If the app closed by itself, this is why. A photo of this box is what is needed to fix it."
            to "Si la app se cerró sola, este es el motivo. Con una foto de este recuadro se puede arreglar.",
        // player
        "Can't play this stream" to "No se puede reproducir", "Open in other player" to "Abrir en otro reproductor",
        "Close" to "Cerrar", "Intro saved for this season" to "Intro guardada para esta temporada",
        "That doesn't look like an intro, try again" to "Eso no parece una intro, inténtalo otra vez",
        "Audio in sync (0 ms)" to "Audio sincronizado (0 ms)",
        "The provider no longer has this file (HTTP 404)." to "El proveedor ya no tiene este archivo (HTTP 404).",
        "Open with" to "Abrir con",
        "No other video player installed (try VLC)" to "No hay otro reproductor instalado (prueba VLC)",
    )
}

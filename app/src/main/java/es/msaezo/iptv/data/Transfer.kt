package es.msaezo.iptv.data

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Hands this device's settings to another TUTV on the same Wi-Fi. While sharing is on, a
 * tiny web server answers one address, protected by a random four-digit code.
 */
object Transfer {
    const val PORT = 8765
    @Volatile private var server: ServerSocket? = null

    /** This device's address on the home network, e.g. 192.168.1.34. */
    fun localAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) {
        null
    }

    /** Starts sharing [json] under [code]. Returns false when the port can't be opened. */
    fun start(json: String, code: String): Boolean {
        stop()
        val socket = try { ServerSocket(PORT) } catch (e: Exception) { return false }
        server = socket
        thread(isDaemon = true, name = "tutv-share") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { runCatching { answer(client, json, code) } }
            }
        }
        return true
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun answer(client: Socket, json: String, code: String) {
        client.use { c ->
            c.soTimeout = 8000
            val line = c.getInputStream().bufferedReader().readLine().orEmpty()
            val path = line.split(' ').getOrNull(1).orEmpty()
            val ok = path == "/$code"
            val body = (if (ok) json else "{}").toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 ${if (ok) "200 OK" else "403 Forbidden"}\r\n" +
                "Content-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            c.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
    }

    /** Fetches the settings another device is sharing at [address] (e.g. "192.168.1.34") with [code]. */
    fun fetch(address: String, code: String): String {
        val host = address.trim().removePrefix("http://").substringBefore('/').let { if (':' in it) it else "$it:$PORT" }
        return Http.getText("http://$host/${code.trim()}", "TUTV")
    }
}

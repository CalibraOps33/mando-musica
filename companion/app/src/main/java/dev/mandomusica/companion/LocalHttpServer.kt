package dev.mandomusica.companion

import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
)

data class HttpResponse(
    val code: Int,
    val body: String,
    val contentType: String,
) {
    companion object {
        fun json(code: Int, body: JSONObject) =
            HttpResponse(code, body.toString(), "application/json; charset=utf-8")
    }
}

/**
 * Servidor HTTP mínimo, sin dependencias, que solo escucha en 127.0.0.1.
 * Cada petición se atiende en un hilo del pool y la conexión se cierra al responder.
 */
class LocalHttpServer(
    private val port: Int,
    private val handler: (HttpRequest) -> HttpResponse,
) {
    @Volatile private var serverSocket: ServerSocket? = null
    private val workers: ExecutorService = Executors.newFixedThreadPool(4)

    fun start() {
        thread(name = "mm-http", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                serverSocket = ss
                ServerState.port = port
                ServerState.error = null
                ServerState.running = true
                while (!ss.isClosed) {
                    val client = try {
                        ss.accept()
                    } catch (e: IOException) {
                        break
                    }
                    workers.execute { serve(client) }
                }
            } catch (e: Exception) {
                ServerState.error = e.toString()
            } finally {
                ServerState.running = false
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // ya estaba cerrado
        }
        serverSocket = null
        workers.shutdownNow()
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                // Cabeceras: se leen y se descartan hasta la línea vacía.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val parts = requestLine.split(' ')
                val method = parts.getOrElse(0) { "GET" }
                val target = parts.getOrElse(1) { "/" }
                val q = target.indexOf('?')
                val path = if (q >= 0) target.substring(0, q) else target
                val query = if (q >= 0) parseQuery(target.substring(q + 1)) else emptyMap()

                val response = try {
                    handler(HttpRequest(method, path, query))
                } catch (e: Exception) {
                    HttpResponse.json(500, JSONObject().put("ok", false).put("error", e.toString()))
                }
                write(socket, response)
            } catch (e: IOException) {
                // El cliente cerró la conexión; nada que hacer.
            }
        }
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val i = pair.indexOf('=')
            if (i < 0) decode(pair) to "" else decode(pair.substring(0, i)) to decode(pair.substring(i + 1))
        }

    private fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun write(socket: Socket, response: HttpResponse) {
        val body = response.body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 ${response.code} ${reason(response.code)}\r\n" +
            "Content-Type: ${response.contentType}\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Error"
    }
}

package dev.mandomusica.companion

import org.json.JSONObject

/**
 * API local (v1). Todas las rutas son GET para que el Side Service de Zepp las llame fácilmente.
 *
 *   GET /                 -> ping: { ok, app, version, api }
 *   GET /status           -> estado del reproductor activo
 *   GET /players          -> apps con sesión multimedia activa
 *   GET /queue            -> cola del reproductor activo (si la app la comparte)
 *   GET /cmd/{accion}     -> ejecuta una orden y devuelve el estado actualizado
 *
 * Acciones: play, pause, toggle, next, prev, stop,
 *           seek?ms=, seek_rel?ms=, vol_up, vol_down, vol_set?value=,
 *           select?package=, custom?id=, skip_queue?id=
 */
object Api {
    fun handle(req: HttpRequest): HttpResponse {
        ServerState.requests += 1
        ServerState.lastRequestAt = System.currentTimeMillis()

        return when {
            req.path == "/" || req.path == "/ping" -> HttpResponse.json(
                200,
                JSONObject()
                    .put("ok", true)
                    .put("app", "mando-musica")
                    .put("version", Config.VERSION)
                    .put("api", Config.API_VERSION),
            )
            req.path == "/status" -> HttpResponse.json(200, MediaHub.status())
            req.path == "/players" -> HttpResponse.json(200, MediaHub.players())
            req.path == "/queue" -> HttpResponse.json(200, MediaHub.queue())
            req.path.startsWith("/cmd/") -> {
                val action = req.path.removePrefix("/cmd/")
                val result = MediaHub.command(action, req.query)
                HttpResponse.json(if (result.optBoolean("ok")) 200 else 400, result)
            }
            else -> HttpResponse.json(404, JSONObject().put("ok", false).put("error", "not_found"))
        }
    }
}

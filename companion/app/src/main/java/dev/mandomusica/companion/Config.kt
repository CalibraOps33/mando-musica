package dev.mandomusica.companion

object Config {
    /** Puerto del servidor local. Solo escucha en 127.0.0.1 (no es accesible desde la wifi). */
    const val PORT = 8737
    const val VERSION = "0.1.0"
    const val API_VERSION = 1
}

/** Estado del servidor, compartido entre el servicio y la pantalla. */
object ServerState {
    @Volatile var running: Boolean = false
    @Volatile var port: Int = 0
    @Volatile var error: String? = null
    @Volatile var requests: Long = 0
    @Volatile var lastRequestAt: Long = 0
}

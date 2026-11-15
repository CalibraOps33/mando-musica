package dev.mandomusica.companion

import android.content.ComponentName
import android.service.notification.NotificationListenerService

/**
 * El sistema mantiene enlazado este servicio mientras la app tenga acceso a notificaciones.
 * Lo usamos como "ancla" para arrancar el servidor local sin necesitar un servicio en primer plano.
 */
class MediaListenerService : NotificationListenerService() {
    private var server: LocalHttpServer? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaHub.init(applicationContext)
        startServer()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        stopServer()
        // Pide al sistema que vuelva a enlazarnos en cuanto pueda.
        NotificationListenerService.requestRebind(ComponentName(this, MediaListenerService::class.java))
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) return
        server = LocalHttpServer(Config.PORT, Api::handle).also { it.start() }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }
}

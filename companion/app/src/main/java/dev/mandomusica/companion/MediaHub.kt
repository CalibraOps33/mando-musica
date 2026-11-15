package dev.mandomusica.companion

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Puente con las sesiones multimedia de Android: sirve para cualquier app que publique
 * una MediaSession (YouTube Music, Spotify, Amazon Music, podcasts...).
 * Todas las llamadas al sistema se hacen en el hilo principal.
 */
object MediaHub {
    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val labelCache = HashMap<String, String>()

    /** App elegida a mano desde el reloj; si es null se elige automáticamente. */
    @Volatile var preferredPackage: String? = null

    private const val NO_PLAYER = "no_player"
    private const val BAD_VALUE = "bad_value"

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    // ---------------------------------------------------------------- acceso

    fun isAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any { entry ->
            ComponentName.unflattenFromString(entry)?.packageName == appContext.packageName
        }
    }

    private fun listenerComponent() = ComponentName(appContext, MediaListenerService::class.java)

    private fun sessions(): List<MediaController> {
        val manager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        return try {
            manager.getActiveSessions(listenerComponent())
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Reproductor a controlar: el elegido, si no el que suena, si no el más reciente. */
    private fun current(): MediaController? {
        val list = sessions()
        val preferred = preferredPackage
        if (preferred != null) {
            list.firstOrNull { it.packageName == preferred }?.let { return it }
        }
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
    }

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun audio() = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---------------------------------------------------------------- lectura

    fun status(): JSONObject = onMain { buildStatus() }

    private fun buildStatus(): JSONObject {
        val out = JSONObject()
            .put("ok", true)
            .put("accessGranted", isAccessGranted())
            .put("serverTime", System.currentTimeMillis())

        val controller = current()
        out.put("volume", volumeJson(controller))
        if (controller == null) {
            return out.put("player", JSONObject.NULL).put("state", "none").put("isPlaying", false)
        }

        val state = controller.playbackState
        val metadata = controller.metadata
        val actions = state?.actions ?: 0L

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val customActions = JSONArray()
        state?.customActions?.forEach { action ->
            customActions.put(JSONObject().put("id", action.action).put("name", action.name.toString()))
        }

        return out
            .put("player", JSONObject()
                .put("package", controller.packageName)
                .put("label", label(controller.packageName)))
            .put("state", stateName(state?.state))
            .put("isPlaying", state?.state == PlaybackState.STATE_PLAYING)
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("durationMs", duration)
            .put("positionMs", position(state))
            .put("can", JSONObject()
                .put("seek", actions and PlaybackState.ACTION_SEEK_TO != 0L)
                .put("next", actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
                .put("prev", actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L))
            .put("customActions", customActions)
            .put("playerCount", sessions().size)
    }

    fun players(): JSONObject = onMain {
        val active = current()?.packageName
        val list = JSONArray()
        sessions().forEach { c ->
            list.put(JSONObject()
                .put("package", c.packageName)
                .put("label", label(c.packageName))
                .put("state", stateName(c.playbackState?.state))
                .put("selected", c.packageName == active))
        }
        JSONObject().put("ok", true).put("preferred", preferredPackage ?: JSONObject.NULL).put("players", list)
    }

    fun queue(): JSONObject = onMain {
        val controller = current()
        val items = JSONArray()
        controller?.queue?.take(50)?.forEach { item ->
            items.put(JSONObject()
                .put("id", item.queueId)
                .put("title", item.description.title?.toString() ?: "")
                .put("subtitle", item.description.subtitle?.toString() ?: ""))
        }
        JSONObject()
            .put("ok", true)
            .put("activeId", controller?.playbackState?.activeQueueItemId ?: -1L)
            .put("items", items)
    }

    private fun volumeJson(controller: MediaController?): JSONObject {
        val info = controller?.playbackInfo
        return if (info != null && info.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            JSONObject().put("current", info.currentVolume).put("max", info.maxVolume).put("remote", true)
        } else {
            val am = audio()
            JSONObject()
                .put("current", am.getStreamVolume(AudioManager.STREAM_MUSIC))
                .put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .put("remote", false)
        }
    }

    private fun position(state: PlaybackState?): Long {
        if (state == null) return 0L
        var pos = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            pos += (elapsed * state.playbackSpeed).toLong()
        }
        return pos.coerceAtLeast(0L)
    }

    private fun stateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        null -> "none"
        else -> "other"
    }

    // ---------------------------------------------------------------- órdenes

    /**
     * Ejecuta una orden. Devuelve el estado actualizado con "ok" y, si falla, "error".
     * Tras las órdenes de transporte espera un momento para que la app de música
     * actualice su estado y el reloj reciba ya el resultado nuevo.
     */
    fun command(action: String, params: Map<String, String>): JSONObject {
        val error = onMain { execute(action, params) }
        if (error != null) {
            return JSONObject().put("ok", false).put("error", error).put("action", action)
        }
        if (action in WAIT_AFTER && Looper.myLooper() != Looper.getMainLooper()) {
            Thread.sleep(250)
        }
        return status().put("action", action)
    }

    private val WAIT_AFTER = setOf("play", "pause", "toggle", "next", "prev", "stop", "seek", "seek_rel", "skip_queue")

    /** Devuelve null si todo fue bien o un código de error. */
    private fun execute(action: String, p: Map<String, String>): String? {
        val controller = current()
        val controls = controller?.transportControls

        when (action) {
            "play" -> if (controls != null) controls.play() else dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            "pause" -> {
                if (controls == null) return NO_PLAYER
                controls.pause()
            }
            "toggle" -> {
                if (controls == null) {
                    // Sin sesión activa: pide al sistema que reanude la última app de música.
                    dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                } else if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
            }
            "next" -> {
                if (controls == null) return NO_PLAYER
                controls.skipToNext()
            }
            "prev" -> {
                if (controls == null) return NO_PLAYER
                controls.skipToPrevious()
            }
            "stop" -> {
                if (controls == null) return NO_PLAYER
                controls.stop()
            }
            "seek" -> {
                if (controls == null) return NO_PLAYER
                val ms = p["ms"]?.toLongOrNull() ?: return BAD_VALUE
                controls.seekTo(ms.coerceAtLeast(0L))
            }
            "seek_rel" -> {
                if (controls == null) return NO_PLAYER
                val delta = p["ms"]?.toLongOrNull() ?: return BAD_VALUE
                controls.seekTo((position(controller?.playbackState) + delta).coerceAtLeast(0L))
            }
            "vol_up" -> adjustVolume(controller, AudioManager.ADJUST_RAISE)
            "vol_down" -> adjustVolume(controller, AudioManager.ADJUST_LOWER)
            "vol_set" -> {
                val value = p["value"]?.toIntOrNull() ?: return BAD_VALUE
                setVolume(controller, value)
            }
            "select" -> {
                val pkg = p["package"]
                preferredPackage = if (pkg.isNullOrBlank()) null else pkg
            }
            "custom" -> {
                if (controls == null) return NO_PLAYER
                val id = p["id"] ?: return BAD_VALUE
                controls.sendCustomAction(id, null)
            }
            "skip_queue" -> {
                if (controls == null) return NO_PLAYER
                val id = p["id"]?.toLongOrNull() ?: return BAD_VALUE
                controls.skipToQueueItem(id)
            }
            else -> return "unknown_action"
        }
        return null
    }

    private fun isRemote(controller: MediaController?): Boolean =
        controller?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE

    /** Volumen: si la música suena en otro dispositivo (Cast, Connect) se ajusta ese; si no, el del móvil. */
    private fun adjustVolume(controller: MediaController?, direction: Int) {
        if (controller != null && isRemote(controller)) {
            controller.adjustVolume(direction, 0)
        } else {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        }
    }

    private fun setVolume(controller: MediaController?, value: Int) {
        if (controller != null && isRemote(controller)) {
            val max = controller.playbackInfo?.maxVolume ?: value
            controller.setVolumeTo(value.coerceIn(0, max), 0)
        } else {
            val am = audio()
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, max), 0)
        }
    }

    private fun dispatchKey(code: Int) {
        val am = audio()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------------------------------------------------------------- hilos

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        mainHandler.post {
            result = runCatching(block)
            latch.countDown()
        }
        if (!latch.await(3, TimeUnit.SECONDS)) throw TimeoutException("main thread busy")
        return result!!.getOrThrow()
    }
}

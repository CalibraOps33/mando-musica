package dev.mandomusica.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Pantalla de configuración y diagnóstico. El producto final la simplificará. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var accessText: TextView
    private lateinit var serverText: TextView
    private lateinit var nowText: TextView

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediaHub.init(applicationContext)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(text("Mando Música · companion v${Config.VERSION}", 22f))

        accessText = text("", 15f)
        root.addView(accessText)
        root.addView(button("1. Dar acceso a notificaciones") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("2. Quitar la optimización de batería") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        serverText = text("", 15f)
        root.addView(serverText)

        nowText = text("", 17f)
        root.addView(nowText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("⏮" to "prev", "⏯" to "toggle", "⏭" to "next", "Vol −" to "vol_down", "Vol +" to "vol_up")
            .forEach { (label, action) ->
                row.addView(
                    button(label) { send(action) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
        root.addView(row)

        root.addView(text(
            "Prueba desde el navegador de este móvil:\nhttp://127.0.0.1:${Config.PORT}/status\n\n" +
                "Prueba desde el reloj: en los ajustes de Link Probe pon el puerto ${Config.PORT} " +
                "y pulsa el botón 127.0.0.1.",
            14f,
        ))

        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun send(action: String) {
        io.execute {
            MediaHub.command(action, emptyMap())
            handler.post { refresh() }
        }
    }

    private fun refresh() {
        accessText.text = if (MediaHub.isAccessGranted()) {
            "✅ Acceso a notificaciones concedido"
        } else {
            "❌ Falta el acceso a notificaciones (paso 1)"
        }

        val error = ServerState.error
        serverText.text = when {
            ServerState.running ->
                "✅ Servidor local en 127.0.0.1:${ServerState.port} · ${ServerState.requests} peticiones"
            error != null -> "❌ Error del servidor: $error"
            else -> "⏳ Servidor parado (arranca al conceder el acceso)"
        }

        io.execute {
            val status = try {
                MediaHub.status()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.toString())
            }
            handler.post { nowText.text = describe(status) }
        }
    }

    private fun describe(s: JSONObject): String {
        if (!s.optBoolean("ok")) return "Error: ${s.optString("error")}"
        val player = s.optJSONObject("player") ?: return "\nNada sonando ahora mismo.\n"
        val volume = s.optJSONObject("volume")
        val vol = if (volume != null) "${volume.optInt("current")}/${volume.optInt("max")}" else "?"
        val where = if (volume?.optBoolean("remote") == true) " (en otro dispositivo)" else ""
        return "\n${player.optString("label")} · ${s.optString("state")}\n" +
            "${s.optString("title")}\n${s.optString("artist")}\n" +
            "Volumen $vol$where · ${s.optInt("playerCount")} app(s) con sesión\n"
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun text(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

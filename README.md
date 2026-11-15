# Mando Música

Mando universal de música para relojes Zepp OS (Amazfit). Controla desde el reloj cualquier reproductor del móvil Android: YouTube Music, Spotify, Amazon Music, podcasts…

```
Reloj (mini app) ──BLE──> app Zepp (Side Service) ──HTTP 127.0.0.1:8737──> app Android "companion" ──> reproductor
```

| Carpeta | Qué es |
|---|---|
| `companion/` | App Android (Kotlin, sin dependencias externas). Lee y controla las sesiones multimedia y expone una API local. |
| `watch-probe/` | Link Probe: mini app de Zepp OS para comprobar la conexión reloj → móvil. |
| `.github/workflows/` | Compila el APK en GitHub y lo publica en *Releases* con la etiqueta `dev`. |

---

## Compilar e instalar la app del móvil (sin Android Studio)

### 1. Crear el repositorio
1. Crea una cuenta gratuita en github.com.
2. Crea un repositorio **privado** llamado `mando-musica`. No marques "Add a README": tiene que quedar vacío.

### 2. Subir el código
**Opción A (recomendada): conectar GitHub a Claude.** Conecta GitHub en los conectores de claude.ai y da acceso a este repositorio. Así Claude sube los cambios y revisa directamente los errores de compilación.

**Opción B: subirlo a mano desde la web.**
1. Descomprime el zip.
2. En el repositorio vacío, pulsa *uploading an existing file*.
3. Arrastra **el contenido** de la carpeta `mando-musica`, incluida la carpeta `.github`.
4. Pulsa *Commit changes*.

Si la carpeta `.github` no se sube, crea el archivo a mano: *Add file → Create new file*, con la ruta `.github/workflows/build-companion.yml`, y pega su contenido.

### 3. Esperar a la compilación
En la pestaña **Actions** verás "Compilar app Android (companion)". Tarda unos 5 minutos. Si sale ✅ verde, el APK está listo. Si sale ❌ rojo, abre el error y envíaselo a Claude (con una captura basta).

### 4. Instalar en el móvil
1. En el navegador del móvil, entra en el repositorio (con tu sesión de GitHub iniciada) → **Releases** → **dev** → `mando-musica-dev.apk`.
2. Android te pedirá permitir que el navegador instale apps desconocidas. Acéptalo.
3. Las siguientes versiones se instalan encima de la anterior, sin desinstalar.

### 5. Primer arranque
Abre **Mando Música (dev)**:
1. **"Dar acceso a notificaciones"** → activa Mando Música.
   - En Android 13 o superior puede aparecer *"Ajuste restringido"*. Ve a *Ajustes → Aplicaciones → Mando Música (dev) → ⋮ (arriba a la derecha) → Permitir ajustes restringidos* y vuelve a intentarlo.
2. **"Quitar la optimización de batería"** → busca Mando Música y elige *Sin restricciones* o *No optimizar*.
   - En Xiaomi, Huawei, OPPO y similares, activa además el **inicio automático** de la app.

La pantalla debe mostrar ✅ en el acceso y ✅ *Servidor local en 127.0.0.1:8737*.

### 6. Probar
1. Pon música en YouTube Music (o cualquier otra app): la companion debe mostrar la app, la canción y el volumen.
2. Prueba los botones ⏮ ⏯ ⏭ y Vol −/+ de la propia app.
3. En el navegador del móvil abre `http://127.0.0.1:8737/status`: debe aparecer el estado en JSON.
4. **Con el reloj:** en los ajustes de Link Probe (app Zepp) pon el puerto **8737** y pulsa el botón **127.0.0.1** en el reloj. Ya no hace falta Termux.
   - El botón *IP de la wifi* fallará con la companion. Es lo esperado: por seguridad, la companion solo escucha dentro del propio móvil.

---

## API local v1 (para el Side Service)

Todas las rutas son `GET` en `http://127.0.0.1:8737` y devuelven JSON.

| Ruta | Respuesta |
|---|---|
| `/` | Ping: `{ ok, app, version, api }` |
| `/status` | Reproductor activo: `player{package,label}`, `state`, `isPlaying`, `title`, `artist`, `album`, `durationMs`, `positionMs`, `volume{current,max,remote}`, `can{seek,next,prev}`, `customActions[]`, `playerCount` |
| `/players` | Apps con sesión multimedia activa |
| `/queue` | Cola del reproductor (solo si la app la comparte) |
| `/cmd/play` `/cmd/pause` `/cmd/toggle` `/cmd/next` `/cmd/prev` `/cmd/stop` | Transporte. Devuelven el estado actualizado. |
| `/cmd/seek?ms=` `/cmd/seek_rel?ms=` | Ir a una posición absoluta o relativa (±15 s → `ms=15000` / `ms=-15000`) |
| `/cmd/vol_up` `/cmd/vol_down` `/cmd/vol_set?value=` | Volumen. Si la música suena en otro dispositivo (Cast/Connect), se ajusta ese. |
| `/cmd/select?package=` | Elegir qué app controlar (vacío = automático) |
| `/cmd/custom?id=` | Acción propia de la app (p. ej. "me gusta"), según `customActions` |
| `/cmd/skip_queue?id=` | Saltar a un elemento de la cola |

`toggle` y `play` sin ninguna app activa piden al sistema que reanude la última app de música.

## Pendiente
- [ ] Resultado de Link Probe con la companion: decide entre la ruta local y el relay en la nube.
- [ ] Token compartido para que otras apps del móvil no puedan usar la API local.
- [ ] App del reloj v0.1: pantalla de reproducción, volumen con los botones físicos y selector de app.
- [ ] Portada del álbum (conversión de imagen para el reloj).
- [ ] Clave de firma de producción y ficha en Google Play. La clave de `companion/keystore/` es **solo para desarrollo**.

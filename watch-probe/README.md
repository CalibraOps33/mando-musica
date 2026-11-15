# Link Probe: prueba de viabilidad del mando universal

## Qué valida

El mando universal depende de una sola pieza técnica dudosa: que el **Side Service** (el código de la mini app que corre dentro de la app Zepp del móvil) pueda hablar con **otra app del mismo móvil** a través de `http://127.0.0.1`.

Si esto funciona, la app Android companion puede controlar cualquier reproductor (YouTube Music, Spotify, Amazon Music…) y el reloj le manda las órdenes a través de la app Zepp. En otros ecosistemas (Fitbit) esta vía dejó de funcionar en algunas versiones de Android, así que hay que probarla antes de construir nada.

La prueba no necesita escribir código Android: en lugar de la app companion se usa un servidor HTTP de Termux.

> **Si ya tienes instalada la companion "Mando Música (dev)", no hace falta Termux.** En los ajustes de Link Probe pon el puerto **8737** y usa solo el botón 127.0.0.1: la companion no escucha en la IP de la wifi.

```
Reloj ──BLE──> app Zepp (Side Service) ──HTTP──> servidor en el móvil (Termux)
                                       └─HTTP──> Internet (control)
```

## 1. Preparar el ordenador

1. Instala **Node.js 20 o superior** (versión LTS) desde nodejs.org.
2. Instala la CLI de Zepp: `npm i -g @zeppos/zeus-cli`
3. Inicia sesión con tu cuenta Zepp: `zeus login`

## 2. Preparar la app Zepp del móvil

1. Activa el **modo desarrollador**. En la mayoría de versiones se hace en *Perfil → Ajustes → Acerca de*, pulsando varias veces el logo de Zepp.
2. Comprueba que la app Zepp puede funcionar en segundo plano: sin optimización de batería y con autoinicio permitido.

## 3. Servidor de prueba en el móvil (Termux)

1. Instala **Termux** (desde F-Droid o Google Play).
2. Ejecuta en Termux:
   ```
   pkg install python
   termux-wake-lock
   python -m http.server 8080
   ```
3. Comprueba que funciona: abre `http://127.0.0.1:8080` en el navegador del móvil. Tiene que aparecer un listado de carpetas.
4. Anota la IP del móvil en la wifi. Puedes verla con `ifconfig` en otra sesión de Termux, o en los ajustes de la wifi.

## 4. Instalar Link Probe en el reloj

En la carpeta del proyecto:

```
npm install
zeus preview
```

Elige el dispositivo (Balance o T-Rex 3). Se mostrará un código QR: escanéalo desde la app Zepp, en la opción de modo desarrollador. La mini app se instala en el reloj.

Después abre los ajustes de Link Probe en la app Zepp (dentro del reloj, en la lista de apps) y rellena la **IP de la wifi**. El puerto por defecto es 8080.

## 5. Protocolo de prueba

Con Termux sirviendo en el puerto 8080, pulsa cada destino en el reloj y anota el resultado. Los últimos resultados también aparecen en los ajustes de Link Probe, en el móvil.

| # | Escenario | 127.0.0.1 (×5) | IP wifi (×3) | Internet (×3) | Latencia total media |
|---|---|---|---|---|---|
| A | App Zepp abierta y móvil desbloqueado | | | | |
| B | Móvil bloqueado, app Zepp en segundo plano | | | — | |
| C | Tras 10 min con el móvil bloqueado | | — | — | |
| D | Repetir A con el otro reloj | | | | |
| E | Con datos móviles (sin wifi) | | — | | |

En cada casilla apunta **OK/FALLO** y, si falla, el texto del error.

## 6. Cómo interpretar los resultados

| Resultado | Qué significa | Arquitectura |
|---|---|---|
| 127.0.0.1 OK en A, B y C | La vía local funciona | **Ruta A (ideal):** companion Android con servidor local. Sin coste de servidores y con latencia mínima. |
| 127.0.0.1 falla pero la IP wifi funciona | Solo funciona dentro de la wifi | No sirve fuera de casa → **Ruta C** |
| Error tipo *cleartext* o *not permitted* | La app Zepp bloquea HTTP sin cifrar | **Ruta C** (o investigar HTTPS local) |
| Nada local funciona y solo funciona Internet | El Side Service solo sale a Internet | **Ruta C:** relay en la nube. El reloj y la companion se comunican a través de un pequeño servidor. Funciona siempre, pero añade latencia y coste. |
| Internet también falla | Problema de configuración | Revisa los permisos en segundo plano de la app Zepp y que el Side Service arranque |

Para que los botones se sientan inmediatos, la **latencia total** debería quedar por debajo de unos 600 ms.

## Problemas frecuentes

- **"Sin respuesta del móvil":** la app Zepp está dormida. Ábrela y revisa la optimización de batería.
- **`zeus preview` da error de appId:** crea la app en la consola de desarrolladores de Zepp y copia su `appId` en `app.json`.
- **Termux deja de responder con la pantalla bloqueada:** asegúrate de haber ejecutado `termux-wake-lock`.

## Estructura

```
app.json            Configuración (API_LEVEL 3.0, Balance, Balance 2 y T-Rex 3, 480×480)
app.js              Arranque de la app (ZML)
page/index.js       Pantalla del reloj: 3 botones y resultado
app-side/index.js   Side Service: hace el GET con timeout de 6 s y guarda los resultados
setting/index.js    Ajustes en la app Zepp: puerto, IP y últimos resultados
assets/round480/    Icono
```

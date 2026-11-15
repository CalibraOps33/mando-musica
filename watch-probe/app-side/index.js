// Link Probe - Side Service (corre dentro de la app Zepp del movil)
// Recibe la orden del reloj, hace un GET al destino y devuelve
// { ok, status, ms, url, error } al reloj. Guarda los ultimos
// resultados en settingsStorage para verlos en la pagina de ajustes.

import { BaseSideService } from '@zeppos/zml/base-side'

const DEFAULTS = {
  port: '8080',
  lanIp: '',
  internetUrl: 'https://www.google.com/generate_204',
}
const TIMEOUT_MS = 6000 // por debajo de los ~10 s en que la app Zepp corta peticiones
const MAX_RESULTS = 12

function getSetting(key) {
  try {
    const value = settings.settingsStorage.getItem(key)
    return value ? String(value).trim() : DEFAULTS[key]
  } catch (e) {
    return DEFAULTS[key]
  }
}

function buildUrl(target) {
  const port = getSetting('port')
  if (target === 'loopback') return 'http://127.0.0.1:' + port + '/'
  if (target === 'lan') {
    const ip = getSetting('lanIp')
    return ip ? 'http://' + ip + ':' + port + '/' : null
  }
  if (target === 'internet') return getSetting('internetUrl')
  return null
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout ' + ms + ' ms')), ms)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        clearTimeout(timer)
        reject(error)
      },
    )
  })
}

function probe(target) {
  const url = buildUrl(target)
  if (!url) {
    return Promise.resolve({ ok: false, url: null, ms: 0, error: 'Falta la IP de la wifi en Ajustes' })
  }
  const t0 = Date.now()
  return withTimeout(fetch({ url: url, method: 'GET' }), TIMEOUT_MS)
    .then((res) => {
      const ms = Date.now() - t0
      const status = res && res.status
      const ok = status === undefined ? !!res : status >= 200 && status < 400
      return { ok: ok, status: status, ms: ms, url: url }
    })
    .catch((error) => {
      const message = error && error.message ? error.message : JSON.stringify(error)
      return { ok: false, url: url, ms: Date.now() - t0, error: String(message) }
    })
}

function record(target, result) {
  try {
    const raw = settings.settingsStorage.getItem('results')
    const list = raw ? JSON.parse(raw) : []
    list.unshift({
      at: new Date().toISOString(),
      target: target,
      ok: result.ok,
      status: result.status,
      ms: result.ms,
      url: result.url,
      error: result.error,
    })
    settings.settingsStorage.setItem('results', JSON.stringify(list.slice(0, MAX_RESULTS)))
  } catch (e) {
    console.log('[probe] no se pudo guardar el resultado: ' + e)
  }
}

AppSideService(
  BaseSideService({
    onInit() {
      console.log('[probe] side service onInit')
    },

    onRun() {},

    onRequest(req, res) {
      if (req.method !== 'probe') {
        res('metodo desconocido: ' + req.method)
        return
      }
      const target = req.params && req.params.target
      probe(target).then((result) => {
        console.log('[probe] ' + target + ' -> ' + JSON.stringify(result))
        record(target, result)
        res(null, result)
      })
    },

    onDestroy() {},
  }),
)

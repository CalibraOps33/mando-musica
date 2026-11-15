// Link Probe - pagina de ajustes (se abre en la app Zepp del movil)
// Permite configurar el puerto, la IP de la wifi y la URL de control,
// y muestra los ultimos resultados que guarda el Side Service.

const DEFAULT_INTERNET_URL = 'https://www.google.com/generate_204'

function parseResults(raw) {
  try {
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

function describe(r) {
  const time = (r.at || '').slice(11, 19)
  const state = r.ok ? 'OK ' + (r.status || '') : 'FALLO'
  const detail = r.error ? ' - ' + r.error : ''
  return time + '  ' + r.target + ': ' + state + ' (' + r.ms + ' ms)' + detail
}

AppSettingsPage({
  build(props) {
    const storage = props.settingsStorage
    const results = parseResults(storage.getItem('results'))

    return View({ style: { padding: '16px' } }, [
      Text({ paragraph: true, bold: true, style: { fontSize: '20px', marginBottom: '8px' } }, 'Link Probe'),
      Text(
        { paragraph: true, style: { fontSize: '14px', color: '#666', marginBottom: '12px' } },
        'Arranca un servidor HTTP en este movil (por ejemplo en Termux) y pulsa los destinos desde el reloj.',
      ),

      TextInput({
        label: 'Puerto del servidor local',
        value: storage.getItem('port') || '8080',
        onChange: (value) => storage.setItem('port', String(value).trim()),
      }),
      TextInput({
        label: 'IP del movil en la wifi (ej. 192.168.1.34)',
        value: storage.getItem('lanIp') || '',
        onChange: (value) => storage.setItem('lanIp', String(value).trim()),
      }),
      TextInput({
        label: 'URL de control (Internet)',
        value: storage.getItem('internetUrl') || DEFAULT_INTERNET_URL,
        onChange: (value) => storage.setItem('internetUrl', String(value).trim()),
      }),

      Text({ paragraph: true, bold: true, style: { fontSize: '16px', marginTop: '16px' } }, 'Ultimos resultados'),
      ...(results.length
        ? results.map((r) => Text({ paragraph: true, style: { fontSize: '13px' } }, describe(r)))
        : [Text({ paragraph: true, style: { fontSize: '13px', color: '#999' } }, 'Todavia no hay pruebas.')]),

      Button({
        label: 'Borrar resultados',
        style: { marginTop: '12px' },
        onClick: () => storage.setItem('results', '[]'),
      }),
    ])
  },
})

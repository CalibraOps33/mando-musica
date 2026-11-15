// Link Probe - pagina del reloj (API_LEVEL 3.0, pantalla redonda 480x480)
// Envia una orden al Side Service (movil) y muestra si este ha podido
// llegar al destino elegido y cuanto ha tardado el viaje completo.

import { BasePage } from '@zeppos/zml/base-page'
import { createWidget, widget, align, prop, text_style } from '@zos/ui'
import { px } from '@zos/utils'

const TARGETS = [
  { key: 'loopback', label: '127.0.0.1' },
  { key: 'lan', label: 'IP de la wifi' },
  { key: 'internet', label: 'Internet' },
]

Page(
  BasePage({
    state: {
      resultWidget: null,
      busy: false,
    },

    build() {
      createWidget(widget.TEXT, {
        x: px(90),
        y: px(36),
        w: px(300),
        h: px(48),
        text: 'Link Probe',
        text_size: px(32),
        color: 0xffffff,
        align_h: align.CENTER_H,
        align_v: align.CENTER_V,
      })

      TARGETS.forEach((target, i) => {
        createWidget(widget.BUTTON, {
          x: px(90),
          y: px(96 + i * 70),
          w: px(300),
          h: px(58),
          radius: px(29),
          normal_color: 0x1f6f4a,
          press_color: 0x2e9e6a,
          text: target.label,
          text_size: px(26),
          color: 0xffffff,
          click_func: () => this.probe(target),
        })
      })

      this.state.resultWidget = createWidget(widget.TEXT, {
        x: px(90),
        y: px(305),
        w: px(300),
        h: px(110),
        text: 'Pulsa un destino',
        text_size: px(22),
        color: 0xcccccc,
        align_h: align.CENTER_H,
        align_v: align.TOP,
        text_style: text_style.WRAP,
      })
    },

    probe(target) {
      if (this.state.busy) return
      this.state.busy = true
      this.show('Probando ' + target.label + '...')

      const t0 = Date.now()
      this.request({ method: 'probe', params: { target: target.key } })
        .then((result) => {
          const total = Date.now() - t0
          if (result && result.ok) {
            this.show(
              target.label + '\nOK ' + (result.status || '') +
              '\nTotal ' + total + ' ms (movil ' + result.ms + ' ms)',
            )
          } else {
            this.show(
              target.label + '\nFALLO\n' + ((result && result.error) || 'sin detalle') +
              '\nTotal ' + total + ' ms',
            )
          }
          this.state.busy = false
        })
        .catch((error) => {
          this.show(target.label + '\nSin respuesta del movil\n' + String(error))
          this.state.busy = false
        })
    },

    show(text) {
      if (this.state.resultWidget) {
        this.state.resultWidget.setProperty(prop.TEXT, text)
      }
    },

    onDestroy() {
      console.log('[probe] page onDestroy')
    },
  }),
)

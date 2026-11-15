import { BaseApp } from '@zeppos/zml/base-app'

App(
  BaseApp({
    globalData: {},
    onCreate() {
      console.log('[probe] app onCreate')
    },
    onDestroy() {
      console.log('[probe] app onDestroy')
    },
  }),
)

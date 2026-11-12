import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.js'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      // 用 jsdom 模擬瀏覽器環境（document、localStorage）
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      server: {
        deps: {
          // Element Plus 的元件會 import 自己的 CSS，交給 Vite 處理，否則 Node 不認得 .css 檔
          inline: ['element-plus'],
        },
      },
    },
  }),
)

import process from 'node:process'
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 後端位置：預設是本機的 Spring Boot（mvnw spring-boot:run 或 docker compose）
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Element Plus 按需載入：模板裡用到 <el-button> 才把按鈕的程式和樣式打包進來，
    // 不用在 main.js 整套 app.use(ElementPlus)，網站下載的檔案小很多（手機上比較快）
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      // import '@/api/http' = import 'src/api/http'
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 開發時的反向代理：瀏覽器只跟 Vite（localhost:5173）講話，
    // Vite 再把 /api、/uploads 轉給後端。對瀏覽器來說是「同一個網站」，不會遇到 CORS。
    // 正式環境由 Nginx 做同樣的事（D03），所以前端程式一律用相對路徑 /api，不寫死後端網址。
    proxy: {
      '/api': backendUrl,
      '/uploads': backendUrl,
    },
  },
})

import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 進入點：建立 Vue 應用程式，掛上 Pinia（狀態管理）與 Vue Router（路由），再渲染到 index.html 的 #app
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

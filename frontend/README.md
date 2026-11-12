# mamaShop 前端

Vue 3 + Vite + Vue Router + Pinia + Axios + Element Plus（按需載入）。前台與後台在同一個 app，用路由區分。

```bash
npm ci          # 依照 package-lock.json 安裝（第一次或依賴變更後）
npm run dev     # 開發伺服器 http://localhost:5173（/api、/uploads 轉給 localhost:8080 的後端）
npm run test:unit -- --run   # 單元測試（Vitest）
npm run lint    # 程式碼檢查
npm run build   # 打包成靜態檔到 dist/（正式環境由 Nginx 提供）
```

後端不在 localhost:8080 時：`BACKEND_URL=http://其他位址 npm run dev`

## 目錄結構

```
src/
├── main.js           # 進入點：建立 app、掛上 Pinia 與 Router
├── App.vue           # 外框（header）＋ <RouterView>
├── router/
│   ├── index.js      # 網址 → 頁面
│   └── guards.js     # 路由守衛：需要登入的頁面、登入後跳轉
├── api/
│   ├── http.js       # axios 實例：自動帶 token、統一錯誤格式、401 導向登入頁
│   ├── catalog.js    # 商品、分類 API
│   └── auth.js       # 註冊、登入 API
├── stores/
│   ├── auth.js       # 登入狀態（token、會員資料，存在 localStorage）
│   └── cart.js       # 購物車（存在 localStorage）
├── views/            # 頁面（一個網址對應一個）
├── components/       # 可重複使用的元件（ProductCard、ProductGrid…）
├── utils/
│   ├── format.js     # 金額、庫存顯示
│   └── validation.js # 表單驗證規則（和後端一致）
└── assets/main.css   # 全站基本樣式與顏色變數
```

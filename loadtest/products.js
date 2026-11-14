// 壓力測試：前台商品列表與商品詳情（R00）
//
// 模擬顧客逛商品：每個虛擬使用者（VU）不停地發請求、不等待，量出系統「最多撐得住多少」。
//   40% 商品列表（第 1～5 頁，一半有篩選分類）
//   10% 關鍵字搜尋
//   50% 商品詳情（隨機商品）
//
// 只讀不寫（不登入、不下單），可以重複執行。執行方式見 loadtest/README.md。
//
// 環境變數（docker run 時用 -e 傳入）：
//   BASE_URL  網站網址（預設 http://web，也就是在 Docker 網路裡直接打 Nginx）
//   VUS       同時幾個虛擬使用者（預設 50）
//   DURATION  測多久（預設 60s）

import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://web';

export const options = {
  scenarios: {
    browse: {
      // 固定 VUS 個使用者、持續 DURATION：每次執行的條件一樣，R01 加快取後才能公平比較
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '60s',
    },
  },
  // 門檻：不符合時結束代碼不是 0（數字仍然會印出來）。依 API 分開列，結果才看得出是哪一支慢
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{api:list}': ['p(95)<500'],
    'http_req_duration{api:search}': ['p(95)<500'],
    'http_req_duration{api:detail}': ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

/** 開始前執行一次：取得分類與商品 id，壓測時從裡面隨機挑（不把 id 寫死） */
export function setup() {
  const categories = http.get(`${BASE_URL}/api/categories`).json('data') || [];
  const productIds = [];
  for (let page = 1; page <= 20; page++) {
    const body = http.get(`${BASE_URL}/api/products?page=${page}&size=50`).json('data');
    if (!body || body.items.length === 0) break;
    productIds.push(...body.items.map((p) => p.id));
  }
  if (productIds.length < 100) {
    fail(`上架商品只有 ${productIds.length} 個，請先執行 loadtest/seed.sql 建立測試資料`);
  }
  console.log(`分類 ${categories.length} 個、商品 ${productIds.length} 個`);
  return { categoryIds: categories.map((c) => c.id), productIds };
}

const pick = (array) => array[Math.floor(Math.random() * array.length)];

export default function (data) {
  const r = Math.random();
  let res;
  if (r < 0.4) {
    const page = 1 + Math.floor(Math.random() * 5);
    const category = Math.random() < 0.5 ? `&categoryId=${pick(data.categoryIds)}` : '';
    // tags.name：把不同參數的網址歸成同一類，結果才不會每個網址各算一行
    res = http.get(`${BASE_URL}/api/products?page=${page}&size=12${category}`, {
      tags: { api: 'list', name: 'GET /api/products' },
    });
  } else if (r < 0.5) {
    res = http.get(`${BASE_URL}/api/products?keyword=${encodeURIComponent('帆布')}&page=1&size=12`, {
      tags: { api: 'search', name: 'GET /api/products?keyword' },
    });
  } else {
    res = http.get(`${BASE_URL}/api/products/${pick(data.productIds)}`, {
      tags: { api: 'detail', name: 'GET /api/products/{id}' },
    });
  }
  check(res, { 'status 200': (r) => r.status === 200 });
}

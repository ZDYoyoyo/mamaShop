# CLAUDE.md

## 專案說明
家人實際使用的全端電商網站，同時是我的求職作品集。
完整計畫與任務清單在 `docs/PLAN.md`，任務編號 B（後端）、F（前台）、A（後台）、D（部署）、R（Redis）、M（RabbitMQ）、P（金流與物流串接）。一次只做一個任務。

## 技術棧
- 後端（`backend/`）：Java 21、Spring Boot 4.x、Maven、Spring Data JPA、PostgreSQL、Flyway、Spring Security＋JWT、JUnit 5、Mockito
- 前端（`frontend/`）：Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus
- 部署（`deploy/`）：Docker Compose、Nginx、Let's Encrypt、GitHub Actions

## 協作規則
- 我是後端工程師，之前用 Spring MVC＋外部 Tomcat，正在學 Spring Boot、Docker 與 Linux。後端實作時請說明設計理由，特別是和傳統 Spring MVC 不同的地方。
- 前端可以由你主導實作，但請簡單說明元件與狀態管理的結構。
- 部署與 Linux 相關任務，請解釋每個指令在做什麼，讓我能自己重做一次。
- 只做指定的任務，不要順手加任務範圍外的功能。
- B08（建立訂單）改由 Claude 實作，但要特別詳細說明設計（併發、交易、回滾），B 系列完成後我會再整體研究一次。
- 每個任務完成後，列出改了哪些檔案、怎麼驗證，並建議 commit message。
- 每個任務完成後，把和 PLAN 不同的決定、刻意沒做的、之後要注意的事記錄到 `docs/NOTES.md`。

## 程式慣例
- 後端依業務模組分包（user、product、category、order），模組內分 controller／service／repository／entity／dto。
- Controller 不回傳 Entity，一律用 DTO。金額用 `BigDecimal`，資料庫用 `NUMERIC(10,2)`。
- Schema 變更只透過 Flyway migration，`ddl-auto` 設為 `validate`。
- 密碼、JWT secret、資料庫帳密等機密一律讀環境變數，不寫死、不進版控。
- 這是會上線、會存顧客個資的系統：注意輸入驗證、權限檢查（顧客只能看自己的訂單）、上傳檔案的類型與大小限制。

## 常用指令
- 啟動本機資料庫：`docker compose up -d db`
- 後端測試：`cd backend && mvn test`
- 前端開發：`cd frontend && npm run dev`
- 啟動整套服務：`docker compose up --build`

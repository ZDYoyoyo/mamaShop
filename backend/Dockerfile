# syntax=docker/dockerfile:1
#
# 後端映像檔：多階段建置（multi-stage build）
#   第 1 階段 build：用完整 JDK + Maven 編譯、打包出 jar
#   第 2 階段 runtime：只放 JRE 和 jar，不含原始碼、Maven、編譯工具
# 最後的映像檔只有第 2 階段的內容，體積小、可被利用的工具也少。
#
# 建置：docker build -t mamashop-backend ./backend
# 通常不用手動建，docker compose up --build 會自動建。

# ===== 第 1 階段：建置 =====
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 先只複製 Maven 相關檔案並下載依賴。
# Docker 會快取每一層：只要 pom.xml 沒變，下次建置就直接用快取，不用重新下載依賴（省好幾分鐘）。
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# 再複製原始碼並打包。改程式碼只會讓這一層之後重跑。
# 測試不在這裡跑（需要資料庫），由 mvnw test／CI 負責。
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ===== 第 2 階段：執行 =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 建立非 root 使用者執行程式：就算程式被攻破，攻擊者也只有這個使用者的權限
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/uploads && chown spring:spring /app/uploads

COPY --from=build --chown=spring:spring /workspace/app.jar app.jar

USER spring

# 上傳的商品圖片存在這裡，docker-compose 會掛 volume，容器重建時圖片不會消失
ENV UPLOAD_DIR=/app/uploads

EXPOSE 8080

# MaxRAMPercentage：JVM 依「容器」的記憶體上限決定 heap 大小（預設只用 25%，太保守）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

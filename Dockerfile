# 우연한 식탁: 프론트엔드(Vite) + 백엔드(Spring Boot)를 한 컨테이너로 배포
# 필요한 환경 변수: DB_URL, DB_USERNAME, DB_PASSWORD, TOKEN_SECRET, (선택) CORS_ORIGINS, OAUTH_REDIRECT_BASE,
#                   KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET

# 1) 프론트엔드 빌드
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx vite build

# 2) 백엔드 빌드 (프론트 빌드 결과를 static 으로 포함)
FROM eclipse-temurin:21-jdk AS api
WORKDIR /api
COPY backend/ ./
COPY --from=web /web/dist/ ./src/main/resources/static/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# 3) 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /api/build/libs/*-SNAPSHOT.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
# 무료 플랜(512MB)에서도 뜨도록 힙을 컨테이너 메모리의 70%로 제한
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -Dserver.port=${PORT:-8080} -jar app.jar"]

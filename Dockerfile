# ── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Gradle wrapper & build scripts 먼저 복사 (캐시 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# 소스 복사 (sourceSets: backend/main/*)
COPY backend backend

RUN ./gradlew bootJar --no-daemon -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 보안: 전용 유저로 실행 (root 금지)
RUN groupadd -r petory && useradd -r -g petory petory

# 업로드 디렉토리
RUN mkdir -p /app/uploads && chown petory:petory /app/uploads

COPY --from=builder /app/build/libs/*.jar app.jar

USER petory

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

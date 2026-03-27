# 1단계: 빌드
# gradle 빌드를 위한 JDK 이미지
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# 의존성 캐싱을 위해 gradle 파일 먼저 복사
# 소스코드가 바뀌어도 의존성이 그대로면 이 레이어는 캐시 재사용
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# 소스코드 복사 후 빌드
COPY src ./src
RUN gradle bootJar --no-daemon

# 2단계: 실행
# 빌드 결과물만 가져와서 실행하는 가벼운 이미지
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드 단계에서 생성된 jar 파일만 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 비root 사용자 생성 및 전환
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 8080 포트 오픈
EXPOSE 8080

# Health check 설정
# 30초마다 /health 엔드포인트 확인
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
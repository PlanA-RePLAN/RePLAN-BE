# 1단계: 빌드
# gradle 베이스 이미지 대신 JDK만 사용해서 프로젝트의 gradlew로 빌드
# → CI와 Docker 빌드가 동일한 Gradle wrapper 버전을 사용하게 됨
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# gradlew 실행 권한 부여를 위해 먼저 복사
COPY gradlew .
COPY gradle ./gradle
RUN chmod +x gradlew

# 의존성 캐싱을 위해 gradle 파일 먼저 복사
# 소스코드가 바뀌어도 의존성이 그대로면 이 레이어는 캐시 재사용
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

# 소스코드 복사 후 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon

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
# 30초마다 /actuator/health 엔드포인트 확인
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
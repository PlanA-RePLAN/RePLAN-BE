# 🗓️ RePLAN Backend

> 실패한 계획을 AI가 분석해 다시 세워주는 투두 관리 서비스 — 백엔드 API 서버

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-EC2%20·%20RDS%20·%20S3-232F3E?logo=amazonaws&logoColor=white)

목표를 세우고 매일의 할 일과 반복 루틴을 관리하며, 계획이 어긋났을 때 **AI(Gemini)가 원인을 분석해 재계획(Re-Plan)을 추천**하는 서비스의 백엔드입니다.

---

## ✨ 주요 기능

- **인증** — 소셜 로그인(Apple · Google · Naver) + JWT 기반 인증·권한 관리
- **목표 / 투두 / 태그** — 목표와 할 일 관리, 목표+투두 일괄 생성
- **반복 루틴** — 매일·매주·매월 반복, 엄마–하위 루틴 트리 구조, 스케줄러로 자동 생성
- **AI 리플랜** — 실패한 계획을 Gemini로 분석해 수정 · 추가 · 루틴화 추천
- **푸시 알림 (FCM)** — 마감 임박·실패 요약 알림, 앱 알림함, 알림 설정
- **월간 리포트** — Spring Batch로 사용자별 월간 통계 자동 생성
- **파일** — S3 프로필 이미지 업로드

## 🛠️ 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0 (MVC · Security · Data JPA · Batch · Validation) |
| Database | PostgreSQL (AWS RDS), Redis |
| Migration | Flyway |
| Auth | JWT (jjwt), OAuth 2.0 (Apple · Google · Naver) |
| AI / Push | Google Gemini, Firebase Admin (FCM) |
| Infra | AWS EC2 · RDS · S3 · SSM · ECR, Docker |
| CI/CD | GitHub Actions |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Code Format | Spotless (google-java-format) |
| Code Review | CodeRabbit |

## 🏗️ 아키텍처

도메인별 패키지(`domain/<name>`) 안에서 계층형 구조로 요청을 처리합니다.

```
HTTP 요청
   │
   ▼
Controller    요청/응답 매핑 · Bean Validation
   │          (implements *ControllerDocs → Swagger 문서를 코드와 분리)
   │  ▲  Request / Response DTO (record)
   ▼  │
Service       비즈니스 로직 · @Transactional 경계
   │
   ▼
Repository    데이터 접근 (Spring Data JPA · JPQL)
   │
   ▼
Entity  ◀──▶  PostgreSQL   (스키마는 Flyway 마이그레이션으로 관리)

── 공통(cross-cutting) ──────────────────────────────
JWT 인증 필터 · 전역 예외 핸들러(@RestControllerAdvice) · Redis · S3
```

- **Controller ↔ Docs 분리** — HTTP 매핑은 `Controller`, Swagger 어노테이션은 `*ControllerDocs` 인터페이스에 두어 문서와 로직을 분리
- **DTO는 record** — 요청·응답을 record로 주고받고, 목록/상세는 **용도별 DTO로 분리**해 불필요한 필드 직렬화를 줄임
- **전역 예외 처리** — `@RestControllerAdvice` + 커스텀 에러 코드로 일관된 에러 응답

## 💡 주요 기술 구현

- **소셜 로그인** — Apple `identityToken`을 애플 공개키(JWKS)로 **서명 검증**, `client_secret`(JWT) 생성 후 인가코드 교환, 탈퇴 시 토큰 철회까지 처리. 리프레시·임시 토큰은 **Redis에 TTL과 함께 저장**
- **반복 루틴 설계** — 미래 투두를 미리 만들지 않고, 사용자가 미래 일정을 수정하면 `(루틴, 날짜)`를 키로 한 **예외(override) 테이블**에 변경분만 저장. 조회 시 실제 투두와 병합하고, 스케줄러가 그날 투두를 생성할 때 예외를 반영 → **저장 비용과 정합성**을 함께 확보
- **AI 리플랜 파이프라인** — 실패한 계획을 분석해 **Gemini 프롬프트를 유형별로 설계**하고, LLM 응답을 파싱해 수정·추가·루틴화 추천으로 변환
- **월간 리포트** — **Spring Batch** Job으로 사용자별 통계를 배치 집계
- **푸시 알림** — FCM **data-only 메시지**로 전송해 브라우저·서비스워커 중복 표시 문제 해결
- **DB 무결성** — 반복 일정 중복 생성을 막는 **조건부 유니크 인덱스**, Flyway로 20여 개의 스키마 변경을 안전하게 관리

## ☁️ 인프라 & 배포 (AWS `ap-northeast-2`)

<img width="3840" height="2160" alt="replan-architecture" src="https://github.com/user-attachments/assets/8737a5b5-d1a1-48d5-8f73-8dc802d929b4" />


| 영역 | 구성 |
|------|------|
| **네트워크** | VPC를 Public/Private 서브넷으로 분리. Route53으로 도메인 연결 |
| **런타임** | EC2 한 대에서 Docker로 **Nginx(리버스 프록시 · Let's Encrypt SSL) + Spring Boot App + Redis**를 함께 운영 |
| **데이터** | **RDS(PostgreSQL)** 메인 DB, **S3 + CloudFront**로 프로필 이미지 저장·배포 |
| **보안** | 시크릿 18종(DB 비밀번호·JWT·소셜 로그인 키·Firebase 키 등)을 **SSM Parameter Store**에서 주입, 민감 값은 SecureString 암호화 |
| **관측** | **CloudWatch**로 로그·메트릭 모니터링 |

**CI/CD (GitHub Actions)**

- **CI** — PR마다 JDK 17로 `./gradlew build` (테스트 + Spotless 포맷 검사)
- **CD** — `main` 병합 시 ① **OIDC로 AWS 임시 자격증명 발급**(장기 액세스 키 불필요) → ② Docker 이미지 빌드 후 **ECR push** → ③ **EC2에 SSH 접속** → SSM 시크릿을 `.env`로 주입 → `docker compose pull && up -d`로 새 이미지 교체
- **Release** — `develop → main` 병합 시 버전 태그·릴리즈 자동 생성

## ✅ 빌드 · 포맷 검사

CI는 `./gradlew build`(Spotless 포맷 검사 포함)를 실행합니다. PR 전 로컬에서 통과시켜 주세요. (Spotless는 JDK 17에서 실행)

```bash
./gradlew spotlessApply   # 포맷 자동 정리
./gradlew build           # 포맷 검사 + 컴파일 + 테스트
```

## 🌿 브랜치 전략 · 커밋 규칙

GitHub Flow + Release Branch. 커밋·PR 제목은 `태그: 한 줄 요약` (예: `Feat: 리플랜 추천 새로고침 추가`)

```
main    → 배포 (태그·릴리즈 자동 생성)    feat/*  → 기능 (feat/<이슈번호>-<이름>)
develop → 개발 완료 기능 병합             fix/*   → 버그 수정
```

| 태그 | 설명 |
| :--- | :--- |
| `Feat` / `Fix` | 기능 추가 / 버그 수정 |
| `Refactor` / `Style` / `Design` | 리팩토링 / 포맷 / UI |
| `Docs` / `Test` / `Chore` | 문서 / 테스트 / 빌드·설정 |
| `!BREAKING CHANGE` / `!HOTFIX` | 큰 API 변경 / 긴급 버그 |

[PR 템플릿](https://github.com/PlanA-RePLAN/RePLAN-BE/blob/main/.github/pull_request_template.md) · [이슈 템플릿](https://github.com/PlanA-RePLAN/RePLAN-BE/blob/main/.github/ISSUE_TEMPLATE)

## 👥 팀원

| 이름 | GitHub |
|------|--------|
| 서지민 | [@SeoJimin1234](https://github.com/SeoJimin1234) |
| 김희윤 | [@heyoonyoon](https://github.com/heyoonyoon) |

# RePLAN Backend

> AI 기반 개인 맞춤형 투두 관리 서비스

---

## 🛠️ 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot |
| Build | Gradle-Groovy |
| Database | MySQL (AWS RDS) |
| ORM | Spring Data JPA |
| Infra | AWS EC2, RDS, S3 |
| CI/CD | GitHub Actions, Docker |
| API 문서 | Swagger (Springdoc OpenAPI) |
| 코드 포맷 | Spotless (Google Format) |
| AI 코드 리뷰 | CodeRabbit |

---

## 📁 패키지 구조

```
src/main/java/plana/replan/
├── domain
│   ├── user
│   ├── todo
│   └── crew
└── global
    ├── config
    ├── exception
    └── response
```

---

## 🌿 브랜치 전략

GitHub Flow + Release Branch

```
main     → 배포 브랜치
develop  → 개발 완성 기능 병합
feat/*   → 기능 개발
```

---

## ✉️ 커밋 컨벤션

| 태그 | 설명 |
|------|------|
| Feat | 새로운 기능 추가 |
| Fix | 버그 수정 |
| Refactor | 코드 리팩토링 |
| Chore | 빌드, 패키지 설정 |
| Docs | 문서 수정 |
| Test | 테스트 코드 |
| Rename | 파일/폴더명 수정 |
| Remove | 파일 삭제 |

---

## 📌 중간 발표 (04/12) 목표

- [ ] 로그인 / 인증
- [ ] 온보딩
- [ ] 홈 (Todo CRUD)
- [ ] 통계 (가능하다면)

---

## 👥 팀원

| 이름 | GitHub |
|------|--------|
| 서지민 | [@SeoJimin1234](https://github.com/SeoJimin1234) |
| 김희윤 | [@heyoonyoon](https://github.com/heyoonyoon) |

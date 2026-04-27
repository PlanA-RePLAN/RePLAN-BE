# RePLAN Backend

> AI 기반 개인 맞춤형 투두 관리 서비스

---

### PR 규칙 및 템플릿
- message 제목 태그

| 제목 태그 이름 | 설명 |
| :--- | :--- |
| **Feat** | 새로운 기능을 추가할 경우 |
| **Fix** | 버그를 고친 경우 |
| **Design** | CSS 등 사용자 UI 디자인 변경 |
| **!BREAKING CHANGE** | 커다란 API 변경의 경우 |
| **!HOTFIX** | 급하게 치명적인 버그를 고쳐야 하는 경우 |
| **Style** | 코드 포맷 변경, 세미 콜론 누락, 코드 수정이 없는 경우 |
| **Refactor** | 프로덕션 코드 리팩토링 |
| **Comment** | 필요한 주석 추가 및 변경 |
| **Docs** | 문서를 수정한 경우 |
| **Test** | 테스트 추가, 테스트 리팩토링 (프로덕션 코드 변경 X) |
| **Chore** | 빌드 테스트 업데이트, 패키지 매니저를 설정하는 경우 (프로덕션 코드 변경 X) |
| **Rename** | 파일 혹은 폴더명을 수정하거나 옮기는 작업만인 경우 |
| **Remove** | 파일을 삭제하는 작업만 수행한 경우 |

- [PR템플릿](https://github.com/PlanA-RePLAN/RePLAN-BE/blob/main/.github/pull_request_template.md)
- [Issue 템플릿](https://github.com/PlanA-RePLAN/RePLAN-BE/blob/main/.github/ISSUE_TEMPLATE/%EC%9D%B4%EC%8A%88-%ED%85%9C%ED%94%8C%EB%A6%BF.md)

---

## ⚙️ 로컬 개발 환경 설정

### 1. 설정 파일 준비

example 파일을 복사해서 실제 파일을 만들고 값을 채웁니다.

```bash
cp docker-compose.yml.example docker-compose.yml
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

> `docker-compose.yml`과 `application-local.yaml`은 gitignore에 등록되어 있습니다.  
> 두 파일의 DB 정보를 동일하게 맞춰주세요.

### 2. Docker 컨테이너 실행

```bash
docker compose up -d
```

PostgreSQL(5432)과 Redis(6379)가 함께 실행됩니다.

### 3. 앱 실행

IntelliJ에서 `local` 프로파일로 실행합니다.

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



---

## 🌿 브랜치 전략

GitHub Flow + Release Branch

```
main     → 배포 브랜치
develop  → 개발 완성 기능 병합
feat/*   → 기능 개발
```



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

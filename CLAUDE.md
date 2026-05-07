# CLAUDE.md

## 필수 규칙

### 빌드 & 테스트
**매 작업 완료 후 반드시 빌드(테스트 포함)를 실행해야 한다.**

```bash
./gradlew build
```

빌드가 실패하면 해당 작업은 완료된 것으로 간주하지 않는다.

### Swagger API 명세 (모든 API 필수)

**Controller는 반드시 Docs 인터페이스와 구현 클래스로 분리한다.**
- `XxxControllerDocs.java` — 모든 Swagger 어노테이션 (`@Operation`, `@ApiResponses`, `@Parameter`, `@Tag`)
- `XxxController.java` — `implements XxxControllerDocs`, HTTP 매핑 어노테이션 + 메서드 구현만

**`XxxControllerDocs` 인터페이스에 반드시 포함할 항목:**

1. **`@Operation`** — `summary`(한 줄 요약) + `description`(아래 표 구조 포함)

2. **description 표 구조** — 필수 여부는 이모지 사용 (✅ 필수 / ❌ 선택)

   - **Request Headers 표** (모든 API)
     ```
     | 헤더명 | 필수 여부 | 타입 | 설명 |
     | Authorization | ✅ 필수 | String | Bearer {accessToken} |
     | Content-Type  | ✅ 필수 | String | application/json (POST/PUT만) |
     ```
   - **Request Body 표** (POST/PUT)
     ```
     | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
     ```
   - **Path Variable 표** (DELETE/GET with path)
     ```
     | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
     ```
   - **Query Parameters 표** (GET with query)
     ```
     | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
     ```
   - **Response Elements 표** (GET 조회 API)
     ```
     | 필드명 | 타입 | 설명 |
     ```
   - **무한스크롤 사용법** (페이지네이션 API)
     ```
     Step 1. 첫 요청 — cursor 없이 호출
     Step 2. 다음 페이지 — nextCursor를 cursor에 전달
     Step 3. 종료 — hasNext=false 또는 nextCursor=null이면 마지막 페이지
     ```

3. **`@ApiResponses`** — 200 성공 + 발생 가능한 모든 에러 코드, 각각 `@ExampleObject`로 JSON 응답 예시 포함. 401은 "토큰 없음"과 "만료된 토큰" 두 케이스를 각각 ExampleObject로 작성

4. **`@Parameter`** — 쿼리/경로 파라미터마다 `description` + `example`

5. **DTO** — 모든 필드에 `@Schema(description = "...", example = "...")`

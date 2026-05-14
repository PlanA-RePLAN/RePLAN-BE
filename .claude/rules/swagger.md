# Swagger 규칙

## 파일 분리 패턴 (필수)

모든 Controller는 Docs 인터페이스와 구현 클래스로 **반드시 분리**한다.

```text
XxxControllerDocs.java  ← 모든 Swagger 어노테이션 (@Operation, @ApiResponses, @Parameter, @Tag)
XxxController.java      ← implements XxxControllerDocs, HTTP 매핑 어노테이션 + 메서드 구현만
```

```java
// XxxControllerDocs.java
@Tag(name = "Xxx", description = "...")
public interface XxxControllerDocs {
  @Operation(summary = "...", description = "...")
  @ApiResponses({...})
  ResponseEntity<ApiResult<XxxResponse>> createXxx(...);
}

// XxxController.java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController implements XxxControllerDocs {
  @Override
  @PostMapping
  public ResponseEntity<ApiResult<XxxResponse>> createXxx(...) {
    return ResponseEntity.ok(ApiResult.ok(xxxService.create(...)));
  }
}
```

## 타입 표기 기준 (JSON 타입 사용)

표의 타입 열은 **JSON 타입 기준**으로 작성한다. Java 타입(`String`, `Long`, `LocalDateTime` 등) 사용 금지.
프론트 개발자는 Java 타입을 모르므로 항상 JSON 관점으로 작성한다.

| Java 타입 | 표에 쓸 타입 | 비고 |
|-----------|------------|------|
| `String` | `string` | |
| `Long`, `Integer` | `integer` | |
| `Boolean` | `boolean` | |
| `List<T>` | `array` | |
| `LocalDateTime` | `string` | 설명 열에 `(ISO 8601 형식)` 추가, 예시 열에 실제 값 |
| `LocalDate` | `string` | 설명 열에 `(yyyy-MM-dd 형식)` 추가, 예시 열에 실제 값 |

예시:
```text
| dueDate | ❌ 선택 | string | 목표 기한 (ISO 8601 형식). 생략 가능 | `"2025-12-31T00:00:00"` |
```

## 필수 여부 이모지

- ✅ 필수: 반드시 포함해야 하는 필드. 없으면 400.
- ❌ 선택: 생략 가능. **생략과 `null` 명시는 동일하게 처리됨.**

❌ 선택 필드가 있는 Request Body 표 하단에 반드시 아래 주석 추가:
> ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

## description 표 구조

### 모든 API — Request Headers 표
```text
| 헤더명 | 필수 여부 | 타입 | 설명 |
|--------|-----------|------|------|
| Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
| Content-Type  | ✅ 필수 | string | `application/json` (POST/PUT만) |
```

### POST/PUT — Request Body 표
```text
| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
|--------|-----------|------|------|------|
| field1 | ✅ 필수 | string | 설명 | `"예시"` |
| field2 | ❌ 선택 | string | 설명 (ISO 8601 형식) | `"2025-12-31T00:00:00"` |
```

### DELETE/GET with path — Path Variable 표
```text
| 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
|-----------|-----------|------|------|------|
| id | ✅ 필수 | integer | 대상 ID | `42` |
```

### GET with query — Query Parameters 표
```text
| 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
|-----------|-----------|------|--------|------|------|
| cursor | ❌ 선택 | integer | 없음 | 이전 응답의 nextCursor 값 | `37` |
| size   | ❌ 선택 | integer | `10`  | 페이지 크기 | `10` |
```

### GET 조회 API — Response Elements 표
```text
| 필드명 | 타입 | 설명 |
|--------|------|------|
| items  | array | 목록 |
| nextCursor | integer | 다음 cursor. 마지막이면 null |
| hasNext | boolean | 다음 페이지 존재 여부 |
```

### 무한스크롤 API — 사용법 설명 (description에 포함)
```text
**Step 1. 첫 번째 요청** — cursor 없이 호출
GET /api/xxx?size=10

**Step 2. 다음 페이지 요청** — 응답의 nextCursor를 cursor에 전달
GET /api/xxx?cursor=37&size=10

**Step 3. 종료 조건** — hasNext=false 또는 nextCursor=null이면 마지막 페이지
```

## Request Body 예시 — optional 필드 있을 때

optional 필드가 있는 POST/PUT API는 반드시 `@io.swagger.v3.oas.annotations.parameters.RequestBody`로 최소 2가지 예시 제공:
1. 모든 필드 포함 예시
2. 필수 필드만 포함 (optional 생략) 예시

```java
@io.swagger.v3.oas.annotations.parameters.RequestBody(
    content = @Content(
        mediaType = "application/json",
        examples = {
          @ExampleObject(
              name = "전체 필드 포함",
              value = """{"title": "토익 900점 달성", "dueDate": "2025-12-31T00:00:00"}"""),
          @ExampleObject(
              name = "필수 필드만 (optional 생략)",
              summary = "optional 필드를 생략하면 null로 처리됨",
              value = """{"title": "토익 900점 달성"}""")
        }))
@RequestBody XxxRequestDto request
```

> **주의:** Spring의 `@RequestBody`와 이름이 겹치므로 Swagger 어노테이션은 반드시 전체 경로
> `@io.swagger.v3.oas.annotations.parameters.RequestBody`로 작성한다.

## @ApiResponse 규칙

- 200 성공 + **발생 가능한 모든 에러 코드** 포함
- 각 응답에 `@ExampleObject`로 JSON 예시 필수
- 401은 반드시 두 케이스를 각각 ExampleObject로 작성:
  - `"토큰 없음"` → `EMPTY_TOKEN`
  - `"만료된 토큰"` → `EXPIRED_TOKEN`

## @Parameter 규칙

쿼리/경로 파라미터마다 `description` + `example` 필수.

## DTO @Schema 규칙

모든 필드에 `@Schema(description = "...", example = "...")` 적용.

```java
@Schema(description = "목표 생성 요청")
public record GoalCreateRequestDto(
    @Schema(description = "목표 제목", example = "토익 900점 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String title,

    @Schema(description = "목표 기한 (ISO 8601 형식)", example = "2025-12-31T00:00:00")
    LocalDateTime dueDate
) {}
```

> **예외:** `List<다른DTO>` 타입 필드는 참조 DTO에 example이 있으면 `example` 생략 가능.
> 직접 example 문자열을 작성하면 DTO 변경 시 문서가 낡아지는 문제가 생긴다.

## 마크다운 코드 블록 언어 지정 (필수)

문서, 블로그 글, PR 본문 등 모든 마크다운 작성 시 코드 블록에 반드시 언어 식별자를 붙인다.

```text
```java      ← Java 코드
```json      ← JSON 예시
```bash      ← 쉘 명령어
```text      ← 표·일반 텍스트 (언어 없을 때 fallback)
```sql       ← SQL
```

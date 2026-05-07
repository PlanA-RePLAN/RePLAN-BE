# Swagger 규칙

## 파일 분리 패턴 (필수)

모든 Controller는 Docs 인터페이스와 구현 클래스로 **반드시 분리**한다.

```
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

## 필수 여부 이모지

- ✅ 필수
- ❌ 선택

## description 표 구조

### 모든 API — Request Headers 표
```
| 헤더명 | 필수 여부 | 타입 | 설명 |
|--------|-----------|------|------|
| Authorization | ✅ 필수 | String | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
| Content-Type  | ✅ 필수 | String | `application/json` (POST/PUT만) |
```

### POST/PUT — Request Body 표
```
| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
|--------|-----------|------|------|------|
| field1 | ✅ 필수 | String | 설명 | `"예시"` |
| field2 | ❌ 선택 | LocalDateTime | 설명 | `"2025-12-31T00:00:00"` |
```

### DELETE/GET with path — Path Variable 표
```
| 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
|-----------|-----------|------|------|------|
| id | ✅ 필수 | Long | 대상 ID | `42` |
```

### GET with query — Query Parameters 표
```
| 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
|-----------|-----------|------|--------|------|------|
| cursor | ❌ 선택 | Long | 없음 | 이전 응답의 nextCursor 값 | `37` |
| size   | ❌ 선택 | int  | `10`  | 페이지 크기 | `10` |
```

### GET 조회 API — Response Elements 표
```
| 필드명 | 타입 | 설명 |
|--------|------|------|
| items  | List\<XxxResponse\> | 목록 |
| nextCursor | Long | 다음 cursor. 마지막이면 null |
| hasNext | boolean | 다음 페이지 존재 여부 |
```

### 무한스크롤 API — 사용법 설명 (description에 포함)
```
**Step 1. 첫 번째 요청** — cursor 없이 호출
GET /api/xxx?size=10

**Step 2. 다음 페이지 요청** — 응답의 nextCursor를 cursor에 전달
GET /api/xxx?cursor=37&size=10

**Step 3. 종료 조건** — hasNext=false 또는 nextCursor=null이면 마지막 페이지
```

## @ApiResponse 규칙

- 200 성공 + **발생 가능한 모든 에러 코드** 포함
- 각 응답에 `@ExampleObject`로 JSON 예시 필수
- 401은 반드시 두 케이스를 각각 ExampleObject로 작성:
  - `"토큰 없음"` → `EMPTY_TOKEN`
  - `"만료된 토큰"` → `EXPIRED_TOKEN`

## DTO @Schema 규칙

모든 필드에 `@Schema(description = "...", example = "...")` 적용.

```java
@Schema(description = "목표 생성 요청")
public record GoalCreateRequest(
    @Schema(description = "목표 제목", example = "토익 900점 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String title,

    @Schema(description = "목표 기한 (ISO 8601)", example = "2025-12-31T00:00:00")
    LocalDateTime dueDate
) {}
```

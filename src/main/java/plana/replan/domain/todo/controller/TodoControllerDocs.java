package plana.replan.domain.todo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Todo", description = "투두 관련 API")
public interface TodoControllerDocs {

  @Operation(
      summary = "투두 상세 조회",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Path Variable**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | todoId | ✅ 필수 | integer | 조회할 투두 ID | `1` |

          **반환 필드**
          - `routineType`: 루틴에 연결된 투두인 경우 `DAILY` / `WEEKLY` / `MONTHLY`, 일반 투두는 `null`
          - `tagId`, `tagTitle`, `tagColor`: 태그가 없으면 모두 `null`
          - `subTodos`: 하위 투두 목록 (없으면 빈 배열)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "투두 상세 조회 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "todoId": 1,
                                "title": "토익 단어 50개 외우기",
                                "dueDate": "2025-12-31T23:59:59",
                                "isCompleted": false,
                                "tagId": 3,
                                "tagTitle": "영어",
                                "tagColor": "BLUE",
                                "routineType": "DAILY",
                                "subTodos": [
                                  {
                                    "todoId": 10,
                                    "title": "단어장 챕터 1 읽기",
                                    "isCompleted": false
                                  },
                                  {
                                    "todoId": 11,
                                    "title": "단어장 챕터 2 읽기",
                                    "isCompleted": true
                                  }
                                ]
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아닌 경우)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "TODO_NOT_FOUND",
                                "message": "투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<TodoDetailResponseDto>> getTodoDetail(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회할 투두 ID", example = "1") @PathVariable Long todoId);

  @Operation(
      summary = "투두 목록 조회",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Query Parameters**

          | 파라미터명 | 필수 여부 | 타입 | 기본값 | 설명 | 예시 |
          |-----------|-----------|------|--------|------|------|
          | filter | ❌ 선택 | string | `all` | 조회 범위 필터 (`all`, `day`, `week`, `month`) | `day` |
          | sort | ❌ 선택 | string | `priority` | 정렬 기준 (`priority`, `duedate`) | `duedate` |

          **filter 값별 조회 조건**

          | filter | 조회 대상 |
          |--------|----------|
          | `all` | 완료되지 않은 모든 투두 (마감일 무관) |
          | `day` | 오늘 마감인 미완료 투두 + 오늘 완료된 투두 |
          | `week` | 오늘부터 7일 이내 마감인 미완료 투두 |
          | `month` | 오늘부터 한 달 이내 마감인 미완료 투두 |

          **sort 값별 정렬 기준** (pinned는 항상 최우선)

          | sort | 정렬 |
          |------|------|
          | `priority` | isPinned DESC → sortOrder ASC |
          | `duedate` | isPinned DESC → dueDate ASC (null 마지막) |

          **day 필터의 추가 정렬 규칙**
          - 미완료 투두 먼저, 완료 투두 나중 → 각 그룹 내에서 선택한 sort 기준 적용

          **반환 필드**
          - `routineType`: 루틴에 연결된 투두인 경우 `DAILY` / `WEEKLY` / `MONTHLY`, 일반 투두는 `null`
          - `tagId`, `tagTitle`, `tagColor`: 태그가 없으면 모두 `null`
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "투두 목록 조회 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": [
                                {
                                  "todoId": 1,
                                  "title": "토익 단어 50개 외우기",
                                  "dueDate": "2025-12-31T23:59:59",
                                  "isPinned": true,
                                  "sortOrder": 1000.0,
                                  "isCompleted": false,
                                  "tagId": 3,
                                  "tagTitle": "영어",
                                  "tagColor": "BLUE",
                                  "routineType": "DAILY"
                                },
                                {
                                  "todoId": 2,
                                  "title": "운동하기",
                                  "dueDate": null,
                                  "isPinned": false,
                                  "sortOrder": 10000.0,
                                  "isCompleted": false,
                                  "tagId": null,
                                  "tagTitle": null,
                                  "tagColor": null,
                                  "routineType": null
                                }
                              ],
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 filter 또는 sort 값",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "잘못된 filter",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_FILTER",
                              "message": "유효하지 않은 필터 값입니다. (all, day, week, month 중 하나)",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "잘못된 sort",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_SORT",
                              "message": "유효하지 않은 정렬 값입니다. (priority, duedate 중 하나)",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                }))
  })
  ResponseEntity<ApiResult<List<TodoListResponseDto>>> getTodos(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회 범위 필터 (all/day/week/month)", example = "all")
          @RequestParam(defaultValue = "all")
          String filter,
      @Parameter(description = "정렬 기준 (priority/duedate)", example = "priority")
          @RequestParam(defaultValue = "priority")
          String sort);

  @Operation(
      summary = "투두 생성",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **비즈니스 로직**
          1. JwtFilter에서 AccessToken 검증 후 userId를 SecurityContext에 저장
          2. @AuthenticationPrincipal 로 userId 주입
          3. title(필수), due_date(선택), tag_id(선택)를 받아 투두 생성
          4. tag_id가 주어진 경우 태그 존재 여부 확인 후 연결

          **참고**: tag_id가 제공된 경우 해당 태그가 존재하지 않으면 404 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "투두 생성 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title 누락)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 400,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "INVALID_INPUT",
                                "message": "제목은 필수입니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(responseCode = "401", description = "AccessToken 없음 또는 유효하지 않은 토큰"),
    @ApiResponse(
        responseCode = "404",
        description = "유저 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "TAG_NOT_FOUND",
                                "message": "태그를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<TodoResponseDto>> createTodo(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TodoCreateRequestDto request);

  @Operation(
      summary = "하위 투두 생성",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Path Variable**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | parentId | ✅ 필수 | integer | 상위 투두 ID | `42` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 하위 투두 제목 | `"개념 정리하기"` |

          **비즈니스 로직**
          1. parentId로 상위 투두 조회
          2. 상위 투두가 요청 유저 소유인지 검증
          3. 하위 투두 생성 후 parentId 포함 응답 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "하위 투두 생성 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "todoId": 43,
                                "title": "개념 정리하기",
                                "dueDate": null,
                                "isCompleted": false,
                                "tagId": null,
                                "parentId": 42
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title 누락)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 400,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "INVALID_INPUT",
                                "message": "제목은 필수입니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "상위 투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아닌 경우)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "TODO_NOT_FOUND",
                                "message": "투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<TodoResponseDto>> createSubTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "상위 투두 ID", example = "42") @PathVariable Long parentId,
      @Valid @RequestBody SubTodoCreateRequestDto request);

  @Operation(
      summary = "하위 투두 수정",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Path Variables**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | parentId | ✅ 필수 | integer | 상위 투두 ID | `42` |
          | subTodoId | ✅ 필수 | integer | 수정할 하위 투두 ID | `43` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 하위 투두 제목 | `"개념 정리하기 (수정)"` |

          **비즈니스 로직**
          1. subTodoId로 하위 투두 조회
          2. 하위 투두가 요청 유저 소유인지 검증
          3. 하위 투두의 parent가 parentId와 일치하는지 검증
          4. 제목 수정 후 수정된 투두 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "하위 투두 수정 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 200,
                              "success": true,
                              "data": {
                                "todoId": 43,
                                "title": "개념 정리하기 (수정)",
                                "dueDate": null,
                                "isCompleted": false,
                                "tagId": null,
                                "parentId": 42
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류 (title 누락)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 400,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "INVALID_INPUT",
                                "message": "제목은 필수입니다.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "하위 투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 parentId 불일치)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "TODO_NOT_FOUND",
                                "message": "투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<ApiResult<TodoResponseDto>> updateSubTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "상위 투두 ID", example = "42") @PathVariable Long parentId,
      @Parameter(description = "수정할 하위 투두 ID", example = "43") @PathVariable Long subTodoId,
      @Valid @RequestBody SubTodoUpdateRequestDto request);

  @Operation(
      summary = "하위 투두 삭제",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Path Variables**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | parentId | ✅ 필수 | integer | 상위 투두 ID | `42` |
          | subTodoId | ✅ 필수 | integer | 삭제할 하위 투두 ID | `43` |

          **비즈니스 로직**
          1. subTodoId로 하위 투두 조회
          2. 하위 투두가 요청 유저 소유인지 검증
          3. 하위 투두의 parent가 parentId와 일치하는지 검증
          4. soft delete 처리 (deleted_at 설정)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "하위 투두 삭제 성공"),
    @ApiResponse(
        responseCode = "401",
        description = "AccessToken 없음 또는 만료",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EMPTY_TOKEN",
                              "message": "토큰이 없습니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {
                            "status": 401,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "EXPIRED_TOKEN",
                              "message": "만료된 토큰입니다.",
                              "detail": null
                            }
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "하위 투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 parentId 불일치)",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "status": 404,
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "TODO_NOT_FOUND",
                                "message": "투두를 찾을 수 없습니다.",
                                "detail": null
                              }
                            }
                            """)))
  })
  ResponseEntity<Void> deleteSubTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "상위 투두 ID", example = "42") @PathVariable Long parentId,
      @Parameter(description = "삭제할 하위 투두 ID", example = "43") @PathVariable Long subTodoId);
}

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
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCompleteRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;
import plana.replan.domain.todo.dto.TodoOrderRequestDto;
import plana.replan.domain.todo.dto.TodoPinRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.domain.todo.dto.TodoUpdateRequestDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Todo", description = "투두 관련 API")
public interface TodoControllerDocs {

  @Operation(
      summary = "핀된 투두 목록 조회",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **조회 조건**: 미완료 + 핀된(`isPinned = true`) 부모 투두만 반환

          **정렬**: sortOrder ASC
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "핀된 투두 목록 조회 성공",
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
                                  "todoId": 3,
                                  "title": "토익 시험 접수",
                                  "dueDate": "2025-12-31T23:59:59",
                                  "isPinned": true,
                                  "sortOrder": 500.0,
                                  "isCompleted": false,
                                  "tagId": 3,
                                  "tagTitle": "영어",
                                  "tagColor": "BLUE",
                                  "routineType": null,
                                  "isOverdue": false
                                }
                              ],
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
                }))
  })
  ResponseEntity<ApiResult<List<TodoListResponseDto>>> getPinnedTodos(
      @AuthenticationPrincipal Long userId);

  @Operation(
      summary = "투두 우선순위 변경",
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
          | todoId | ✅ 필수 | integer | 순서를 변경할 투두 ID | `3` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | prevTodoId | ❌ 선택 | integer | 바로 앞에 위치할 투두의 ID. 맨 앞으로 이동 시 null | `1` |
          | nextTodoId | ❌ 선택 | integer | 바로 뒤에 위치할 투두의 ID. 맨 뒤로 이동 시 null | `5` |

          **제약 조건**: `prevTodoId`와 `nextTodoId`를 동시에 null로 보내면 400 반환

          **정렬 방식**: `sortOrder = (prevTodoId의 sortOrder + nextTodoId의 sortOrder) / 2`
          - 맨 앞으로 이동 시: `prevSortOrder = 0` 으로 계산
          - 맨 뒤로 이동 시: `nextSortOrder = prevSortOrder + 20000` 으로 계산

          **제약 조건**: 하위 투두(sub-todo)는 순서 변경 불가
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "우선순위 변경 성공",
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
                                "todoId": 3,
                                "title": "토익 단어 50개 외우기",
                                "dueDate": null,
                                "isPinned": false,
                                "sortOrder": 15000.0,
                                "isCompleted": false,
                                "tagId": null,
                                "tagTitle": null,
                                "tagColor": null,
                                "routineType": null,
                                "isOverdue": false
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "prevTodoId와 nextTodoId 모두 null",
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
                                "message": "잘못된 입력입니다.",
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
        description = "투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 하위 투두인 경우)",
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
  ResponseEntity<ApiResult<TodoListResponseDto>> reorderTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "순서를 변경할 투두 ID", example = "3") @PathVariable Long todoId,
      @Valid @RequestBody TodoOrderRequestDto request);

  @Operation(
      summary = "투두 완료/미완료 처리",
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
          | todoId | ✅ 필수 | integer | 완료/미완료 처리할 투두 ID | `1` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | isCompleted | ✅ 필수 | boolean | `true`면 완료, `false`면 미완료 처리 | `true` |

          **동작**: 완료 처리 시 `completedTime`이 현재 시각으로 기록되고, 미완료 처리 시 `null`로 초기화됨

          **제약 조건**: 하위 투두(sub-todo)는 이 API로 처리 불가 — todoId가 하위 투두인 경우 404 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "완료/미완료 처리 성공",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "완료 처리",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "todoId": 1,
                              "title": "토익 단어 50개 외우기",
                              "dueDate": null,
                              "isPinned": false,
                              "sortOrder": 1000.0,
                              "isCompleted": true,
                              "tagId": null,
                              "tagTitle": null,
                              "tagColor": null,
                              "routineType": null,
                              "isOverdue": false
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "미완료 처리",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "todoId": 1,
                              "title": "토익 단어 50개 외우기",
                              "dueDate": null,
                              "isPinned": false,
                              "sortOrder": 1000.0,
                              "isCompleted": false,
                              "tagId": null,
                              "tagTitle": null,
                              "tagColor": null,
                              "routineType": null,
                              "isOverdue": false
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "isCompleted 누락",
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
                                "message": "잘못된 입력입니다.",
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
        description = "투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 하위 투두인 경우)",
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
  ResponseEntity<ApiResult<TodoListResponseDto>> completeTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "완료/미완료 처리할 투두 ID", example = "1") @PathVariable Long todoId,
      @Valid @RequestBody TodoCompleteRequestDto request);

  @Operation(
      summary = "투두 핀/언핀",
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
          | todoId | ✅ 필수 | integer | 핀/언핀할 투두 ID | `1` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | isPinned | ✅ 필수 | boolean | `true`면 핀, `false`면 언핀 | `true` |

          **제약 조건**: 하위 투두(sub-todo)는 핀 불가 — todoId가 하위 투두인 경우 404 반환
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "핀/언핀 성공",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "핀",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "todoId": 1,
                              "title": "토익 단어 50개 외우기",
                              "dueDate": null,
                              "isPinned": true,
                              "sortOrder": 1000.0,
                              "isCompleted": false,
                              "tagId": null,
                              "tagTitle": null,
                              "tagColor": null,
                              "routineType": null,
                              "isOverdue": false
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "언핀",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "todoId": 1,
                              "title": "토익 단어 50개 외우기",
                              "dueDate": null,
                              "isPinned": false,
                              "sortOrder": 1000.0,
                              "isCompleted": false,
                              "tagId": null,
                              "tagTitle": null,
                              "tagColor": null,
                              "routineType": null,
                              "isOverdue": false
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "isPinned 누락",
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
                                "message": "잘못된 입력입니다.",
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
        description = "투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 하위 투두인 경우)",
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
  ResponseEntity<ApiResult<TodoListResponseDto>> pinTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "핀/언핀할 투두 ID", example = "1") @PathVariable Long todoId,
      @Valid @RequestBody TodoPinRequestDto request);

  @Operation(
      summary = "투두 삭제",
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
          | todoId | ✅ 필수 | integer | 삭제할 투두 ID | `1` |

          **비즈니스 로직**
          1. todoId로 투두 조회
          2. 요청 유저 소유인지 검증
          3. 하위 투두(sub-todo)의 ID를 직접 전달한 경우 404 반환
          4. 하위 투두가 있으면 모두 함께 soft delete
          5. 투두 soft delete (deleted_at 설정)
          6. 연결된 루틴은 삭제하지 않음 (루틴은 계속 미래 투두를 생성함)
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "투두 삭제 성공"),
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
        description = "투두를 찾을 수 없음 (존재하지 않거나 본인 소유가 아니거나 하위 투두 ID인 경우)",
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
  ResponseEntity<Void> deleteTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "삭제할 투두 ID", example = "1") @PathVariable Long todoId);

  @Operation(
      summary = "투두 수정",
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
          | todoId | ✅ 필수 | integer | 수정할 투두 ID | `1` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ❌ 선택 | string | 투두 제목. null이면 변경하지 않음. 빈 문자열 불가 | `"토익 단어 50개 외우기"` |
          | dueDate | ❌ 선택 | string | 마감 일시 (ISO 8601 형식). null이면 마감일 제거 | `"2025-12-31T23:59:59"` |
          | tagId | ❌ 선택 | integer | 태그 ID. null이면 태그 제거 | `3` |
          | routineType | ❌ 선택 | string | 반복 유형 (`DAILY`/`WEEKLY`/`MONTHLY`). **이미 반복에 연결된 투두에는 무시됨** | `"WEEKLY"` |
          | routineDate | ❌ 선택 | integer | 반복 날짜 (WEEKLY: 1-127 비트마스크, MONTHLY: 1-31). DAILY는 null. **이미 반복에 연결된 투두에는 무시됨** | `5` |
          | routineTime | ❌ 선택 | string | 반복 마감 시각 (HH:mm:ss). **이미 반복에 연결된 투두에는 무시됨** | `"09:00:00"` |

          ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.

          **반복(routine) 처리 규칙**

          `routineType`/`routineDate`/`routineTime`은 **해당 투두에 아직 연결된 Routine이 없을 때만** 새 Routine을 생성해 연결합니다.
          이미 Routine이 연결되어 있다면 이 필드들은 무시됩니다 — 반복 시리즈 전체를 수정하려면 `PUT /api/routines/{id}`를 사용하세요.

          | 기존 상태 | routineType 값 | 동작 |
          |----------|---------------|------|
          | 반복 없음 | `null` | 변경 없음 |
          | 반복 없음 | 유형 값 | 새 루틴 생성 후 투두에 연결 |
          | 반복 있음 | 어떤 값이든 | **무시됨** — 기존 루틴 유지 (루틴 수정/삭제는 `PUT /api/routines/{id}` 또는 `DELETE /api/routines/{id}` 사용) |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "투두 수정 성공",
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
                                "title": "토익 단어 50개 외우기 (수정)",
                                "dueDate": "2025-12-31T23:59:59",
                                "isCompleted": false,
                                "tagId": 3,
                                "tagTitle": "영어",
                                "tagColor": "BLUE",
                                "routineType": "WEEKLY",
                                "routineDate": 5,
                                "subTodos": []
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "입력값 오류",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "title 빈 문자열",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "INVALID_INPUT",
                              "message": "잘못된 입력입니다.",
                              "detail": null
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "잘못된 반복 날짜",
                      value =
                          """
                          {
                            "status": 400,
                            "success": false,
                            "data": null,
                            "error": {
                              "code": "ROUTINE_INVALID_DATE",
                              "message": "유효하지 않은 반복 날짜입니다.",
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
                })),
    @ApiResponse(
        responseCode = "404",
        description = "투두 또는 태그를 찾을 수 없음",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "투두 없음",
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
                          """),
                  @ExampleObject(
                      name = "태그 없음",
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
                          """)
                }))
  })
  ResponseEntity<ApiResult<TodoDetailResponseDto>> updateTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "수정할 투두 ID", example = "1") @PathVariable Long todoId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples = {
                        @ExampleObject(
                            name = "전체 필드 포함",
                            value =
                                """
                                {"title": "토익 단어 50개 외우기", "dueDate": "2025-12-31T23:59:59", "tagId": 3, "routineType": "WEEKLY", "routineDate": 5}
                                """),
                        @ExampleObject(
                            name = "필수 필드만 (optional 생략)",
                            summary = "optional 필드를 생략하면 null로 처리됨",
                            value =
                                """
                                {"title": "토익 단어 50개 외우기"}
                                """)
                      }))
          @Valid
          @RequestBody
          TodoUpdateRequestDto request);

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
          - `routineDate`: WEEKLY이면 요일 비트마스크(1-127), MONTHLY이면 일자(1-31), DAILY 또는 루틴 없으면 `null`
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
                                "routineDate": null,
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
          | sort | ❌ 선택 | string | `priority` | 정렬 기준 (`priority`, `dueDate`) | `dueDate` |
          | date | ❌ 선택 | string | 오늘 | 기준 날짜 (yyyy-MM-dd 형식). 생략 시 서버 기준 오늘 사용 | `"2026-05-28"` |

          **filter 값별 조회 조건**

          | filter | 조회 대상 |
          |--------|----------|
          | `all` | 완료되지 않은 모든 투두 (마감일 무관, date 무시) |
          | `day` | 기준 날짜에 마감인 미완료 투두 + 기준 날짜에 완료된 투두 |
          | `week` | 기준 날짜부터 7일 이내 마감인 미완료 투두 |
          | `month` | 기준 날짜부터 한 달 이내 마감인 미완료 투두 |

          **sort 값별 정렬 기준**

          | sort | 정렬 |
          |------|------|
          | `priority` | sortOrder ASC |
          | `dueDate` | dueDate ASC (null 마지막) |

          **day 필터의 추가 정렬 규칙**
          - 미완료 투두 먼저, 완료 투두 나중 → 각 그룹 내에서 선택한 sort 기준 적용

          **반환 필드**
          - `routineType`: 루틴에 연결된 투두인 경우 `DAILY` / `WEEKLY` / `MONTHLY`, 일반 투두는 `null`
          - `tagId`, `tagTitle`, `tagColor`: 태그가 없으면 모두 `null`
          - `isOverdue`: 미완료이고 `dueDate`가 현재 시각 이전인 경우 `true`, 그 외 `false`
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
                                  "routineType": "DAILY",
                                  "isOverdue": true
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
                                  "routineType": null,
                                  "isOverdue": false
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
                              "message": "유효하지 않은 정렬 값입니다. (priority, dueDate 중 하나)",
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
      @Parameter(description = "정렬 기준 (priority/dueDate)", example = "priority")
          @RequestParam(defaultValue = "priority")
          String sort,
      @Parameter(description = "기준 날짜 (yyyy-MM-dd). 생략 시 오늘", example = "2026-05-28")
          @RequestParam(required = false)
          LocalDate date);

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

  @Operation(
      summary = "일반 투두 삭제 취소",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          삭제된 일반 투두를 복원합니다. 하위 투두도 함께 복원됩니다.

          - 루틴 투두는 이 API로 복원할 수 없습니다. 루틴 투두는 `PATCH /api/routines/{routineId}/overrides/{date}/unskip` 을 사용하세요.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Path Variable**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | todoId | ✅ 필수 | integer | 복원할 투두 ID | `1` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 취소 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "루틴 투두에 호출한 경우",
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
                                "code": "ROUTINE_TODO_USE_ROUTINE_API",
                                "message": "반복 todo는 루틴 API를 통해 수정해주세요.",
                                "detail": null
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "404",
        description = "삭제된 투두를 찾을 수 없음",
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
                }))
  })
  ResponseEntity<ApiResult<Void>> restoreTodo(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "복원할 투두 ID", example = "1") @PathVariable Long todoId);
}

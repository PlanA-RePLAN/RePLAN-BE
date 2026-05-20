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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import plana.replan.domain.todo.dto.SubTodoCreateRequestDto;
import plana.replan.domain.todo.dto.SubTodoUpdateRequestDto;
import plana.replan.domain.todo.dto.TodoCreateRequestDto;
import plana.replan.domain.todo.dto.TodoResponseDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Todo", description = "투두 관련 API")
public interface TodoControllerDocs {

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

package plana.replan.domain.tag.controller;

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
import plana.replan.domain.tag.dto.TagCreateRequestDto;
import plana.replan.domain.tag.dto.TagResponseDto;
import plana.replan.domain.tag.dto.TagUpdateRequestDto;
import plana.replan.global.common.ApiResult;

@Tag(name = "Tag", description = "태그 관련 API")
public interface TagControllerDocs {

  @Operation(
      summary = "태그 생성",
      description =
          """
          **호출 주체**: AccessToken을 보유한 인증 사용자

          **요청 방법**: `Authorization: Bearer {accessToken}` 헤더 필수

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |
          | Content-Type | ✅ 필수 | string | `application/json` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ✅ 필수 | string | 태그 이름 | `"영어"` |
          | color | ❌ 선택 | string | 태그 색상 (RED/ORANGE/YELLOW/GREEN/BLUE/PURPLE/PINK/GRAY) | `"BLUE"` |

          ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "태그 생성 성공 — HTTP 상태는 201이며, 응답 본문의 status 필드는 ApiResult 공통 성공 규약에 따라 200으로 고정됩니다.",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "색상 포함",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "tagId": 1,
                              "title": "영어",
                              "color": "BLUE"
                            },
                            "error": null
                          }
                          """),
                  @ExampleObject(
                      name = "색상 없음",
                      value =
                          """
                          {
                            "status": 200,
                            "success": true,
                            "data": {
                              "tagId": 2,
                              "title": "독서",
                              "color": null
                            },
                            "error": null
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "title 누락 또는 빈 문자열",
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
                                "detail": "title: 태그 이름은 필수입니다."
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "색상 포함",
                    value =
                        """
                        {"title": "영어", "color": "BLUE"}
                        """),
                @ExampleObject(
                    name = "색상 생략",
                    summary = "color 생략 시 null로 처리",
                    value = """
                        {"title": "독서"}
                        """)
              }))
  ResponseEntity<ApiResult<TagResponseDto>> createTag(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody TagCreateRequestDto request);

  @Operation(
      summary = "태그 수정",
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
          | tagId | ✅ 필수 | integer | 수정할 태그 ID | `1` |

          **Request Body**

          | 필드명 | 필수 여부 | 타입 | 설명 | 예시 |
          |--------|-----------|------|------|------|
          | title | ❌ 선택 | string | 태그 이름. null이면 변경하지 않음. 빈 문자열은 허용하지 않음 | `"업무"` |
          | color | ❌ 선택 | string | 태그 색상. null이면 색상 제거 | `"RED"` |

          ❌ 선택 필드는 생략하거나 null로 전달해도 동일하게 처리됩니다.
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "태그 수정 성공",
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
                                "tagId": 1,
                                "title": "업무",
                                "color": "RED"
                              },
                              "error": null
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "title이 빈 문자열",
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
        description = "태그를 찾을 수 없음 (존재하지 않거나 본인 소유가 아닌 경우)",
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = "application/json",
              examples = {
                @ExampleObject(
                    name = "전체 필드 포함",
                    value =
                        """
                        {"title": "업무", "color": "RED"}
                        """),
                @ExampleObject(
                    name = "title만 수정",
                    summary = "color를 생략하면 색상이 제거됨",
                    value = """
                        {"title": "업무"}
                        """)
              }))
  ResponseEntity<ApiResult<TagResponseDto>> updateTag(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "수정할 태그 ID", example = "1") @PathVariable Long tagId,
      @Valid @RequestBody TagUpdateRequestDto request);

  @Operation(
      summary = "태그 삭제",
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
          | tagId | ✅ 필수 | integer | 삭제할 태그 ID | `1` |
          """,
      security = @SecurityRequirement(name = "Bearer Authentication"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "태그 삭제 성공"),
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
        description = "태그를 찾을 수 없음 (존재하지 않거나 본인 소유가 아닌 경우)",
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
  ResponseEntity<Void> deleteTag(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "삭제할 태그 ID", example = "1") @PathVariable Long tagId);
}

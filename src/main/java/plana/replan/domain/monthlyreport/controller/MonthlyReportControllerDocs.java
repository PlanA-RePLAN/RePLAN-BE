package plana.replan.domain.monthlyreport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import plana.replan.domain.monthlyreport.dto.MonthlyReportResponse;
import plana.replan.global.common.ApiResult;

@Tag(name = "MonthlyReport", description = "월간 통계 리포트 API")
public interface MonthlyReportControllerDocs {

  @Operation(
      summary = "월간 통계 조회",
      description =
          """
          지정한 연월의 투두 달성 통계 리포트를 조회합니다.
          리포트는 매월 1일 00:00에 배치 작업으로 자동 생성됩니다.

          **Request Headers**

          | 헤더명 | 필수 여부 | 타입 | 설명 |
          |--------|-----------|------|------|
          | Authorization | ✅ 필수 | string | `Bearer {accessToken}` 형식의 JWT 액세스 토큰 |

          **Query Parameters**

          | 파라미터명 | 필수 여부 | 타입 | 설명 | 예시 |
          |-----------|-----------|------|------|------|
          | year | ✅ 필수 | integer | 조회 연도 | `2025` |
          | month | ✅ 필수 | integer | 조회 월 (1~12) | `5` |

          **Response Elements**

          | 필드명 | 타입 | 설명 |
          |--------|------|------|
          | year | integer | 연도 |
          | month | integer | 월 |
          | totalTodos | integer | 해당 월 전체 투두 수 |
          | completedTodos | integer | 달성 투두 수 |
          | achievementRate | number | 달성률 (%) |
          | prevMonthDiff | number | 전월 대비 달성률 차이 (%p). 전월 데이터 없으면 null |
          | replanCount | integer | 리플랜 횟수 |
          | replanAchievementEffect | number | 리플랜 달성 효과 (%). 리플랜 없으면 null |
          | analysisData | object | 분석 데이터. 투두 활동 없으면 null |
          | aiInsight | object | AI 인사이트. 투두 활동 없으면 null |
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content =
            @Content(
                mediaType = "application/json",
                examples =
                    @ExampleObject(
                        value =
                            """
                            {
                              "code": 200,
                              "data": {
                                "year": 2025,
                                "month": 5,
                                "totalTodos": 30,
                                "completedTodos": 21,
                                "achievementRate": 70.00,
                                "prevMonthDiff": 5.50,
                                "replanCount": 3,
                                "replanAchievementEffect": 66.67,
                                "analysisData": {
                                  "topFailureReason": "심리적 저항",
                                  "failureDistribution": [
                                    {"reason": "심리적 저항", "count": 5, "rate": 55.56},
                                    {"reason": "컨디션 난조", "count": 4, "rate": 44.44}
                                  ],
                                  "bestAchievementTag": {"title": "운동", "color": "#FF5733", "rate": 85.0},
                                  "worstAchievementTag": {"title": "공부", "color": "#3399FF", "rate": 50.0},
                                  "bestAchievementDay": {"day": "화요일", "rate": 90.0},
                                  "worstAchievementDay": {"day": "월요일", "rate": 40.0},
                                  "patternCombinations": [
                                    {"reason": "심리적 저항", "tag": "공부", "day": null, "count": 3}
                                  ]
                                },
                                "aiInsight": {
                                  "insights": [
                                    {"summary": "화요일 집중력이 가장 높습니다", "detail": "화요일 달성률이 90%로 가장 높았습니다."}
                                  ],
                                  "writingTip": "월요일에는 가벼운 투두 위주로 구성하면 달성률이 높아집니다."
                                }
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                mediaType = "application/json",
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          """
                          {"code":401,"message":"EMPTY_TOKEN","data":null}
                          """),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          """
                          {"code":401,"message":"EXPIRED_TOKEN","data":null}
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "리포트 없음",
        content =
            @Content(
                mediaType = "application/json",
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"code":404,"message":"해당 월의 통계 리포트가 없습니다.","data":null}
                            """)))
  })
  ResponseEntity<ApiResult<MonthlyReportResponse>> getReport(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회 연도", example = "2025") @RequestParam int year,
      @Parameter(description = "조회 월 (1~12)", example = "5") @RequestParam int month);
}

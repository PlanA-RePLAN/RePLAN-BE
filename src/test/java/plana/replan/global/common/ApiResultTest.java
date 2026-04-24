package plana.replan.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import plana.replan.global.exception.ErrorDetail;
import plana.replan.global.exception.GlobalErrorCode;

/**
 * ApiResult가 성공/실패 케이스에서 올바른 구조를 만드는지 검사한다.
 *
 * <p>실제 서버나 DB 없이 순수 Java 코드만으로 동작하는 가장 단순한 형태의 테스트다.
 */
class ApiResultTest {

  @Test
  @DisplayName("데이터가 있는 성공 응답은 success=true, data 있음, error 없음")
  void ok_withData() {
    // given: "hello"라는 데이터를 담은 성공 응답 생성
    ApiResult<String> result = ApiResult.ok("hello");

    // then: 구조가 기대한 대로인지 검사
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isEqualTo("hello");
    assertThat(result.getError()).isNull(); // 성공이니까 error는 없어야 함
  }

  @Test
  @DisplayName("데이터 없는 성공 응답은 success=true, data=null, error 없음")
  void ok_noData() {
    // given: 회원가입/로그아웃처럼 반환 데이터가 없는 성공 응답
    ApiResult<Void> result = ApiResult.ok();

    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNull();
    assertThat(result.getError()).isNull();
  }

  @Test
  @DisplayName("에러 응답은 success=false, error 있음, data 없음")
  void error() {
    // given: 401 에러 응답 생성
    ErrorDetail errorDetail = ErrorDetail.of(GlobalErrorCode.UNAUTHORIZED);
    ApiResult<Void> result = ApiResult.error(401, errorDetail);

    assertThat(result.getStatus()).isEqualTo(401);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getData()).isNull(); // 에러니까 data는 없어야 함
    assertThat(result.getError()).isNotNull();
    assertThat(result.getError().getCode()).isEqualTo("UNAUTHORIZED");
  }
}

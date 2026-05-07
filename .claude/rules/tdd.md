# TDD 규칙

## 작업 순서

1. **테스트 코드 먼저 작성** → `./gradlew build` → 테스트 실패 확인
2. **구현 코드 작성** → `./gradlew build` → 테스트 통과 확인

구현 코드 없이 테스트가 통과되면 테스트가 잘못 작성된 것이다.

## 테스트 메서드명

**한글로 작성한다.** JUnit 5 + Java는 유니코드 식별자를 지원하므로 문제없다.

```java
@Test
void 목표_생성_성공() { ... }

@Test
void 목표_삭제_타인_목표_403() { ... }

@Test
void 목표_조회_첫_페이지_커서없음() { ... }

@Test
void 인증_없이_요청_시_401() { ... }
```

## 기존 테스트 패턴 참고

프로젝트 내 기존 테스트 파일:
- Controller 테스트: `@WebMvcTest` + `@MockitoBean` 사용
  - `src/test/.../auth/controller/AuthControllerTest.java`
  - `src/test/.../user/controller/UserControllerTest.java`
- Service 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock` 사용
  - `src/test/.../auth/service/AuthServiceGoogleLoginTest.java`

## 테스트 파일 위치

구현 파일과 동일한 패키지 구조로 `src/test/java/` 하위에 작성.
- 구현: `src/main/java/plana/replan/domain/goal/service/GoalService.java`
- 테스트: `src/test/java/plana/replan/domain/goal/service/GoalServiceTest.java`

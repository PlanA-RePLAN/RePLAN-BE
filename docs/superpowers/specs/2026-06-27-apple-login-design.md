# 애플 로그인(Sign in with Apple) + 탈퇴 시 토큰 철회 설계

작성일: 2026-06-27

## 배경

iOS 앱(PWA를 Capacitor로 감싼 형태)에서 애플 로그인을 지원해야 한다.
애플은 "소셜 로그인을 제공하는 앱은 애플 로그인도 제공해야 한다"는 앱스토어 심사 정책이 있어 사실상 필수다.
또한 애플은 "애플 로그인을 쓰는 앱은 계정 삭제 시 애플 연동도 해제(revoke)"하도록 요구한다.

현재 백엔드에는 Google / Kakao / Naver 로그인이 이미 있고, `Provider` enum과
"기존 유저면 토큰 발급 / 신규면 임시토큰(tempToken) 발급 후 register" 흐름을 공유한다.
회원 탈퇴(`UserService.deleteAccount`)도 이미 존재한다.

## 프론트 환경 (검증 설정에 영향)

- 프론트는 **PWA + Capacitor 래퍼**. iOS에서는 Capacitor 네이티브 애플 로그인 플러그인
  (`@capacitor-community/apple-sign-in` 등)으로 **네이티브 ASAuthorization 흐름**을 사용한다.
- 따라서 서버가 검증할 `identityToken`의 `aud`는 **앱 번들 ID**다. (웹 Services ID 아님)
- 이 플러그인은 로그인 시 `identityToken`(JWT)과 `authorizationCode`를 모두 반환한다.

## 결정 사항 (브레인스토밍에서 확정)

1. **무엇**: 애플 로그인 추가 + 탈퇴 시 애플 토큰 철회(revoke).
2. **로그인 검증**: 앱이 보낸 `identityToken`(JWT)을 서버가 애플 공개키로 검증.
3. **revoke용 토큰 확보 시점**: 로그인 때 `authorizationCode`도 함께 받아 refresh token으로 교환해 **미리 저장**.
   (프론트가 탈퇴 시 추가 작업을 하지 않아도 되도록.)
4. **저장 위치**: **Redis**. `users` 테이블에 애플 전용 컬럼을 추가하지 않는다(응집도 + 민감정보 격리).
5. **revoke 실패 시**: 우리 서비스의 탈퇴(익명화·soft delete)는 **그대로 진행**. 실패는 로그만 남긴다.

## 데이터/모델 변경

- `Provider` enum에 `APPLE` 추가. (enum 값 추가라 별도 DB 마이그레이션 불필요 —
  `login_type`은 `EnumType.STRING`이라 새 문자열만 들어감.)
- `users` 테이블 변경 없음.

## 설정 (application.yml + Config)

애플 검증/토큰교환/철회에 필요한 값은 프로퍼티로 분리하고 실제 값은 운영 환경변수/SSM으로 주입한다.

| 프로퍼티 | 용도 |
|----------|------|
| `apple.client-id` | identityToken `aud` 검증값 = 앱 번들 ID. 토큰 교환/철회의 `client_id`로도 사용 |
| `apple.team-id` | client_secret(JWT) 생성용 애플 팀 ID |
| `apple.key-id` | client_secret(JWT) 헤더 `kid` |
| `apple.private-key` | client_secret 서명용 `.p8` 비공개키 내용(ES256) |

`AppleOAuthConfig`에서 위 값을 읽어 애플 검증/토큰 클라이언트 빈을 구성한다.
기존 `OAuthConfig.restClient()`를 재사용한다.

## 로그인 흐름 — `POST /api/auth/login/apple`

요청 바디: `AppleLoginRequestDto { identityToken, authorizationCode }`

1. **identityToken 검증** — 애플 JWKS(`https://appleid.apple.com/auth/keys`)로 RS256 서명 검증 +
   `iss == https://appleid.apple.com`, `aud == apple.client-id`, `exp` 만료 확인 → `email`, `sub` 추출.
2. **authorizationCode 교환** — `apple.*` 설정으로 ES256 client_secret(JWT) 생성 후
   `POST https://appleid.apple.com/auth/token` (`grant_type=authorization_code`) 호출 → `refresh_token` 확보.
3. **분기** (기존 OAuth 로그인과 동일 패턴):
   - 같은 이메일이 다른 provider로 이미 있으면 → 409 충돌(`CustomException`).
   - 기존 `APPLE` 유저면 → refresh token을 `apple:refresh:{userId}`에 저장(갱신) + 토큰쌍 발급 →
     `OAuthLoginResponseDto.existingUser(...)`.
   - 신규면 → refresh token을 `apple:refresh:{email}`에 tempToken과 동일 TTL로 임시 저장 +
     tempToken 발급 → `OAuthLoginResponseDto.newUser(...)`.

## 회원가입(register) 보완

신규 애플 유저가 `register(OAuthRegisterRequestDto, tempToken)`로 가입을 완료할 때:

- provider가 `APPLE`이면 `apple:refresh:{email}`에 임시 저장된 refresh token을 읽어
  `apple:refresh:{userId}`(만료 없음)로 옮기고 임시키를 삭제한다.

## 탈퇴 흐름 보완 — `UserService.deleteAccount`

기존 익명화/soft delete **전에** 다음을 추가한다.

- `user.getProvider() == APPLE`이고 `apple:refresh:{userId}` 값이 있으면:
  - client_secret(JWT) 생성 후 `POST https://appleid.apple.com/auth/revoke`
    (`token=refresh_token`, `token_type_hint=refresh_token`) 호출.
  - 성공/실패와 무관하게 `apple:refresh:{userId}` 삭제.
  - 호출 실패 시 예외를 삼키고 로그만 남긴다(탈퇴는 계속 진행).
- 이후 기존 로직(데이터 soft delete → `withdraw()` → 우리 refresh token 무효화) 그대로.

## Swagger 문서

- `AuthControllerDocs`에 애플 로그인 엔드포인트 문서 추가. 발생 가능한 모든 에러 응답을
  `@ApiResponse` + `@ExampleObject`로 포함(잘못된 토큰, provider 충돌 등).
- `AuthController`는 `AuthControllerDocs` 구현 + HTTP 매핑만. (파일 분리 규칙 준수)
- 탈퇴 API 문서는 동작이 그대로라 변경 없음(내부에 revoke가 추가될 뿐).

## 테스트

기존 OAuth 테스트와 동일 패턴으로:

- `AuthServiceAppleLoginTest` — 신규/기존/타 provider 충돌/잘못된 토큰 케이스. 애플 JWKS 검증과
  토큰 교환은 외부 호출이므로 검증기/토큰 클라이언트를 모킹.
- `register` 보완: 애플 신규 유저 가입 시 refresh token이 userId 키로 옮겨지는지.
- `deleteAccount` 보완: APPLE 유저 탈퇴 시 revoke 호출 + 키 삭제, revoke 실패해도 탈퇴 완료되는지.

## 구성 단위 (책임 분리)

- `AppleTokenVerifier` — identityToken 검증 전담(JWKS 캐시 + 서명/클레임 검증).
- `AppleAuthClient` — client_secret 생성 + 애플 token/revoke 엔드포인트 호출 전담.
- `AuthService.appleLogin(...)` — 위 둘을 조합한 로그인 비즈니스 로직.
- Redis 키 헬퍼 — `apple:refresh:{email|userId}` 키 규칙 한 곳에서 관리.

## 미해결/외부 의존 (구현 전 확보 필요)

- 애플 개발자 콘솔에서 발급한 실제 값: 번들 ID, 팀 ID, 키 ID, `.p8` 비공개키. (운영 주입)
- JWKS/JWT 검증 라이브러리 선택은 구현 계획 단계에서 build.gradle 현황 확인 후 결정.

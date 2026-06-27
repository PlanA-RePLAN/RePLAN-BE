# 애플 로그인 + 탈퇴 시 토큰 철회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 OAuth(Google/Kakao/Naver)와 동일한 흐름으로 애플 로그인을 추가하고, 회원 탈퇴 시 애플 연동을 해제(revoke)한다.

**Architecture:** 프론트가 보낸 애플 `identityToken`(JWT)을 서버가 애플 공개키(JWKS)로 검증해 이메일을 얻고, 함께 받은 `authorizationCode`를 애플 토큰 엔드포인트에서 refresh token으로 교환해 Redis에 보관한다. 탈퇴 시 그 refresh token으로 애플 revoke를 호출한다. 웹/네이티브 양쪽을 한 코드로 지원하기 위해 허용 `aud`를 리스트로 검증하고, 교환·철회 시 client_id는 검증된 토큰의 `aud`를 그대로 사용한다.

**Tech Stack:** Spring Boot, jjwt 0.13.0(이미 존재 — JWKS 검증 + ES256 client_secret 서명 둘 다 처리), Spring `RestClient`, Redis(StringRedisTemplate).

## Global Constraints

- 새 라이브러리 추가 금지. `io.jsonwebtoken:jjwt 0.13.0`만 사용한다.
- 엔드포인트 경로 컨벤션: `POST /api/auth/oauth/apple` (기존 `/oauth/google` 등과 동일).
- `Provider`는 `EnumType.STRING` — enum 값만 추가하면 되고 DB 마이그레이션 불필요.
- `users` 테이블 변경 금지. 애플 refresh token은 Redis에만 저장.
- revoke 실패해도 탈퇴(익명화·soft delete)는 계속 진행. 실패는 로그만 남긴다.
- Swagger는 기존 `AuthController`의 **인라인 어노테이션 패턴**을 따른다(Docs 인터페이스 분리 안 함 — 주변 코드 일관성).
- 빌드/포맷 검사는 JDK 17로: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`.
- Redis 키 규칙:
  - 신규 유저 임시: `apple-refresh-temp:{email}` (TTL 300초, tempToken과 동일)
  - 확정: `apple:refresh:{userId}` (만료 없음)
  - 저장 값 형식: `{clientId}|{refreshToken}` (revoke 시 동일 client_id 필요하므로 함께 보관)

---

### Task 1: Provider enum + 에러 코드 추가

**Files:**
- Modify: `src/main/java/plana/replan/domain/user/entity/Provider.java`
- Modify: `src/main/java/plana/replan/domain/user/exception/UserErrorCode.java:14-18`

**Interfaces:**
- Produces: `Provider.APPLE`, `UserErrorCode.APPLE_TOKEN_INVALID`

- [ ] **Step 1: Provider에 APPLE 추가**

```java
public enum Provider {
  LOCAL,
  KAKAO,
  GOOGLE,
  NAVER,
  APPLE
}
```

- [ ] **Step 2: 에러 코드 추가**

`UserErrorCode.java`의 `KAKAO_TOKEN_INVALID(...)` 줄 아래에 추가:

```java
  APPLE_TOKEN_INVALID(401, "Apple ID Token 검증에 실패했습니다."),
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/plana/replan/domain/user/entity/Provider.java src/main/java/plana/replan/domain/user/exception/UserErrorCode.java
git commit -m "Feat: 애플 로그인용 Provider.APPLE와 에러 코드 추가"
```

---

### Task 2: 애플 설정 프로퍼티 + EC 키 로딩(AppleProperties, AppleClientSecretGenerator)

**Files:**
- Create: `src/main/java/plana/replan/global/config/AppleProperties.java`
- Create: `src/main/java/plana/replan/domain/auth/apple/AppleClientSecretGenerator.java`
- Test: `src/test/java/plana/replan/domain/auth/apple/AppleClientSecretGeneratorTest.java`
- Modify: `src/main/resources/application.yml` (apple 프로퍼티 추가)

**Interfaces:**
- Produces:
  - `AppleProperties` — `List<String> getClientIds()`, `String getTeamId()`, `String getKeyId()`, `String getPrivateKey()`
  - `AppleClientSecretGenerator.generate(String clientId)` → `String` (ES256 서명된 client_secret JWT)

- [ ] **Step 1: AppleProperties 작성**

```java
package plana.replan.global.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apple")
public class AppleProperties {
  private List<String> clientIds = List.of();
  private String teamId;
  private String keyId;
  private String privateKey;
}
```

- [ ] **Step 2: application.yml에 프로퍼티 추가**

`google:` 블록 옆에 추가(실제 값은 환경변수로 주입, 기본은 빈 값):

```yaml
apple:
  client-ids: ${APPLE_CLIENT_IDS:}
  team-id: ${APPLE_TEAM_ID:}
  key-id: ${APPLE_KEY_ID:}
  private-key: ${APPLE_PRIVATE_KEY:}
```

- [ ] **Step 3: client_secret 생성기 테스트 작성(실패)**

`.p8` 실제 키 대신 테스트에서 임시 EC 키쌍을 만들어 검증한다.

```java
package plana.replan.domain.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import plana.replan.global.config.AppleProperties;

class AppleClientSecretGeneratorTest {

  private AppleProperties propsWithKey(ECPrivateKey privateKey) {
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n-----END PRIVATE KEY-----";
    AppleProperties props = new AppleProperties();
    props.setTeamId("TEAM123456");
    props.setKeyId("KEY1234567");
    props.setPrivateKey(pem);
    return props;
  }

  @Test
  void generate_ES256_client_secret_헤더와_클레임이_올바르다() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
    gen.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = gen.generateKeyPair();

    AppleProperties props = propsWithKey((ECPrivateKey) pair.getPrivate());
    AppleClientSecretGenerator generator = new AppleClientSecretGenerator(props);

    String secret = generator.generate("com.replan.service");

    Jws<Claims> jws =
        Jwts.parser().verifyWith(pair.getPublic()).build().parseSignedClaims(secret);
    Claims claims = jws.getPayload();
    assertThat(jws.getHeader().getKeyId()).isEqualTo("KEY1234567");
    assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
    assertThat(claims.getSubject()).isEqualTo("com.replan.service");
    assertThat(claims.getAudience()).contains("https://appleid.apple.com");
  }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AppleClientSecretGeneratorTest"`
Expected: FAIL (AppleClientSecretGenerator 없음 — 컴파일 에러)

- [ ] **Step 5: AppleClientSecretGenerator 구현**

```java
package plana.replan.domain.auth.apple;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.AppleProperties;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

  private static final String APPLE_AUD = "https://appleid.apple.com";

  private final AppleProperties properties;

  public String generate(String clientId) {
    PrivateKey privateKey = loadPrivateKey(properties.getPrivateKey());
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(properties.getKeyId())
        .and()
        .issuer(properties.getTeamId())
        .subject(clientId)
        .audience()
        .add(APPLE_AUD)
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
        .signWith(privateKey, Jwts.SIG.ES256)
        .compact();
  }

  private PrivateKey loadPrivateKey(String p8) {
    try {
      String cleaned =
          p8.replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(cleaned);
      return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AppleClientSecretGeneratorTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/plana/replan/global/config/AppleProperties.java src/main/java/plana/replan/domain/auth/apple/AppleClientSecretGenerator.java src/test/java/plana/replan/domain/auth/apple/AppleClientSecretGeneratorTest.java src/main/resources/application.yml
git commit -m "Feat: 애플 client_secret(JWT) 생성기와 설정 프로퍼티 추가"
```

---

### Task 3: identityToken 검증기(AppleTokenVerifier)

**Files:**
- Create: `src/main/java/plana/replan/domain/auth/apple/AppleIdTokenPayload.java`
- Create: `src/main/java/plana/replan/domain/auth/apple/AppleTokenVerifier.java`

**Interfaces:**
- Consumes: `AppleProperties`, `RestClient`(기존 `OAuthConfig.restClient()` 빈)
- Produces:
  - `record AppleIdTokenPayload(String email, String aud)`
  - `AppleTokenVerifier.verify(String identityToken)` → `AppleIdTokenPayload` (검증 실패 시 `CustomException(APPLE_TOKEN_INVALID)`, 애플 통신 실패 시 `OAUTH_SERVER_UNAVAILABLE`)

> 검증기는 외부(JWKS) 통신을 하므로 단위 테스트 대신 `AuthService` 테스트에서 mock으로 대체한다. 따라서 이 Task에는 별도 테스트 없이 컴파일 확인만 한다.

- [ ] **Step 1: AppleIdTokenPayload 작성**

```java
package plana.replan.domain.auth.apple;

public record AppleIdTokenPayload(String email, String aud) {}
```

- [ ] **Step 2: AppleTokenVerifier 구현**

```java
package plana.replan.domain.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import java.security.Key;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.AppleProperties;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class AppleTokenVerifier {

  private static final String APPLE_ISS = "https://appleid.apple.com";
  private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";

  private final RestClient restClient;
  private final AppleProperties properties;

  public AppleIdTokenPayload verify(String identityToken) {
    try {
      String jwksJson = restClient.get().uri(JWKS_URL).retrieve().body(String.class);
      var jwkSet = Jwks.setParser().build().parse(jwksJson);

      LocatorAdapter<Key> keyLocator =
          new LocatorAdapter<>() {
            @Override
            protected Key locate(ProtectedHeader header) {
              String kid = header.getKeyId();
              return jwkSet.getKeys().stream()
                  .filter(jwk -> kid != null && kid.equals(jwk.getId()))
                  .findFirst()
                  .map(jwk -> (Key) ((PublicJwk<?>) jwk).toKey())
                  .orElseThrow(() -> new CustomException(UserErrorCode.APPLE_TOKEN_INVALID));
            }
          };

      Jws<Claims> jws =
          Jwts.parser()
              .keyLocator(keyLocator)
              .requireIssuer(APPLE_ISS)
              .build()
              .parseSignedClaims(identityToken);

      Claims claims = jws.getPayload();
      Set<String> audiences = claims.getAudience();
      String matchedAud =
          properties.getClientIds().stream()
              .filter(id -> audiences != null && audiences.contains(id))
              .findFirst()
              .orElseThrow(() -> new CustomException(UserErrorCode.APPLE_TOKEN_INVALID));

      String email = claims.get("email", String.class);
      if (email == null) {
        throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
      }
      return new AppleIdTokenPayload(email, matchedAud);
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/apple/AppleIdTokenPayload.java src/main/java/plana/replan/domain/auth/apple/AppleTokenVerifier.java
git commit -m "Feat: 애플 identityToken 검증기 추가 (JWKS 서명+aud 검증)"
```

---

### Task 4: 애플 토큰 클라이언트(AppleAuthClient — 교환/철회)

**Files:**
- Create: `src/main/java/plana/replan/domain/auth/apple/AppleAuthClient.java`

**Interfaces:**
- Consumes: `RestClient`, `AppleClientSecretGenerator`
- Produces:
  - `AppleAuthClient.exchangeRefreshToken(String clientId, String authorizationCode)` → `String` refreshToken
  - `AppleAuthClient.revoke(String clientId, String refreshToken)` → `void` (실패 시 예외를 던짐; 호출자가 처리)

> 외부 통신 컴포넌트라 단위 테스트는 생략하고, 사용처(AuthService/UserService) 테스트에서 mock으로 검증한다.

- [ ] **Step 1: AppleAuthClient 구현**

```java
package plana.replan.domain.auth.apple;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class AppleAuthClient {

  private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
  private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

  private final RestClient restClient;
  private final AppleClientSecretGenerator clientSecretGenerator;

  @SuppressWarnings("unchecked")
  public String exchangeRefreshToken(String clientId, String authorizationCode) {
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("client_id", clientId);
      form.add("client_secret", clientSecretGenerator.generate(clientId));
      form.add("grant_type", "authorization_code");
      form.add("code", authorizationCode);

      Map<String, Object> body =
          restClient
              .post()
              .uri(TOKEN_URL)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);

      if (body == null || body.get("refresh_token") == null) {
        throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
      }
      return (String) body.get("refresh_token");
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }

  public void revoke(String clientId, String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecretGenerator.generate(clientId));
    form.add("token", refreshToken);
    form.add("token_type_hint", "refresh_token");

    restClient
        .post()
        .uri(REVOKE_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/apple/AppleAuthClient.java
git commit -m "Feat: 애플 토큰 교환/철회 클라이언트 추가"
```

---

### Task 5: 요청 DTO(AppleLoginRequestDto)

**Files:**
- Create: `src/main/java/plana/replan/domain/auth/dto/AppleLoginRequestDto.java`

**Interfaces:**
- Produces: `AppleLoginRequestDto` — `getIdentityToken()`, `getAuthorizationCode()`

> 기존 `GoogleLoginRequestDto`와 동일한 형태(클래스 + `@Getter` + `@Schema` + `@NotBlank`)로 작성. 실제 기존 DTO를 열어 어노테이션/롬복 사용을 그대로 맞출 것.

- [ ] **Step 1: AppleLoginRequestDto 작성**

```java
package plana.replan.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "애플 로그인 요청")
public class AppleLoginRequestDto {

  @Schema(
      description = "애플이 발급한 identity token(JWT)",
      example = "eyJraWQiOiJ...",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String identityToken;

  @Schema(
      description = "애플이 발급한 authorization code (refresh token 교환 및 탈퇴 시 철회용)",
      example = "c1a2b3...",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String authorizationCode;
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/dto/AppleLoginRequestDto.java
git commit -m "Feat: 애플 로그인 요청 DTO 추가"
```

---

### Task 6: AuthService.appleLogin

> **보안 보강(실제 구현 반영, 코드리뷰 후 추가):** appleLogin은 ① 검증된 identityToken의 `sub`와
> authorizationCode 교환 응답 id_token의 `sub`가 일치하는지 확인하고(불일치/없음 → `APPLE_TOKEN_INVALID`,
> 거부 시 발급된 애플 refresh token은 best-effort revoke), ② provider 충돌 검사를 애플 네트워크 호출
> **전에** 수행한다. identityToken의 `email_verified`(true) 검사는 AppleTokenVerifier(Task 3)에서 한다.

**Files:**
- Modify: `src/main/java/plana/replan/domain/auth/service/AuthService.java` (필드 추가 + appleLogin 메서드 추가)
- Test: `src/test/java/plana/replan/domain/auth/service/AuthServiceAppleLoginTest.java`

**Interfaces:**
- Consumes: `AppleTokenVerifier.verify(...)`, `AppleAuthClient.exchangeRefreshToken(...)`, `AppleIdTokenPayload`
- Produces: `AuthService.appleLogin(AppleLoginRequestDto)` → `OAuthLoginResponseDto`

- [ ] **Step 1: 실패 테스트 작성**

```java
package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import plana.replan.domain.auth.apple.AppleAuthClient;
import plana.replan.domain.auth.apple.AppleIdTokenPayload;
import plana.replan.domain.auth.apple.AppleTokenVerifier;
import plana.replan.domain.auth.dto.AppleLoginRequestDto;
import plana.replan.domain.auth.dto.OAuthLoginResponseDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceAppleLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private AppleTokenVerifier appleTokenVerifier;
  @Mock private AppleAuthClient appleAuthClient;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private AuthService authService;

  private static final String EMAIL = "apple-user@privaterelay.appleid.com";
  private static final String AUD = "com.replan.service";

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
    given(appleTokenVerifier.verify(anyString()))
        .willReturn(new AppleIdTokenPayload(EMAIL, AUD));
    given(appleAuthClient.exchangeRefreshToken(eq(AUD), anyString()))
        .willReturn("apple-refresh-token");
  }

  private AppleLoginRequestDto request() {
    return new AppleLoginRequestDto("id-token", "auth-code");
  }

  @Test
  @DisplayName("기존 애플 유저면 토큰쌍을 발급하고 refresh token을 userId 키에 저장한다")
  void existingUser() {
    User user =
        User.builder()
            .email(EMAIL)
            .nickname("apple")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
    given(userRepository.findByEmailAndProvider(EMAIL, Provider.APPLE))
        .willReturn(Optional.of(user));

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getAccessToken()).isEqualTo("access-token");
    verify(valueOperations)
        .set(eq("apple:refresh:" + user.getId()), eq(AUD + "|apple-refresh-token"));
  }

  @Test
  @DisplayName("신규 유저면 tempToken을 발급하고 refresh token을 email 임시 키에 저장한다")
  void newUser() {
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider(EMAIL, Provider.APPLE))
        .willReturn(Optional.empty());

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getTempToken()).isNotBlank();
    verify(valueOperations)
        .set(
            eq("apple-refresh-temp:" + EMAIL),
            eq(AUD + "|apple-refresh-token"),
            org.mockito.ArgumentMatchers.eq(300L),
            any());
  }

  @Test
  @DisplayName("같은 이메일이 다른 provider로 가입돼 있으면 충돌 예외")
  void providerConflict() {
    User google =
        User.builder()
            .email(EMAIL)
            .nickname("g")
            .role(Role.ROLE_USER)
            .provider(Provider.GOOGLE)
            .build();
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(google));

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.OAUTH_PROVIDER_CONFLICT);
  }
}
```

> 참고: `OAuthLoginResponseDto`의 getter 이름(`getTempToken` 등)과 `CustomException`의 errorCode 접근자는 기존 테스트(`AuthServiceGoogleLoginTest`)를 열어 동일하게 맞출 것. 다르면 그 이름으로 수정.

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthServiceAppleLoginTest"`
Expected: FAIL (appleLogin / 필드 없음 — 컴파일 에러)

- [ ] **Step 3: AuthService에 필드 추가**

기존 필드(`googleIdTokenVerifier` 등) 아래에 추가:

```java
  private final plana.replan.domain.auth.apple.AppleTokenVerifier appleTokenVerifier;
  private final plana.replan.domain.auth.apple.AppleAuthClient appleAuthClient;
```

(import 정리는 spotless가 처리. 가독성을 위해 상단에 import 추가 권장.)

- [ ] **Step 4: appleLogin 메서드 구현**

`kakaoLogin(...)` 아래에 추가:

```java
  @Transactional
  public OAuthLoginResponseDto appleLogin(AppleLoginRequestDto request) {

    // 1. identityToken 검증 → 이메일, aud(=client_id) 추출
    AppleIdTokenPayload payload = appleTokenVerifier.verify(request.getIdentityToken());
    String email = payload.email();
    String clientId = payload.aud();

    // 2. authorizationCode 교환 → refresh token 확보(탈퇴 시 철회용)
    String refreshToken =
        appleAuthClient.exchangeRefreshToken(clientId, request.getAuthorizationCode());
    String storedValue = clientId + "|" + refreshToken;

    // 3. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.APPLE) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 4. 기존유저: JWT 발급 + refresh token을 userId 키에 저장 / 신규유저: tempToken + email 임시 키
    return userRepository
        .findByEmailAndProvider(email, Provider.APPLE)
        .map(
            user -> {
              redisTemplate.opsForValue().set("apple:refresh:" + user.getId(), storedValue);
              LoginResponseDto tokens = issueTokenPair(user);
              return OAuthLoginResponseDto.existingUser(
                  tokens.getAccessToken(), tokens.getRefreshToken());
            })
        .orElseGet(
            () -> {
              redisTemplate
                  .opsForValue()
                  .set("apple-refresh-temp:" + email, storedValue, 300, TimeUnit.SECONDS);
              return OAuthLoginResponseDto.newUser(issueTempToken(email, Provider.APPLE));
            });
  }
```

필요한 import: `plana.replan.domain.auth.apple.AppleIdTokenPayload`, `plana.replan.domain.auth.dto.AppleLoginRequestDto`.

- [ ] **Step 5: 테스트 통과 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthServiceAppleLoginTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/service/AuthService.java src/test/java/plana/replan/domain/auth/service/AuthServiceAppleLoginTest.java
git commit -m "Feat: 애플 로그인 비즈니스 로직 추가"
```

---

### Task 7: register에서 애플 refresh token 이관

**Files:**
- Modify: `src/main/java/plana/replan/domain/auth/service/AuthService.java` (register 메서드 내부)
- Test: `src/test/java/plana/replan/domain/auth/service/AuthServiceRegisterTest.java` (애플 케이스 추가)

**Interfaces:**
- Consumes: Redis `apple-refresh-temp:{email}`
- Produces: Redis `apple:refresh:{userId}` (애플 신규 가입 완료 시)

- [ ] **Step 1: 실패 테스트 추가**

`AuthServiceRegisterTest`에 메서드 추가(기존 setUp/mock 구조 재사용). 핵심 검증: provider가 APPLE인 tempToken으로 register하면 임시 키 값을 userId 키로 옮기고 임시 키를 지운다.

```java
  @Test
  @DisplayName("애플 신규 가입 완료 시 임시 refresh token을 userId 키로 옮기고 임시 키를 삭제한다")
  void register_apple_movesRefreshToken() {
    String tempToken = "temp-uuid";
    String email = "apple-user@privaterelay.appleid.com";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn(email + ":APPLE");
    given(valueOperations.get("apple-refresh-temp:" + email))
        .willReturn("com.replan.service|apple-refresh-token");
    given(userRepository.existsByNickname(anyString())).willReturn(false);
    User saved =
        User.builder()
            .email(email)
            .nickname("nick")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    given(userRepository.save(any(User.class))).willReturn(saved);

    authService.register(new OAuthRegisterRequestDto("nick", null), tempToken);

    verify(valueOperations)
        .set("apple:refresh:" + saved.getId(), "com.replan.service|apple-refresh-token");
    verify(redisTemplate).delete("apple-refresh-temp:" + email);
  }
```

> `OAuthRegisterRequestDto` 생성자 시그니처는 기존 테스트를 보고 맞출 것. `saved.getId()`가 null이면(빌더로 만든 엔티티) 테스트에서 `ReflectionTestUtils.setField(saved, "id", 1L)`로 id를 지정.

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthServiceRegisterTest"`
Expected: FAIL (이관 로직 없음)

- [ ] **Step 3: register에 이관 로직 추가**

`register` 메서드에서 `User user = userRepository.save(...)` 직후, `tempToken 삭제` 전에 추가:

```java
    // 5-1. 애플이면 임시 저장된 refresh token을 userId 키로 옮긴다
    if (provider == Provider.APPLE) {
      String appleRefresh = redisTemplate.opsForValue().get("apple-refresh-temp:" + email);
      if (appleRefresh != null) {
        redisTemplate.opsForValue().set("apple:refresh:" + user.getId(), appleRefresh);
        redisTemplate.delete("apple-refresh-temp:" + email);
      }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthServiceRegisterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/service/AuthService.java src/test/java/plana/replan/domain/auth/service/AuthServiceRegisterTest.java
git commit -m "Feat: 애플 신규 가입 시 refresh token을 userId 키로 이관"
```

---

### Task 8: 컨트롤러 엔드포인트 + Swagger

**Files:**
- Modify: `src/main/java/plana/replan/domain/auth/controller/AuthController.java`
- Test: `src/test/java/plana/replan/domain/auth/controller/AuthControllerTest.java` (애플 엔드포인트 케이스 추가)

**Interfaces:**
- Consumes: `AuthService.appleLogin(AppleLoginRequestDto)`
- Produces: `POST /api/auth/oauth/apple` → `ApiResult<OAuthLoginResponseDto>`

- [ ] **Step 1: 컨트롤러 테스트 추가**

기존 `AuthControllerTest`(google/kakao 케이스)를 참고해 동일 패턴으로 작성. 핵심: 정상 요청 시 200 + service 위임, identityToken 누락 시 400.

```java
  @Test
  @DisplayName("애플 로그인 - 정상 요청이면 200")
  void appleLogin_success() throws Exception {
    given(authService.appleLogin(any()))
        .willReturn(OAuthLoginResponseDto.existingUser("access", "refresh"));

    mockMvc
        .perform(
            post("/api/auth/oauth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"identityToken\":\"id-token\",\"authorizationCode\":\"auth-code\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("애플 로그인 - identityToken 누락 시 400")
  void appleLogin_missingToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/oauth/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"auth-code\"}"))
        .andExpect(status().isBadRequest());
  }
```

> import/setup(`mockMvc`, `@MockBean AuthService` 등)은 기존 `AuthControllerTest` 구조를 그대로 따른다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthControllerTest"`
Expected: FAIL (엔드포인트 없음 → 404/405)

- [ ] **Step 3: 엔드포인트 추가 (인라인 Swagger)**

`kakaoLogin` 매핑(`@PostMapping("/oauth/kakao")`) 아래에 추가. google/kakao의 `@Operation`·`@ApiResponses` 블록을 본떠 작성하되 에러 예시는 `APPLE_TOKEN_INVALID`, `OAUTH_PROVIDER_CONFLICT`, `OAUTH_SERVER_UNAVAILABLE` 포함:

```java
  @Operation(
      summary = "애플 로그인",
      description =
          """
          **호출 주체**: 비인증 사용자

          **비즈니스 로직**
          1. 프론트가 보낸 identityToken(JWT)을 애플 공개키로 검증 → 이메일 추출
          2. authorizationCode를 애플 토큰 엔드포인트에서 refresh token으로 교환(탈퇴 시 철회용)
          3. 같은 이메일이 다른 provider로 가입돼 있으면 409
          4. 기존유저: accessToken/refreshToken 발급 (isNewUser: false)
          5. 신규유저: tempToken(5분) 발급 (isNewUser: true) → 온보딩 후 `/api/auth/oauth/register` 호출 필요
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(
        responseCode = "401",
        description = "애플 토큰 검증 실패",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        name = "APPLE_TOKEN_INVALID",
                        value =
                            "{\"success\":false,\"message\":\"Apple ID Token 검증에 실패했습니다.\"}"))),
    @ApiResponse(
        responseCode = "409",
        description = "다른 방식으로 이미 가입된 이메일",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        name = "OAUTH_PROVIDER_CONFLICT",
                        value =
                            "{\"success\":false,\"message\":\"해당 이메일은 이미 다른 방식으로 가입되어 있습니다.\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "애플 서버 통신 실패",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        name = "OAUTH_SERVER_UNAVAILABLE",
                        value =
                            "{\"success\":false,\"message\":\"OAuth 서버와 통신에 실패했습니다.\"}")))
  })
  @PostMapping("/oauth/apple")
  public ResponseEntity<ApiResult<OAuthLoginResponseDto>> appleLogin(
      @Valid @RequestBody AppleLoginRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(authService.appleLogin(request)));
  }
```

필요한 import: `plana.replan.domain.auth.dto.AppleLoginRequestDto`.

- [ ] **Step 4: 테스트 통과 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*AuthControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/plana/replan/domain/auth/controller/AuthController.java src/test/java/plana/replan/domain/auth/controller/AuthControllerTest.java
git commit -m "Feat: 애플 로그인 엔드포인트(/api/auth/oauth/apple) 추가"
```

---

### Task 9: 탈퇴 시 애플 토큰 철회(UserService.deleteAccount)

**Files:**
- Modify: `src/main/java/plana/replan/domain/user/service/UserService.java`
- Test: `src/test/java/plana/replan/domain/user/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `AppleAuthClient.revoke(clientId, refreshToken)`, Redis `apple:refresh:{userId}`
- Produces: 탈퇴 시 애플 연동 해제 + Redis 키 정리

- [ ] **Step 1: 실패 테스트 추가**

```java
  @Test
  @DisplayName("애플 유저 탈퇴 시 refresh token으로 revoke를 호출하고 키를 삭제한다")
  void deleteAccount_apple_revokes() {
    Long userId = 1L;
    User user =
        User.builder()
            .email("apple-user@privaterelay.appleid.com")
            .nickname("a")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
    given(userRepository.findById(userId)).willReturn(java.util.Optional.of(user));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("apple:refresh:" + userId))
        .willReturn("com.replan.service|apple-refresh-token");

    userService.deleteAccount(userId);

    verify(appleAuthClient).revoke("com.replan.service", "apple-refresh-token");
    verify(redisTemplate).delete("apple:refresh:" + userId);
  }

  @Test
  @DisplayName("revoke가 실패해도 탈퇴(soft delete)는 진행된다")
  void deleteAccount_apple_revokeFails_stillWithdraws() {
    Long userId = 1L;
    User user =
        User.builder()
            .email("apple-user@privaterelay.appleid.com")
            .nickname("a")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
    given(userRepository.findById(userId)).willReturn(java.util.Optional.of(user));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("apple:refresh:" + userId))
        .willReturn("com.replan.service|apple-refresh-token");
    org.mockito.BDDMockito.willThrow(new RuntimeException("apple down"))
        .given(appleAuthClient)
        .revoke(anyString(), anyString());

    userService.deleteAccount(userId);

    // 탈퇴는 진행됨 — 데이터 soft delete가 호출됐는지로 확인
    verify(todoRepository).softDeleteAllByUserId(eq(userId), any());
    verify(redisTemplate).delete("apple:refresh:" + userId);
  }
```

> `UserServiceTest`의 기존 mock 필드(`@Mock` repositories, `valueOperations`, `@InjectMocks userService`)를 재사용하고 `@Mock AppleAuthClient appleAuthClient`를 추가한다. 기존 테스트의 mock 이름/구조에 맞춰 import를 정리할 것.

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*UserServiceTest"`
Expected: FAIL (revoke 호출 없음 / appleAuthClient 필드 없음)

- [ ] **Step 3: UserService에 의존성 + 로거 추가**

클래스에 `@Slf4j` 추가(`import lombok.extern.slf4j.Slf4j;`), 필드 추가:

```java
  private final plana.replan.domain.auth.apple.AppleAuthClient appleAuthClient;
```

- [ ] **Step 4: deleteAccount에 revoke 로직 추가**

`String originalEmail = user.getEmail();` 다음, 데이터 soft delete 전에 추가:

```java
    // 애플 유저면 애플 연동 해제(revoke). 실패해도 탈퇴는 계속 진행한다.
    if (user.getProvider() == Provider.APPLE) {
      String stored = redisTemplate.opsForValue().get("apple:refresh:" + userId);
      if (stored != null) {
        int sep = stored.indexOf('|');
        if (sep > 0) {
          try {
            appleAuthClient.revoke(stored.substring(0, sep), stored.substring(sep + 1));
          } catch (Exception e) {
            log.warn("애플 토큰 철회 실패 - 탈퇴는 계속 진행합니다. userId={}", userId, e);
          }
        }
        redisTemplate.delete("apple:refresh:" + userId);
      }
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests "*UserServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/plana/replan/domain/user/service/UserService.java src/test/java/plana/replan/domain/user/service/UserServiceTest.java
git commit -m "Feat: 애플 유저 탈퇴 시 애플 토큰 철회(revoke) 추가"
```

---

### Task 10: 전체 빌드 + 포맷 검사

**Files:** 없음(검증 전용)

- [ ] **Step 1: 포맷 자동 정리**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew spotlessApply`

- [ ] **Step 2: 전체 빌드(포맷 검사 + 컴파일 + 테스트)**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 포맷 변경분 있으면 커밋**

```bash
git add -A
git commit -m "Style: spotless 포맷 정리" || echo "no format changes"
```

---

## Self-Review

**Spec coverage:**
- Provider.APPLE + 에러코드 → Task 1 ✅
- 설정 프로퍼티(aud 리스트, team/key/.p8) + client_secret 생성 → Task 2 ✅
- identityToken 검증(JWKS, aud 리스트, iss/exp) → Task 3 ✅
- authorizationCode 교환 + revoke 클라이언트 → Task 4 ✅
- 요청 DTO → Task 5 ✅
- appleLogin(기존/신규/충돌 분기, Redis 저장, aud→client_id) → Task 6 ✅
- register 이관(email 임시 키 → userId 키) → Task 7 ✅
- 엔드포인트 + Swagger(에러 전수) → Task 8 ✅
- 탈퇴 revoke(실패해도 진행) → Task 9 ✅
- 빌드/포맷 → Task 10 ✅

**Placeholder scan:** 코드 단계는 모두 실제 코드 포함. DTO/테스트의 기존 시그니처 의존 부분은 "기존 파일 확인" 주석으로 명시(추측 금지 안내).

**Type consistency:** `AppleIdTokenPayload(email, aud)` / `exchangeRefreshToken(clientId, code)` / `revoke(clientId, refreshToken)` / Redis 키(`apple:refresh:{userId}`, `apple-refresh-temp:{email}`) / 저장 값(`{clientId}|{refreshToken}`) — Task 2~9 전반에서 동일하게 사용.

## 외부 의존 (구현과 별개로 운영에서 확보)

- `APPLE_CLIENT_IDS`(웹 Services ID, 후에 번들 ID 추가), `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`(`.p8` 내용) 환경변수 주입.
- 애플 개발자 콘솔: Services ID 등록 + 리턴 URL/도메인 인증(웹 Sign in with Apple 사용 시).

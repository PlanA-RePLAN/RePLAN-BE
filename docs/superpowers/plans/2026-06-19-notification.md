# 알림(푸시) 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버가 정한 순간(마감 하루 전 / 어제 실패한 투두 / 월간 리포트 도착)에 사용자에게 FCM 푸시 알림을 보내고, 앱 안 알림함에도 기록을 쌓는다.

**Architecture:** 새 `domain/notification` 도메인을 만든다. FCM 연동은 `PushSender` 포트(인터페이스) 뒤에 숨겨 단위 테스트를 mock으로 한다. 발송은 `NotificationService.send(...)` 한 곳을 통하며 ①설정 확인 ②알림함 저장 ③토큰별 푸시 ④죽은 토큰 삭제를 한다. 트리거는 자정 스케줄러 2종(마감 임박·실패 요약)과 월간 리포트 배치의 Spring 이벤트다.

**Tech Stack:** Spring Boot(WebMVC), Spring Data JPA, PostgreSQL + Flyway, Firebase Admin SDK(FCM), JUnit5 + Mockito + AssertJ, Lombok.

## Global Constraints

- 패키지 루트: `plana.replan.domain.notification`. Controller는 반드시 `XxxControllerDocs`(Swagger 어노테이션) + `XxxController`(매핑·구현)로 분리한다(`.claude/rules/swagger.md`).
- 현재 로그인 사용자는 컨트롤러에서 `@AuthenticationPrincipal Long userId`로 받는다.
- 응답은 `ResponseEntity<ApiResult<T>>` + `ApiResult.ok(data)` / `ApiResult.ok()`.
- 예외는 도메인별 `enum ... implements ErrorCode` + `throw new CustomException(코드)`.
- 엔티티는 `BaseTimeEntity` 상속(자동 `created_at`/`updated_at`/`deleted_at` + `softDelete()`).
- Flyway 마이그레이션 다음 번호는 **V12**부터(현재 최신 V11). PostgreSQL 문법만 사용(`.claude/rules/jpa.md`).
- JPQL 날짜 함수는 `EXTRACT(... FROM ...)`만(MySQL `FUNCTION('YEAR'...)` 금지).
- 시간은 테스트 가능하게 주입된 `java.time.Clock`(빈 `ClockConfig` 존재)을 쓴다. cron zone은 `Asia/Seoul`.
- 알림 문구(**정확히 이 문자열**):
  - 마감 임박 — title: `'{투두 제목}' 투두`, body: `주요 투두로 설정한 투두의 마감 시간이 하루 남았어요.`
  - 실패 리플랜 — title: `오늘 실패한 투두 {N}개 있어요.`, body: `실패한 투두의 리플랜을 진행해보세요.`
  - 리포트 도착 — title: `이번 달 리포트가 나왔어요.`, body: `{N}월 리포트를 확인해보세요.`
- "주요 투두" = `Todo.isPinned == true`. "실패한 투두" = 마감(dueDate)이 어제 안 + `isCompleted=false` + `isActive=true` + `replan IS NULL`, 핀 무관.
- 커밋은 작업(Task) 단위로 즉시. 커밋 메시지는 한국어, README 태그 규칙(`Feat:`/`Test:` 등). AI 흔적 금지.
- 코드 스타일: 커밋 전 `./gradlew spotlessApply` 또는 google-java-format 적용(프로젝트 관례).

---

## File Structure

```text
domain/notification/
  entity/
    DeviceToken.java            # 기기 FCM 토큰 (user 1:N)
    Platform.java               # enum WEB/ANDROID/IOS
    Notification.java           # 앱 안 알림함 한 줄
    NotificationCategory.java   # enum TODO/STATS/ETC (알림함 탭)
    NotificationType.java       # enum 3종 (카테고리 보유)
    TargetType.java             # enum TODO/REPORT/REPLAN (누르면 갈 곳)
  repository/
    DeviceTokenRepository.java
    NotificationRepository.java
  dto/
    DeviceTokenRegisterRequest.java
    DeviceTokenDeleteRequest.java
    NotificationResponse.java
    NotificationListResponse.java
    UnreadCountResponse.java
    NotificationSettingResponse.java
    NotificationSettingUpdateRequest.java
  service/
    DeviceTokenService.java     # 토큰 등록(upsert)/삭제
    NotificationService.java    # send(...) 공용 엔진 + 알림함 조회/읽음
    NotificationSettingService.java  # 설정 조회/변경 (user 칼럼)
    NotificationTriggerService.java  # 자정 트리거 로직(마감 임박/실패 요약)
  scheduler/
    NotificationScheduler.java  # @Scheduled → NotificationTriggerService 호출
  controller/
    NotificationTokenController.java / ...Docs.java
    NotificationController.java / ...Docs.java         # 알림함 + 설정
  event/
    MonthlyReportCreatedEvent.java
    MonthlyReportNotificationListener.java
  infra/
    PushSender.java             # 포트 (인터페이스)
    PushResult.java             # enum SUCCESS/DEAD_TOKEN/FAILURE
    FcmPushSender.java          # FCM 구현 (수동/통합 테스트)
  config/
    FirebaseConfig.java         # FirebaseApp 초기화
  exception/
    NotificationErrorCode.java

domain/user/entity/User.java    # 설정 칼럼 3개 추가 (수정)
domain/todo/repository/TodoRepository.java  # 쿼리 2개 추가 (수정)
domain/monthlyreport/batch/MonthlyReportItemWriter.java  # 이벤트 발행 (수정)
resources/db/migration/
  V12__add_device_token.sql
  V13__add_notification.sql
  V14__add_user_notification_settings.sql
```

---

## Phase 1 — 인프라 / 주소록 / 발송 포트

### Task 1: FCM 의존성 + Firebase 초기화 + PushSender 포트

**Files:**
- Modify: `build.gradle` (dependencies 블록)
- Create: `src/main/java/plana/replan/domain/notification/infra/PushResult.java`
- Create: `src/main/java/plana/replan/domain/notification/infra/PushSender.java`
- Create: `src/main/java/plana/replan/domain/notification/infra/FcmPushSender.java`
- Create: `src/main/java/plana/replan/domain/notification/config/FirebaseConfig.java`
- Modify: `src/main/resources/application.yaml`, `application-local.yaml`
- Test: `src/test/java/plana/replan/domain/notification/infra/FcmPushSenderTest.java`

**Interfaces:**
- Produces: `enum PushResult { SUCCESS, DEAD_TOKEN, FAILURE }`;
  `interface PushSender { PushResult send(String token, String title, String body, Map<String,String> data); }`;
  `FcmPushSender.classify(MessagingErrorCode) -> PushResult` (static, 테스트 대상).

- [ ] **Step 1: build.gradle 의존성 추가**

`dependencies { ... }` 안에 추가:

```groovy
    implementation 'com.google.firebase:firebase-admin:9.4.3'
```

- [ ] **Step 2: PushResult / PushSender 작성**

`PushResult.java`:

```java
package plana.replan.domain.notification.infra;

public enum PushResult {
  SUCCESS,
  DEAD_TOKEN, // 토큰이 더 이상 유효하지 않음(앱 삭제 등) → 저장소에서 지운다
  FAILURE // 일시적/기타 실패 → 토큰은 유지
}
```

`PushSender.java`:

```java
package plana.replan.domain.notification.infra;

import java.util.Map;

/** 푸시 발송 포트. 단위 테스트에서는 mock으로 대체한다. */
public interface PushSender {
  PushResult send(String token, String title, String body, Map<String, String> data);
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`FcmPushSenderTest.java`:

```java
package plana.replan.domain.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmPushSenderTest {

  @Test
  @DisplayName("UNREGISTERED/INVALID_ARGUMENT 는 죽은 토큰으로 분류한다")
  void classifiesDeadTokens() {
    assertThat(FcmPushSender.classify(MessagingErrorCode.UNREGISTERED))
        .isEqualTo(PushResult.DEAD_TOKEN);
    assertThat(FcmPushSender.classify(MessagingErrorCode.INVALID_ARGUMENT))
        .isEqualTo(PushResult.DEAD_TOKEN);
  }

  @Test
  @DisplayName("그 외 에러 코드는 일시적 실패로 분류한다")
  void classifiesOtherFailures() {
    assertThat(FcmPushSender.classify(MessagingErrorCode.INTERNAL))
        .isEqualTo(PushResult.FAILURE);
    assertThat(FcmPushSender.classify(null)).isEqualTo(PushResult.FAILURE);
  }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.infra.FcmPushSenderTest'`
Expected: FAIL — `FcmPushSender` 가 없어 컴파일 에러.

- [ ] **Step 5: FcmPushSender 구현**

```java
package plana.replan.domain.notification.infra;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

  private final FirebaseMessaging firebaseMessaging;

  @Override
  public PushResult send(String token, String title, String body, Map<String, String> data) {
    Message message =
        Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data)
            .build();
    try {
      firebaseMessaging.send(message);
      return PushResult.SUCCESS;
    } catch (FirebaseMessagingException e) {
      PushResult result = classify(e.getMessagingErrorCode());
      log.warn("FCM 발송 실패 - token={}, code={}, result={}", token, e.getMessagingErrorCode(), result);
      return result;
    }
  }

  static PushResult classify(MessagingErrorCode code) {
    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
      return PushResult.DEAD_TOKEN;
    }
    return PushResult.FAILURE;
  }
}
```

- [ ] **Step 6: FirebaseConfig 작성**

```java
package plana.replan.domain.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

  /** 서비스 계정 키 JSON 전체 문자열. 환경변수/시크릿으로 주입한다(절대 커밋 금지). */
  @Value("${firebase.service-account-json}")
  private String serviceAccountJson;

  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance();
    }
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(
            new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
    FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
    return FirebaseApp.initializeApp(options);
  }

  @Bean
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
```

- [ ] **Step 7: 설정 키 추가**

`application.yaml` 하단에:

```yaml
firebase:
  service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
```

`application-local.yaml` 에는 로컬 테스트용으로 같은 키를 두되 값은 환경변수로 주입한다(파일에 키 값 직접 넣지 않는다).

> 주의: 로컬에서 서버를 띄우려면 `FIREBASE_SERVICE_ACCOUNT_JSON` 환경변수에 Firebase 서비스 계정 키 JSON 전체를 넣어야 한다(설계서 8번 준비 단계). 테스트(`./gradlew test`)는 FirebaseConfig 빈을 로드하지 않는 단위 테스트라 키 없이도 통과한다.

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.infra.FcmPushSenderTest'`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add build.gradle src/main/java/plana/replan/domain/notification/infra src/main/java/plana/replan/domain/notification/config src/main/resources/application.yaml src/main/resources/application-local.yaml src/test/java/plana/replan/domain/notification/infra
git commit -m "Feat: FCM 푸시 발송 포트와 Firebase 초기화 추가"
```

---

### Task 2: DeviceToken 엔티티 + 마이그레이션 + 저장소

**Files:**
- Create: `entity/Platform.java`, `entity/DeviceToken.java`
- Create: `resources/db/migration/V12__add_device_token.sql`
- Create: `repository/DeviceTokenRepository.java`
- Test: `src/test/java/plana/replan/domain/notification/entity/DeviceTokenTest.java`

**Interfaces:**
- Produces: `Platform{WEB,ANDROID,IOS}`; `DeviceToken`(빌더: `user`, `token`, `platform`; getter `getToken()`, `getUser()`, `getPlatform()`; 메서드 `updatePlatform(Platform)`);
  `DeviceTokenRepository extends JpaRepository<DeviceToken,Long>` with `Optional<DeviceToken> findByToken(String)`, `List<DeviceToken> findAllByUser(User)`, `Optional<DeviceToken> findByUserAndToken(User,String)`.

- [ ] **Step 1: 실패하는 테스트 작성**

`DeviceTokenTest.java`:

```java
package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceTokenTest {

  @Test
  @DisplayName("플랫폼을 갱신할 수 있다")
  void updatePlatform() {
    DeviceToken token =
        DeviceToken.builder().user(null).token("abc").platform(Platform.WEB).build();

    token.updatePlatform(Platform.ANDROID);

    assertThat(token.getPlatform()).isEqualTo(Platform.ANDROID);
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.entity.DeviceTokenTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: Platform / DeviceToken 작성**

`Platform.java`:

```java
package plana.replan.domain.notification.entity;

public enum Platform {
  WEB,
  ANDROID,
  IOS
}
```

`DeviceToken.java`:

```java
package plana.replan.domain.notification.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // FCM 토큰. 같은 토큰 중복 저장 금지(DB 유니크는 V12 마이그레이션에서 관리).
  @Column(nullable = false, columnDefinition = "TEXT")
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Platform platform;

  @Builder
  public DeviceToken(User user, String token, Platform platform) {
    this.user = user;
    this.token = Objects.requireNonNull(token, "토큰은 필수입니다.");
    this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
  }

  public void updatePlatform(Platform platform) {
    this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
  }
}
```

- [ ] **Step 4: 마이그레이션 작성**

`V12__add_device_token.sql`:

```sql
CREATE TABLE device_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES "user" (id),
    token       TEXT   NOT NULL,
    platform    VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- 같은 FCM 토큰은 한 번만 저장한다.
CREATE UNIQUE INDEX ux_device_token_token ON device_token (token);
CREATE INDEX ix_device_token_user ON device_token (user_id);
```

> 참고: user 테이블명이 예약어라 `"user"`로 참조. 기존 마이그레이션(V1__init.sql)에서 user 테이블이 어떻게 정의됐는지 확인해 FK 참조 표기를 맞출 것.

- [ ] **Step 5: 저장소 작성**

`DeviceTokenRepository.java`:

```java
package plana.replan.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.user.entity.User;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
  Optional<DeviceToken> findByToken(String token);

  Optional<DeviceToken> findByUserAndToken(User user, String token);

  List<DeviceToken> findAllByUser(User user);
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.entity.DeviceTokenTest'`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/entity src/main/java/plana/replan/domain/notification/repository/DeviceTokenRepository.java src/main/resources/db/migration/V12__add_device_token.sql src/test/java/plana/replan/domain/notification/entity/DeviceTokenTest.java
git commit -m "Feat: 기기 FCM 토큰 저장용 device_token 테이블 추가"
```

---

### Task 3: 토큰 등록/삭제 서비스 + DTO + 에러코드

**Files:**
- Create: `exception/NotificationErrorCode.java`
- Create: `dto/DeviceTokenRegisterRequest.java`, `dto/DeviceTokenDeleteRequest.java`
- Create: `service/DeviceTokenService.java`
- Test: `src/test/java/plana/replan/domain/notification/service/DeviceTokenServiceTest.java`

**Interfaces:**
- Consumes: `DeviceTokenRepository`, `UserRepository`, `DeviceToken`, `Platform`.
- Produces: `DeviceTokenService.register(Long userId, DeviceTokenRegisterRequest)` (upsert: 같은 토큰 있으면 소유자/플랫폼 갱신), `DeviceTokenService.delete(Long userId, DeviceTokenDeleteRequest)`;
  `NotificationErrorCode{ TOKEN_NOT_FOUND(404,...) }`.

- [ ] **Step 1: 에러코드 + DTO 작성**

`NotificationErrorCode.java`:

```java
package plana.replan.domain.notification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
  TOKEN_NOT_FOUND(404, "등록된 기기 토큰을 찾을 수 없습니다."),
  NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다.");

  private final int status;
  private final String message;
}
```

`DeviceTokenRegisterRequest.java`:

```java
package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import plana.replan.domain.notification.entity.Platform;

@Schema(description = "기기 토큰 등록 요청")
public record DeviceTokenRegisterRequest(
    @Schema(description = "FCM 토큰", example = "fcm-token-xyz",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String token,
    @Schema(description = "기기 종류", example = "WEB",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Platform platform) {}
```

`DeviceTokenDeleteRequest.java`:

```java
package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "기기 토큰 삭제 요청")
public record DeviceTokenDeleteRequest(
    @Schema(description = "삭제할 FCM 토큰", example = "fcm-token-xyz",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String token) {}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`DeviceTokenServiceTest.java`:

```java
package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Platform;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private DeviceTokenService deviceTokenService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("nick")
        .role(Role.USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("새 토큰이면 저장한다")
  void registerNewToken() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));
    given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.empty());

    deviceTokenService.register(1L, new DeviceTokenRegisterRequest("t1", Platform.WEB));

    verify(deviceTokenRepository).save(any(DeviceToken.class));
  }

  @Test
  @DisplayName("이미 있는 토큰이면 새로 저장하지 않고 플랫폼만 갱신한다")
  void registerExistingTokenUpserts() {
    DeviceToken existing =
        DeviceToken.builder().user(user()).token("t1").platform(Platform.WEB).build();
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));
    given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(existing));

    deviceTokenService.register(1L, new DeviceTokenRegisterRequest("t1", Platform.ANDROID));

    assertThat(existing.getPlatform()).isEqualTo(Platform.ANDROID);
    verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
  }

  @Test
  @DisplayName("내 토큰이 아니면 삭제 시 예외")
  void deleteMissingToken() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));
    given(deviceTokenRepository.findByUserAndToken(u, "nope")).willReturn(Optional.empty());

    assertThatThrownBy(() -> deviceTokenService.delete(1L, new DeviceTokenDeleteRequest("nope")))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(NotificationErrorCode.TOKEN_NOT_FOUND.getMessage());
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.DeviceTokenServiceTest'`
Expected: FAIL — `DeviceTokenService` 없음.

- [ ] **Step 4: 서비스 구현**

`DeviceTokenService.java`:

```java
package plana.replan.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final UserRepository userRepository;

  @Transactional
  public void register(Long userId, DeviceTokenRegisterRequest request) {
    User user = findUser(userId);
    deviceTokenRepository
        .findByToken(request.token())
        .ifPresentOrElse(
            existing -> {
              existing.updatePlatform(request.platform());
              existing.changeOwner(user); // 같은 토큰이 다른 계정으로 재등록될 수 있음
            },
            () ->
                deviceTokenRepository.save(
                    DeviceToken.builder()
                        .user(user)
                        .token(request.token())
                        .platform(request.platform())
                        .build()));
  }

  @Transactional
  public void delete(Long userId, DeviceTokenDeleteRequest request) {
    User user = findUser(userId);
    DeviceToken token =
        deviceTokenRepository
            .findByUserAndToken(user, request.token())
            .orElseThrow(() -> new CustomException(NotificationErrorCode.TOKEN_NOT_FOUND));
    deviceTokenRepository.delete(token);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
```

- [ ] **Step 5: DeviceToken에 소유자 변경 메서드 추가**

`DeviceToken.java` 에 메서드 추가:

```java
  public void changeOwner(User user) {
    this.user = user;
  }
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.DeviceTokenServiceTest'`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification
git commit -m "Feat: 기기 토큰 등록·삭제 서비스 추가"
```

---

### Task 4: 토큰 컨트롤러 + Docs

**Files:**
- Create: `controller/NotificationTokenControllerDocs.java`, `controller/NotificationTokenController.java`

**Interfaces:**
- Consumes: `DeviceTokenService.register/delete`.
- Produces: `POST /api/notifications/tokens`, `DELETE /api/notifications/tokens`.

- [ ] **Step 1: Docs 인터페이스 작성**

`NotificationTokenControllerDocs.java`:

```java
package plana.replan.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.global.common.ApiResult;

@Tag(name = "Notification Token", description = "푸시 알림 기기 토큰 등록/삭제 API")
public interface NotificationTokenControllerDocs {

  @Operation(
      summary = "기기 토큰 등록",
      description =
          "알림 권한 허용 후 프론트가 받은 FCM 토큰을 등록한다. 같은 토큰을 다시 보내면 갱신(upsert)된다.\n\n"
              + "### Request Headers\n"
              + "| 헤더명 | 필수 여부 | 타입 | 설명 |\n"
              + "|--------|-----------|------|------|\n"
              + "| Authorization | ✅ 필수 | string | `Bearer {accessToken}` |\n"
              + "| Content-Type | ✅ 필수 | string | `application/json` |\n\n"
              + "### Request Body\n"
              + "| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|--------|-----------|------|------|------|\n"
              + "| token | ✅ 필수 | string | FCM 토큰 | `\"fcm-token-xyz\"` |\n"
              + "| platform | ✅ 필수 | string | 기기 종류 (WEB/ANDROID/IOS) | `\"WEB\"` |")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "등록 성공",
        content =
            @Content(
                examples =
                    @ExampleObject(value = "{\"status\":200,\"success\":true,\"data\":null,\"error\":null}"))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content =
            @Content(
                examples = {
                  @ExampleObject(
                      name = "토큰 없음",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EMPTY_TOKEN\",\"message\":\"인증 토큰이 없습니다.\"}}"),
                  @ExampleObject(
                      name = "만료된 토큰",
                      value =
                          "{\"status\":401,\"success\":false,\"data\":null,\"error\":{\"code\":\"EXPIRED_TOKEN\",\"message\":\"만료된 토큰입니다.\"}}")
                })),
    @ApiResponse(
        responseCode = "404",
        description = "사용자를 찾을 수 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"사용자를 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<Void>> registerToken(Long userId, @Valid DeviceTokenRegisterRequest request);

  @Operation(
      summary = "기기 토큰 삭제",
      description =
          "로그아웃 시 해당 기기의 FCM 토큰을 삭제한다.\n\n"
              + "### Request Body\n"
              + "| 필드명 | 필수 여부 | 타입 | 설명 | 예시 |\n"
              + "|--------|-----------|------|------|------|\n"
              + "| token | ✅ 필수 | string | 삭제할 FCM 토큰 | `\"fcm-token-xyz\"` |")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "404",
        description = "토큰 없음",
        content =
            @Content(
                examples =
                    @ExampleObject(
                        value =
                            "{\"status\":404,\"success\":false,\"data\":null,\"error\":{\"code\":\"TOKEN_NOT_FOUND\",\"message\":\"등록된 기기 토큰을 찾을 수 없습니다.\"}}")))
  })
  ResponseEntity<ApiResult<Void>> deleteToken(Long userId, @Valid DeviceTokenDeleteRequest request);
}
```

- [ ] **Step 2: 컨트롤러 구현**

`NotificationTokenController.java`:

```java
package plana.replan.domain.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.service.DeviceTokenService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/notifications/tokens")
@RequiredArgsConstructor
public class NotificationTokenController implements NotificationTokenControllerDocs {

  private final DeviceTokenService deviceTokenService;

  @Override
  @PostMapping
  public ResponseEntity<ApiResult<Void>> registerToken(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody DeviceTokenRegisterRequest request) {
    deviceTokenService.register(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @DeleteMapping
  public ResponseEntity<ApiResult<Void>> deleteToken(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody DeviceTokenDeleteRequest request) {
    deviceTokenService.delete(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/controller
git commit -m "Feat: 기기 토큰 등록·삭제 API 추가"
```

---

## Phase 2 — 알림함 / 설정 / 발송 엔진

### Task 5: Notification 엔티티 + enum 3종 + 마이그레이션 + 저장소

**Files:**
- Create: `entity/NotificationCategory.java`, `entity/NotificationType.java`, `entity/TargetType.java`, `entity/Notification.java`
- Create: `resources/db/migration/V13__add_notification.sql`
- Create: `repository/NotificationRepository.java`
- Test: `src/test/java/plana/replan/domain/notification/entity/NotificationTest.java`

**Interfaces:**
- Produces:
  - `NotificationCategory{ TODO, STATS, ETC }`
  - `NotificationType{ TODO_DUE_SOON(TODO), TODO_FAILED_REPLAN(TODO), REPORT_READY(STATS) }` — `getCategory()` 보유
  - `TargetType{ TODO, REPORT, REPLAN }`
  - `Notification`(빌더: `user, type, title, body, targetType, targetId`; 자동 `category = type.getCategory()`; `markRead()`, `isRead()`)
  - `NotificationRepository`: `findByIdAndUser`, `countByUserAndIsReadFalse`, 커서 목록 쿼리 2종(전체/카테고리).

- [ ] **Step 1: 실패하는 테스트 작성**

`NotificationTest.java`:

```java
package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  @DisplayName("타입이 정해지면 카테고리가 자동으로 따라온다")
  void categoryFollowsType() {
    Notification n =
        Notification.builder()
            .user(null)
            .type(NotificationType.REPORT_READY)
            .title("t")
            .body("b")
            .targetType(TargetType.REPORT)
            .targetId(5L)
            .build();

    assertThat(n.getCategory()).isEqualTo(NotificationCategory.STATS);
    assertThat(n.isRead()).isFalse();
  }

  @Test
  @DisplayName("읽음 처리하면 isRead 가 true 가 된다")
  void markRead() {
    Notification n =
        Notification.builder()
            .user(null)
            .type(NotificationType.TODO_DUE_SOON)
            .title("t")
            .body("b")
            .targetType(TargetType.TODO)
            .targetId(1L)
            .build();

    n.markRead();

    assertThat(n.isRead()).isTrue();
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.entity.NotificationTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: enum 3종 작성**

`NotificationCategory.java`:

```java
package plana.replan.domain.notification.entity;

public enum NotificationCategory {
  TODO,
  STATS,
  ETC
}
```

`NotificationType.java`:

```java
package plana.replan.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
  TODO_DUE_SOON(NotificationCategory.TODO),
  TODO_FAILED_REPLAN(NotificationCategory.TODO),
  REPORT_READY(NotificationCategory.STATS);

  private final NotificationCategory category;
}
```

`TargetType.java`:

```java
package plana.replan.domain.notification.entity;

public enum TargetType {
  TODO,
  REPORT,
  REPLAN
}
```

- [ ] **Step 4: Notification 엔티티 작성**

`Notification.java`:

```java
package plana.replan.domain.notification.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.user.entity.User;
import plana.replan.global.entity.BaseTimeEntity;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", length = 16)
  private TargetType targetType;

  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "is_read", nullable = false)
  private boolean isRead = false;

  @Builder
  public Notification(
      User user,
      NotificationType type,
      String title,
      String body,
      TargetType targetType,
      Long targetId) {
    this.user = user;
    this.type = Objects.requireNonNull(type, "알림 종류는 필수입니다.");
    this.category = type.getCategory();
    this.title = Objects.requireNonNull(title, "제목은 필수입니다.");
    this.body = Objects.requireNonNull(body, "내용은 필수입니다.");
    this.targetType = targetType;
    this.targetId = targetId;
  }

  public void markRead() {
    this.isRead = true;
  }
}
```

- [ ] **Step 5: 마이그레이션 작성**

`V13__add_notification.sql`:

```sql
CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES "user" (id),
    category    VARCHAR(16) NOT NULL,
    type        VARCHAR(32) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        VARCHAR(255) NOT NULL,
    target_type VARCHAR(16),
    target_id   BIGINT,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- 목록(최신순, 카테고리 필터)과 안읽음 카운트에 쓰는 인덱스
CREATE INDEX ix_notification_user_id_desc ON notification (user_id, id DESC);
CREATE INDEX ix_notification_user_category ON notification (user_id, category);
```

- [ ] **Step 6: 저장소 작성**

`NotificationRepository.java`:

```java
package plana.replan.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.domain.user.entity.User;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Optional<Notification> findByIdAndUser(Long id, User user);

  long countByUserAndIsReadFalse(User user);

  List<Notification> findByUserAndIsReadFalse(User user);

  // 전체 목록 (cursor = 직전 응답의 마지막 id, 없으면 Long.MAX_VALUE 전달)
  @Query(
      "SELECT n FROM Notification n WHERE n.user = :user AND n.id < :cursor ORDER BY n.id DESC")
  List<Notification> findPage(
      @Param("user") User user, @Param("cursor") Long cursor, Pageable pageable);

  // 카테고리(탭) 필터 목록
  @Query(
      "SELECT n FROM Notification n WHERE n.user = :user AND n.category = :category"
          + " AND n.id < :cursor ORDER BY n.id DESC")
  List<Notification> findPageByCategory(
      @Param("user") User user,
      @Param("category") NotificationCategory category,
      @Param("cursor") Long cursor,
      Pageable pageable);
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.entity.NotificationTest'`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/entity src/main/java/plana/replan/domain/notification/repository/NotificationRepository.java src/main/resources/db/migration/V13__add_notification.sql src/test/java/plana/replan/domain/notification/entity/NotificationTest.java
git commit -m "Feat: 앱 안 알림함 notification 테이블 추가"
```

---

### Task 6: 사용자 알림 설정(칼럼 3개) + 설정 서비스/API

**Files:**
- Modify: `domain/user/entity/User.java` (필드 3개 + 메서드)
- Create: `resources/db/migration/V14__add_user_notification_settings.sql`
- Create: `dto/NotificationSettingResponse.java`, `dto/NotificationSettingUpdateRequest.java`
- Create: `service/NotificationSettingService.java`
- Test: `src/test/java/plana/replan/domain/notification/service/NotificationSettingServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `User`.
- Produces:
  - `User`: `boolean isNotifyTodoDue()`, `isNotifyTodoFailed()`, `isNotifyReport()`, `void updateNotificationSettings(Boolean todoDue, Boolean todoFailed, Boolean report)` (null이면 해당 항목 유지).
  - `NotificationSettingService.get(Long userId) -> NotificationSettingResponse`, `update(Long userId, NotificationSettingUpdateRequest) -> NotificationSettingResponse`.
  - `NotificationSettingResponse(boolean todoDue, boolean todoFailed, boolean report)`.

- [ ] **Step 1: User 엔티티에 필드/메서드 추가**

`User.java` 의 필드 영역(예: profileImage 아래)에 추가:

```java
  @Column(name = "notify_todo_due", nullable = false)
  private boolean notifyTodoDue = true;

  @Column(name = "notify_todo_failed", nullable = false)
  private boolean notifyTodoFailed = true;

  @Column(name = "notify_report", nullable = false)
  private boolean notifyReport = true;
```

같은 클래스에 메서드 추가:

```java
  public void updateNotificationSettings(Boolean todoDue, Boolean todoFailed, Boolean report) {
    if (todoDue != null) {
      this.notifyTodoDue = todoDue;
    }
    if (todoFailed != null) {
      this.notifyTodoFailed = todoFailed;
    }
    if (report != null) {
      this.notifyReport = report;
    }
  }
```

> `@Getter`가 클래스에 있으면 `isNotifyTodoDue()` 등은 자동 생성된다. 없으면 boolean getter를 직접 추가한다.

- [ ] **Step 2: 마이그레이션 작성**

`V14__add_user_notification_settings.sql`:

```sql
ALTER TABLE "user"
    ADD COLUMN notify_todo_due    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_todo_failed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_report      BOOLEAN NOT NULL DEFAULT TRUE;
```

- [ ] **Step 3: DTO 작성**

`NotificationSettingResponse.java`:

```java
package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.user.entity.User;

@Schema(description = "알림 설정")
public record NotificationSettingResponse(
    @JsonProperty("todoDue") @Schema(description = "마감 임박 알림 받기", example = "true")
        boolean todoDue,
    @JsonProperty("todoFailed") @Schema(description = "실패 리플랜 알림 받기", example = "true")
        boolean todoFailed,
    @JsonProperty("report") @Schema(description = "리포트 도착 알림 받기", example = "true")
        boolean report) {

  public static NotificationSettingResponse from(User user) {
    return new NotificationSettingResponse(
        user.isNotifyTodoDue(), user.isNotifyTodoFailed(), user.isNotifyReport());
  }
}
```

> `@JsonProperty`는 boolean record 컴포넌트가 `is` 접두사 없이 직렬화될 때 키 이름을 고정하기 위한 것(프로젝트 메모리 규칙).

`NotificationSettingUpdateRequest.java`:

```java
package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 변경 요청 (보낼 항목만 포함, 생략/null은 기존값 유지)")
public record NotificationSettingUpdateRequest(
    @Schema(description = "마감 임박 알림 받기", example = "false") Boolean todoDue,
    @Schema(description = "실패 리플랜 알림 받기", example = "true") Boolean todoFailed,
    @Schema(description = "리포트 도착 알림 받기", example = "true") Boolean report) {}
```

- [ ] **Step 4: 실패하는 테스트 작성**

`NotificationSettingServiceTest.java`:

```java
package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private NotificationSettingService settingService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("nick")
        .role(Role.USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("기본값은 모두 켜짐이다")
  void defaultsAllOn() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));

    NotificationSettingResponse res = settingService.get(1L);

    assertThat(res.todoDue()).isTrue();
    assertThat(res.todoFailed()).isTrue();
    assertThat(res.report()).isTrue();
  }

  @Test
  @DisplayName("일부만 보내면 그 항목만 바뀌고 나머지는 유지된다")
  void partialUpdate() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    NotificationSettingResponse res =
        settingService.update(1L, new NotificationSettingUpdateRequest(false, null, null));

    assertThat(res.todoDue()).isFalse();
    assertThat(res.todoFailed()).isTrue();
    assertThat(res.report()).isTrue();
  }
}
```

- [ ] **Step 5: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationSettingServiceTest'`
Expected: FAIL — `NotificationSettingService` 없음.

- [ ] **Step 6: 서비스 구현**

`NotificationSettingService.java`:

```java
package plana.replan.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public NotificationSettingResponse get(Long userId) {
    return NotificationSettingResponse.from(findUser(userId));
  }

  @Transactional
  public NotificationSettingResponse update(Long userId, NotificationSettingUpdateRequest request) {
    User user = findUser(userId);
    user.updateNotificationSettings(request.todoDue(), request.todoFailed(), request.report());
    return NotificationSettingResponse.from(user);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationSettingServiceTest'`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/plana/replan/domain/user/entity/User.java src/main/resources/db/migration/V14__add_user_notification_settings.sql src/main/java/plana/replan/domain/notification
git commit -m "Feat: 사용자별 알림 설정(마감·실패·리포트 켜기끄기) 추가"
```

---

### Task 7: 발송 공용 엔진 NotificationService.send(...)

**Files:**
- Modify: `service/NotificationService.java` (신규 생성)
- Test: `src/test/java/plana/replan/domain/notification/service/NotificationServiceSendTest.java`

**Interfaces:**
- Consumes: `NotificationRepository`, `DeviceTokenRepository`, `PushSender`, `PushResult`, `User`, `NotificationType`, `TargetType`.
- Produces: `NotificationService.send(User user, NotificationType type, String title, String body, TargetType targetType, Long targetId)` — 설정 꺼져 있으면 아무것도 안 함; 켜져 있으면 알림함 저장 + 토큰별 발송 + 죽은 토큰 삭제.

- [ ] **Step 1: 실패하는 테스트 작성**

`NotificationServiceSendTest.java`:

```java
package plana.replan.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.Platform;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.infra.PushResult;
import plana.replan.domain.notification.infra.PushSender;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationServiceSendTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private PushSender pushSender;
  @InjectMocks private NotificationService notificationService;

  private User userWithReportOff() {
    User u =
        User.builder()
            .email("a@a.com")
            .nickname("n")
            .role(Role.USER)
            .provider(Provider.LOCAL)
            .build();
    u.updateNotificationSettings(true, true, false); // report off
    return u;
  }

  private User userAllOn() {
    return User.builder()
        .email("b@b.com")
        .nickname("n")
        .role(Role.USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("설정이 꺼져 있으면 저장도 발송도 하지 않는다")
  void skipsWhenSettingOff() {
    notificationService.send(
        userWithReportOff(), NotificationType.REPORT_READY, "t", "b", TargetType.REPORT, 1L);

    verify(notificationRepository, never()).save(any());
    verify(pushSender, never()).send(any(), any(), any(), anyMap());
  }

  @Test
  @DisplayName("설정이 켜져 있으면 알림함에 저장하고 토큰마다 발송한다")
  void savesAndSends() {
    User u = userAllOn();
    DeviceToken t1 = DeviceToken.builder().user(u).token("a").platform(Platform.WEB).build();
    DeviceToken t2 = DeviceToken.builder().user(u).token("b").platform(Platform.ANDROID).build();
    given(deviceTokenRepository.findAllByUser(u)).willReturn(List.of(t1, t2));
    given(pushSender.send(any(), any(), any(), anyMap())).willReturn(PushResult.SUCCESS);

    notificationService.send(
        u, NotificationType.TODO_DUE_SOON, "title", "body", TargetType.TODO, 9L);

    verify(notificationRepository).save(any(Notification.class));
    verify(pushSender).send(eq("a"), eq("title"), eq("body"), anyMap());
    verify(pushSender).send(eq("b"), eq("title"), eq("body"), anyMap());
  }

  @Test
  @DisplayName("죽은 토큰 응답을 받으면 그 토큰을 삭제한다")
  void deletesDeadToken() {
    User u = userAllOn();
    DeviceToken dead = DeviceToken.builder().user(u).token("dead").platform(Platform.WEB).build();
    given(deviceTokenRepository.findAllByUser(u)).willReturn(List.of(dead));
    given(pushSender.send(any(), any(), any(), anyMap())).willReturn(PushResult.DEAD_TOKEN);

    notificationService.send(
        u, NotificationType.TODO_DUE_SOON, "title", "body", TargetType.TODO, 9L);

    verify(deviceTokenRepository).delete(dead);
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationServiceSendTest'`
Expected: FAIL — `NotificationService` 없음.

- [ ] **Step 3: NotificationService 구현(send 부분)**

`NotificationService.java`:

```java
package plana.replan.domain.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.infra.PushResult;
import plana.replan.domain.notification.infra.PushSender;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final PushSender pushSender;

  @Transactional
  public void send(
      User user,
      NotificationType type,
      String title,
      String body,
      TargetType targetType,
      Long targetId) {
    if (!isEnabled(user, type)) {
      return;
    }

    notificationRepository.save(
        Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .body(body)
            .targetType(targetType)
            .targetId(targetId)
            .build());

    Map<String, String> data = new HashMap<>();
    data.put("type", type.name());
    data.put("targetType", targetType == null ? "" : targetType.name());
    data.put("targetId", targetId == null ? "" : String.valueOf(targetId));

    List<DeviceToken> tokens = deviceTokenRepository.findAllByUser(user);
    for (DeviceToken token : tokens) {
      PushResult result;
      try {
        result = pushSender.send(token.getToken(), title, body, data);
      } catch (Exception e) {
        // 푸시 실패가 알림함 저장/다음 처리를 막으면 안 된다.
        log.warn("푸시 발송 중 예외 - tokenId={}", token.getId(), e);
        continue;
      }
      if (result == PushResult.DEAD_TOKEN) {
        deviceTokenRepository.delete(token);
      }
    }
  }

  private boolean isEnabled(User user, NotificationType type) {
    return switch (type) {
      case TODO_DUE_SOON -> user.isNotifyTodoDue();
      case TODO_FAILED_REPLAN -> user.isNotifyTodoFailed();
      case REPORT_READY -> user.isNotifyReport();
    };
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationServiceSendTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/service/NotificationService.java src/test/java/plana/replan/domain/notification/service/NotificationServiceSendTest.java
git commit -m "Feat: 알림 발송 공용 엔진(설정 확인·알림함 저장·푸시·죽은토큰 삭제) 추가"
```

---

### Task 8: 알림함 조회/읽음 API

**Files:**
- Modify: `service/NotificationService.java` (조회/읽음 메서드 추가)
- Create: `dto/NotificationResponse.java`, `dto/NotificationListResponse.java`, `dto/UnreadCountResponse.java`
- Create: `controller/NotificationControllerDocs.java`, `controller/NotificationController.java`
- Test: `src/test/java/plana/replan/domain/notification/service/NotificationServiceQueryTest.java`

**Interfaces:**
- Consumes: `NotificationRepository`, `UserRepository`, `Notification`.
- Produces:
  - `NotificationService.getList(Long userId, NotificationCategory category, Long cursor, int size) -> NotificationListResponse`
  - `NotificationService.getUnreadCount(Long userId) -> UnreadCountResponse`
  - `NotificationService.markRead(Long userId, Long notificationId)`
  - `NotificationService.markAllRead(Long userId)`
  - `NotificationResponse(Long id, String category, String type, String title, String body, String targetType, Long targetId, boolean read, String createdAt)`
  - `NotificationListResponse(List<NotificationResponse> items, Long nextCursor, boolean hasNext)`
  - `UnreadCountResponse(long count)`
  - 엔드포인트: `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`.

- [ ] **Step 1: DTO 작성**

`NotificationResponse.java`:

```java
package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.notification.entity.Notification;

@Schema(description = "알림 한 건")
public record NotificationResponse(
    @Schema(description = "알림 id", example = "12") Long id,
    @Schema(description = "탭 분류 (TODO/STATS/ETC)", example = "TODO") String category,
    @Schema(description = "알림 종류", example = "TODO_DUE_SOON") String type,
    @Schema(description = "제목", example = "'영단어 100개 암기' 투두") String title,
    @Schema(description = "내용", example = "주요 투두로 설정한 투두의 마감 시간이 하루 남았어요.") String body,
    @Schema(description = "누르면 갈 화면 종류 (없으면 null)", example = "TODO") String targetType,
    @Schema(description = "누르면 갈 대상 id (없으면 null)", example = "9") Long targetId,
    @JsonProperty("read") @Schema(description = "읽음 여부", example = "false") boolean read,
    @Schema(description = "생성 시각 (ISO 8601 형식)", example = "2026-06-19T00:00:00")
        String createdAt) {

  public static NotificationResponse from(Notification n) {
    return new NotificationResponse(
        n.getId(),
        n.getCategory().name(),
        n.getType().name(),
        n.getTitle(),
        n.getBody(),
        n.getTargetType() == null ? null : n.getTargetType().name(),
        n.getTargetId(),
        n.isRead(),
        n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
  }
}
```

`NotificationListResponse.java`:

```java
package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "알림함 목록 (무한스크롤)")
public record NotificationListResponse(
    @Schema(description = "알림 목록") List<NotificationResponse> items,
    @Schema(description = "다음 cursor. 마지막이면 null", example = "37") Long nextCursor,
    @JsonProperty("hasNext") @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext) {}
```

`UnreadCountResponse.java`:

```java
package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "안 읽은 알림 개수")
public record UnreadCountResponse(
    @Schema(description = "안 읽은 알림 개수", example = "3") long count) {}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`NotificationServiceQueryTest.java`:

```java
package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.entity.Notification;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.notification.repository.NotificationRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceQueryTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private NotificationService notificationService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.USER)
        .provider(Provider.LOCAL)
        .build();
  }

  private Notification noti(long id) {
    Notification n =
        Notification.builder()
            .user(null)
            .type(NotificationType.TODO_DUE_SOON)
            .title("t")
            .body("b")
            .targetType(TargetType.TODO)
            .targetId(1L)
            .build();
    ReflectionTestUtils.setField(n, "id", id);
    return n;
  }

  @Test
  @DisplayName("size+1 개가 오면 hasNext=true 이고 마지막 1개는 잘라낸다")
  void listHasNext() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(java.util.Optional.of(u));
    given(notificationRepository.findPage(eq(u), eq(Long.MAX_VALUE), any(Pageable.class)))
        .willReturn(List.of(noti(10), noti(9), noti(8))); // size=2 요청 → 3개 반환

    NotificationListResponse res = notificationService.getList(1L, null, null, 2);

    assertThat(res.items()).hasSize(2);
    assertThat(res.hasNext()).isTrue();
    assertThat(res.nextCursor()).isEqualTo(9L);
  }

  @Test
  @DisplayName("내 알림이 아니면 읽음 처리 시 예외")
  void markReadNotFound() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(java.util.Optional.of(u));
    given(notificationRepository.findByIdAndUser(99L, u)).willReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(1L, 99L))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationServiceQueryTest'`
Expected: FAIL — 조회 메서드 없음.

- [ ] **Step 4: NotificationService에 조회/읽음 메서드 + UserRepository 의존 추가**

`NotificationService.java` 수정: 필드에 `UserRepository userRepository;` 추가하고 아래 메서드들을 클래스에 추가한다.

```java
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public plana.replan.domain.notification.dto.NotificationListResponse getList(
      Long userId,
      plana.replan.domain.notification.entity.NotificationCategory category,
      Long cursor,
      int size) {
    User user = findUser(userId);
    long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;
    org.springframework.data.domain.Pageable pageable =
        org.springframework.data.domain.PageRequest.of(0, size + 1);

    List<Notification> rows =
        category == null
            ? notificationRepository.findPage(user, effectiveCursor, pageable)
            : notificationRepository.findPageByCategory(user, category, effectiveCursor, pageable);

    boolean hasNext = rows.size() > size;
    List<Notification> page = hasNext ? rows.subList(0, size) : rows;
    Long nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getId();

    return new plana.replan.domain.notification.dto.NotificationListResponse(
        page.stream()
            .map(plana.replan.domain.notification.dto.NotificationResponse::from)
            .toList(),
        hasNext ? nextCursor : null,
        hasNext);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public plana.replan.domain.notification.dto.UnreadCountResponse getUnreadCount(Long userId) {
    User user = findUser(userId);
    return new plana.replan.domain.notification.dto.UnreadCountResponse(
        notificationRepository.countByUserAndIsReadFalse(user));
  }

  @org.springframework.transaction.annotation.Transactional
  public void markRead(Long userId, Long notificationId) {
    User user = findUser(userId);
    Notification n =
        notificationRepository
            .findByIdAndUser(notificationId, user)
            .orElseThrow(
                () ->
                    new plana.replan.global.exception.CustomException(
                        plana.replan.domain.notification.exception.NotificationErrorCode
                            .NOTIFICATION_NOT_FOUND));
    n.markRead();
  }

  @org.springframework.transaction.annotation.Transactional
  public void markAllRead(Long userId) {
    User user = findUser(userId);
    notificationRepository.findByUserAndIsReadFalse(user).forEach(Notification::markRead);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () ->
                new plana.replan.global.exception.CustomException(
                    plana.replan.domain.user.exception.UserErrorCode.USER_NOT_FOUND));
  }
```

> 위 메서드들에서 정규화를 위해 클래스 상단 import를 추가하고 fully-qualified 이름을 정리하는 것은 구현 시 자유롭게 한다(컴파일만 통과하면 됨). `private final UserRepository userRepository;` 를 필드에 추가하는 것을 잊지 말 것.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationServiceQueryTest'`
Expected: PASS

- [ ] **Step 6: 컨트롤러 + Docs 작성**

`NotificationControllerDocs.java` — `@Tag(name = "Notification", ...)`. 4개 메서드에 `@Operation` + Request Headers / Query Parameters / Path Variable / Response Elements 표를 `.claude/rules/swagger.md` 형식으로 작성하고, 각 메서드에 200 + 401(EMPTY_TOKEN/EXPIRED_TOKEN 두 예시) + 404 응답 예시(`USER_NOT_FOUND`, 읽음 처리에는 `NOTIFICATION_NOT_FOUND`)를 단다. 무한스크롤은 description에 Step 1~3 사용법을 포함한다. 시그니처:

```java
ResponseEntity<ApiResult<NotificationListResponse>> getNotifications(
    Long userId, NotificationCategory category, Long cursor, int size);

ResponseEntity<ApiResult<UnreadCountResponse>> getUnreadCount(Long userId);

ResponseEntity<ApiResult<Void>> readOne(Long userId, Long notificationId);

ResponseEntity<ApiResult<Void>> readAll(Long userId);
```

`NotificationController.java`:

```java
package plana.replan.domain.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.notification.dto.NotificationListResponse;
import plana.replan.domain.notification.dto.UnreadCountResponse;
import plana.replan.domain.notification.entity.NotificationCategory;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

  private final NotificationService notificationService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<NotificationListResponse>> getNotifications(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) NotificationCategory category,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(
        ApiResult.ok(notificationService.getList(userId, category, cursor, size)));
  }

  @Override
  @GetMapping("/unread-count")
  public ResponseEntity<ApiResult<UnreadCountResponse>> getUnreadCount(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(notificationService.getUnreadCount(userId)));
  }

  @Override
  @PatchMapping("/{notificationId}/read")
  public ResponseEntity<ApiResult<Void>> readOne(
      @AuthenticationPrincipal Long userId, @PathVariable Long notificationId) {
    notificationService.markRead(userId, notificationId);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/read-all")
  public ResponseEntity<ApiResult<Void>> readAll(@AuthenticationPrincipal Long userId) {
    notificationService.markAllRead(userId);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
```

> 설정 API(GET/PATCH `/api/notifications/settings`)도 이 컨트롤러(또는 별도 SettingController)에 추가하고 `NotificationSettingService`에 위임한다. Docs에도 동일 규칙으로 문서화. (경로 충돌 주의: `/settings`는 `/{notificationId}/read`와 안 겹친다.)

- [ ] **Step 7: 설정 API 추가**

`NotificationController`에 메서드 2개 추가(생성자에 `NotificationSettingService` 주입):

```java
  @Override
  @GetMapping("/settings")
  public ResponseEntity<ApiResult<NotificationSettingResponse>> getSettings(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(notificationSettingService.get(userId)));
  }

  @Override
  @PatchMapping("/settings")
  public ResponseEntity<ApiResult<NotificationSettingResponse>> updateSettings(
      @AuthenticationPrincipal Long userId,
      @org.springframework.web.bind.annotation.RequestBody NotificationSettingUpdateRequest request) {
    return ResponseEntity.ok(ApiResult.ok(notificationSettingService.update(userId, request)));
  }
```

Docs 인터페이스에도 대응 시그니처/문서를 추가한다.

- [ ] **Step 8: 전체 컴파일 + 관련 테스트 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.*'`
Expected: PASS (모든 notification 테스트)

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification src/test/java/plana/replan/domain/notification/service/NotificationServiceQueryTest.java
git commit -m "Feat: 알림함 목록·안읽은개수·읽음처리·설정 조회/변경 API 추가"
```

---

## Phase 3 — 트리거 (자정 스케줄러 + 리포트 이벤트)

### Task 9: 마감 임박/실패 대상 조회 쿼리 추가

**Files:**
- Modify: `domain/todo/repository/TodoRepository.java`
- Test: `src/test/java/plana/replan/domain/todo/repository/TodoNotificationQueryTest.java` (`@DataJpaTest`)

**Interfaces:**
- Produces:
  - `List<Todo> findPinnedDueBetween(LocalDateTime start, LocalDateTime end)` — 핀 고정 + 미완료 + 활성 + 부모없음 + dueDate ∈ [start,end)
  - `List<Todo> findFailedBetween(LocalDateTime start, LocalDateTime end)` — 미완료 + 활성 + 부모없음 + replan 없음 + dueDate ∈ [start,end)

- [ ] **Step 1: 실패하는 테스트 작성**

`TodoNotificationQueryTest.java` — `@DataJpaTest`로 두 쿼리를 검증한다. 핀+내일마감 투두 1개와 실패(어제 마감·미완료) 투두 1개를 저장하고, 각 쿼리가 정확히 그 건만 반환하는지 확인. (User, Todo 빌더 사용; `ReflectionTestUtils` 불필요, 저장 후 조회.)

```java
package plana.replan.domain.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@DataJpaTest
class TodoNotificationQueryTest {

  @Autowired private TodoRepository todoRepository;
  @Autowired private UserRepository userRepository;

  private User newUser() {
    return userRepository.save(
        User.builder()
            .email("q@q.com")
            .nickname("n")
            .role(Role.USER)
            .provider(Provider.LOCAL)
            .build());
  }

  @Test
  @DisplayName("핀 고정 + 미완료 + 내일 마감 투두만 찾는다")
  void findPinnedDueBetween() {
    User user = newUser();
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    Todo pinnedDueTomorrow =
        Todo.builder()
            .title("핀")
            .user(user)
            .isPinned(true)
            .dueDate(tomorrow.atTime(18, 0))
            .build();
    Todo notPinned =
        Todo.builder().title("일반").user(user).dueDate(tomorrow.atTime(18, 0)).build();
    todoRepository.save(pinnedDueTomorrow);
    todoRepository.save(notPinned);

    List<Todo> result =
        todoRepository.findPinnedDueBetween(
            tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());

    assertThat(result).extracting(Todo::getTitle).containsExactly("핀");
  }

  @Test
  @DisplayName("어제 마감 + 미완료 + 리플랜 없음 투두만 찾는다")
  void findFailedBetween() {
    User user = newUser();
    LocalDate yesterday = LocalDate.now().minusDays(1);
    Todo failed =
        Todo.builder().title("실패").user(user).dueDate(yesterday.atTime(10, 0)).build();
    todoRepository.save(failed);

    List<Todo> result =
        todoRepository.findFailedBetween(
            yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());

    assertThat(result).extracting(Todo::getTitle).containsExactly("실패");
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.todo.repository.TodoNotificationQueryTest'`
Expected: FAIL — 메서드 없음.

- [ ] **Step 3: 쿼리 추가**

`TodoRepository.java` 에 추가:

```java
  @Query(
      "SELECT t FROM Todo t WHERE t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isPinned = true AND t.isActive = true"
          + " AND t.dueDate >= :start AND t.dueDate < :end")
  List<Todo> findPinnedDueBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT t FROM Todo t WHERE t.parent IS NULL AND t.isCompleted = false"
          + " AND t.isActive = true AND t.replan IS NULL"
          + " AND t.dueDate >= :start AND t.dueDate < :end")
  List<Todo> findFailedBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.todo.repository.TodoNotificationQueryTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/plana/replan/domain/todo/repository/TodoRepository.java src/test/java/plana/replan/domain/todo/repository/TodoNotificationQueryTest.java
git commit -m "Feat: 마감 임박·실패 투두 조회 쿼리 추가"
```

---

### Task 10: 자정 트리거 서비스 + 스케줄러

**Files:**
- Create: `service/NotificationTriggerService.java`
- Create: `scheduler/NotificationScheduler.java`
- Test: `src/test/java/plana/replan/domain/notification/service/NotificationTriggerServiceTest.java`

**Interfaces:**
- Consumes: `TodoRepository.findPinnedDueBetween/findFailedBetween`, `NotificationService.send`, `Clock`, `Todo`.
- Produces:
  - `NotificationTriggerService.sendDueSoon()` — 내일 마감 핀 투두마다 `TODO_DUE_SOON` 발송. title=`'{제목}' 투두`, body=고정 문구, target=TODO/todoId.
  - `NotificationTriggerService.sendFailedReplan()` — 사용자별 어제 실패 투두 개수>0이면 `TODO_FAILED_REPLAN` 요약 1건. title=`오늘 실패한 투두 {N}개 있어요.`, body=고정 문구, target=REPLAN/null.

- [ ] **Step 1: 실패하는 테스트 작성**

`NotificationTriggerServiceTest.java`:

```java
package plana.replan.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationTriggerServiceTest {

  @Mock private TodoRepository todoRepository;
  @Mock private NotificationService notificationService;

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  private NotificationTriggerService service() {
    return new NotificationTriggerService(todoRepository, notificationService, clock);
  }

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("내일 마감 핀 투두마다 마감 임박 알림을 보낸다")
  void sendDueSoon() {
    User u = user();
    Todo t = Todo.builder().title("영단어").user(u).build();
    given(todoRepository.findPinnedDueBetween(any(), any())).willReturn(List.of(t));

    service().sendDueSoon();

    verify(notificationService)
        .send(
            eq(u),
            eq(NotificationType.TODO_DUE_SOON),
            eq("'영단어' 투두"),
            eq("주요 투두로 설정한 투두의 마감 시간이 하루 남았어요."),
            eq(TargetType.TODO),
            any());
  }

  @Test
  @DisplayName("실패 투두는 사용자별 개수로 묶어 요약 1건을 보낸다")
  void sendFailedReplanGrouped() {
    User u = user();
    Todo t1 = Todo.builder().title("a").user(u).build();
    Todo t2 = Todo.builder().title("b").user(u).build();
    given(todoRepository.findFailedBetween(any(), any())).willReturn(List.of(t1, t2));

    service().sendFailedReplan();

    verify(notificationService, times(1))
        .send(
            eq(u),
            eq(NotificationType.TODO_FAILED_REPLAN),
            eq("오늘 실패한 투두 2개 있어요."),
            eq("실패한 투두의 리플랜을 진행해보세요."),
            eq(TargetType.REPLAN),
            isNull());
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationTriggerServiceTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: NotificationTriggerService 구현**

```java
package plana.replan.domain.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.todo.entity.Todo;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.entity.User;

@Service
@RequiredArgsConstructor
public class NotificationTriggerService {

  private static final String DUE_SOON_BODY = "주요 투두로 설정한 투두의 마감 시간이 하루 남았어요.";
  private static final String FAILED_BODY = "실패한 투두의 리플랜을 진행해보세요.";

  private final TodoRepository todoRepository;
  private final NotificationService notificationService;
  private final Clock clock;

  /** 내일 마감인 핀 고정 투두마다 마감 임박 알림 1건씩. */
  public void sendDueSoon() {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime start = today.plusDays(1).atStartOfDay();
    LocalDateTime end = today.plusDays(2).atStartOfDay();

    for (Todo todo : todoRepository.findPinnedDueBetween(start, end)) {
      notificationService.send(
          todo.getUser(),
          NotificationType.TODO_DUE_SOON,
          "'" + todo.getTitle() + "' 투두",
          DUE_SOON_BODY,
          TargetType.TODO,
          todo.getId());
    }
  }

  /** 어제 마감 지나고 못 끝낸 투두를 사용자별 개수로 묶어 요약 1건씩. */
  public void sendFailedReplan() {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime start = today.minusDays(1).atStartOfDay();
    LocalDateTime end = today.atStartOfDay();

    Map<User, Integer> countByUser = new LinkedHashMap<>();
    for (Todo todo : todoRepository.findFailedBetween(start, end)) {
      countByUser.merge(todo.getUser(), 1, Integer::sum);
    }

    countByUser.forEach(
        (user, count) ->
            notificationService.send(
                user,
                NotificationType.TODO_FAILED_REPLAN,
                "오늘 실패한 투두 " + count + "개 있어요.",
                FAILED_BODY,
                TargetType.REPLAN,
                null));
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.service.NotificationTriggerServiceTest'`
Expected: PASS

- [ ] **Step 5: 스케줄러 작성**

`NotificationScheduler.java`:

```java
package plana.replan.domain.notification.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.service.NotificationTriggerService;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationTriggerService notificationTriggerService;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void runDailyNotifications() {
    notificationTriggerService.sendDueSoon();
    notificationTriggerService.sendFailedReplan();
  }
}
```

> `@EnableScheduling`이 이미 켜져 있는지 확인한다(기존 `RoutineTodoScheduler`가 동작하므로 보통 켜져 있음). 없으면 `ReplanApplication` 또는 config에 `@EnableScheduling` 추가.

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/service/NotificationTriggerService.java src/main/java/plana/replan/domain/notification/scheduler src/test/java/plana/replan/domain/notification/service/NotificationTriggerServiceTest.java
git commit -m "Feat: 자정 마감 임박·실패 요약 알림 스케줄러 추가"
```

---

### Task 11: 월간 리포트 도착 이벤트 연결

**Files:**
- Create: `event/MonthlyReportCreatedEvent.java`
- Create: `event/MonthlyReportNotificationListener.java`
- Modify: `domain/monthlyreport/batch/MonthlyReportItemWriter.java` (저장 후 이벤트 발행)
- Test: `src/test/java/plana/replan/domain/notification/event/MonthlyReportNotificationListenerTest.java`

**Interfaces:**
- Produces:
  - `record MonthlyReportCreatedEvent(Long userId, Long reportId, int month)`
  - `MonthlyReportNotificationListener.handle(MonthlyReportCreatedEvent)` — 사용자 조회 후 `REPORT_READY` 발송. title=`이번 달 리포트가 나왔어요.`, body=`{month}월 리포트를 확인해보세요.`, target=REPORT/reportId.
- Consumes: `MonthlyReportItemWriter`는 `ApplicationEventPublisher`로 새 리포트 저장 시 이벤트 발행.

- [ ] **Step 1: 이벤트 레코드 작성**

`MonthlyReportCreatedEvent.java`:

```java
package plana.replan.domain.notification.event;

public record MonthlyReportCreatedEvent(Long userId, Long reportId, int month) {}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`MonthlyReportNotificationListenerTest.java`:

```java
package plana.replan.domain.notification.event;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MonthlyReportNotificationListenerTest {

  @Mock private UserRepository userRepository;
  @Mock private NotificationService notificationService;
  @InjectMocks private MonthlyReportNotificationListener listener;

  @Test
  @DisplayName("리포트 생성 이벤트를 받으면 리포트 도착 알림을 보낸다")
  void handle() {
    User u =
        User.builder()
            .email("a@a.com")
            .nickname("n")
            .role(Role.USER)
            .provider(Provider.LOCAL)
            .build();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    listener.handle(new MonthlyReportCreatedEvent(1L, 55L, 6));

    verify(notificationService)
        .send(
            eq(u),
            eq(NotificationType.REPORT_READY),
            eq("이번 달 리포트가 나왔어요."),
            eq("6월 리포트를 확인해보세요."),
            eq(TargetType.REPORT),
            eq(55L));
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.event.MonthlyReportNotificationListenerTest'`
Expected: FAIL — 리스너 없음.

- [ ] **Step 4: 리스너 구현**

`MonthlyReportNotificationListener.java`:

```java
package plana.replan.domain.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.entity.NotificationType;
import plana.replan.domain.notification.entity.TargetType;
import plana.replan.domain.notification.service.NotificationService;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class MonthlyReportNotificationListener {

  private final UserRepository userRepository;
  private final NotificationService notificationService;

  @EventListener
  public void handle(MonthlyReportCreatedEvent event) {
    User user =
        userRepository
            .findById(event.userId())
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    notificationService.send(
        user,
        NotificationType.REPORT_READY,
        "이번 달 리포트가 나왔어요.",
        event.month() + "월 리포트를 확인해보세요.",
        TargetType.REPORT,
        event.reportId());
  }
}
```

> `@EventListener`(동기)로 두면 배치 트랜잭션 안에서 실행된다. 발송 실패가 배치를 깨지 않도록 `NotificationService.send`는 이미 푸시 예외를 삼킨다. 만약 알림함 저장 트랜잭션까지 배치와 분리하고 싶으면 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`로 바꾼다(선택, v1은 동기로 시작).

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'plana.replan.domain.notification.event.MonthlyReportNotificationListenerTest'`
Expected: PASS

- [ ] **Step 6: Writer에서 이벤트 발행**

`MonthlyReportItemWriter.java` 수정:
- 생성자 의존성에 `ApplicationEventPublisher eventPublisher` 추가(`@RequiredArgsConstructor`이므로 `private final` 필드만 추가).
- 새 리포트 저장 분기(`save(...)`)에서 저장 결과를 받아 이벤트 발행. 기존 `ifPresentOrElse`의 else 람다를 아래처럼 바꾼다:

```java
              () -> {
                MonthlyReport saved =
                    monthlyReportRepository.save(
                        MonthlyReport.builder()
                            .user(data.user())
                            .reportMonth(data.reportMonth())
                            .totalTodos(stats.totalTodos())
                            .completedTodos(stats.completedTodos())
                            .achievementRate(stats.achievementRate())
                            .prevMonthDiff(stats.prevMonthDiff())
                            .replanCount(stats.replanCount())
                            .replanAchievementEffect(stats.replanAchievementEffect())
                            .analysisData(stats.analysisData())
                            .aiInsight(data.aiInsight())
                            .build());
                eventPublisher.publishEvent(
                    new plana.replan.domain.notification.event.MonthlyReportCreatedEvent(
                        data.user().getId(),
                        saved.getId(),
                        data.reportMonth().getMonthValue()));
              });
```

필요한 import: `org.springframework.context.ApplicationEventPublisher`, `plana.replan.domain.monthlyreport.entity.MonthlyReport`(이미 있음).

> 갱신(update) 분기에서는 이벤트를 발행하지 않는다(이미 알린 리포트 재계산 시 중복 알림 방지). 스펙 7-2의 "리포트가 만들어지는 순간"에 맞춘다.

- [ ] **Step 7: 전체 테스트 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (전체 그린)

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/plana/replan/domain/notification/event src/main/java/plana/replan/domain/monthlyreport/batch/MonthlyReportItemWriter.java src/test/java/plana/replan/domain/notification/event
git commit -m "Feat: 월간 리포트 생성 시 리포트 도착 알림 발송 연결"
```

---

## 로컬 통합 테스트 (코드 완료 후, CLAUDE.md 작업 프로세스 2단계)

1. Docker로 postgres/redis 기동, `FIREBASE_SERVICE_ACCOUNT_JSON` 환경변수 설정 후 `./gradlew bootRun --args='--spring.profiles.active=local'`.
2. 로그인(`claude-test@replan.local`)으로 accessToken 발급.
3. `POST /api/notifications/tokens` 로 실제(혹은 테스트) FCM 토큰 등록 → DB `device_token` 확인.
4. 설정 `GET/PATCH /api/notifications/settings` 동작 확인(끄면 발송 안 됨).
5. 트리거 수동 검증: 내일 마감 핀 투두 / 어제 마감 미완료 투두를 만들어 두고 `NotificationTriggerService` 메서드를 임시 dev 엔드포인트나 테스트로 호출 → 알림함(`GET /api/notifications`)·안읽음 카운트·실제 FCM 수신 확인.
6. 월간 리포트 배치 실행 → `REPORT_READY` 알림 생성 확인.

---

## Self-Review 메모

- 스펙 커버리지: 알림 3종(Task 10·11), 토큰(2·3·4), 알림함(5·8), 설정 user 칼럼(6), 발송 엔진·죽은토큰(7), 카테고리 탭(5·8), 자정 발송(10), 리포트 이벤트(11) — 모두 태스크 존재.
- 타입 일관성: `send(User, NotificationType, String, String, TargetType, Long)` 시그니처가 Task 7 정의 → 10·11에서 동일 사용. `PushResult`/`PushSender` Task 1 정의 → 7에서 사용. `findPinnedDueBetween/findFailedBetween` Task 9 정의 → 10에서 사용.
- 플레이스홀더: 컨트롤러 Docs의 표 본문은 형식을 지정했고 시그니처/응답코드를 명시했다(스웨거 규칙 위임).
```

# 알림(푸시) 기능 설계서

> 작성일: 2026-06-19
> 한 줄 요약: 사용자 폰/브라우저로 푸시 알림을 보내고, 앱 안에도 알림함을 쌓는 기능. 배달은 FCM으로 한다.

## 1. 왜 만드나 (배경)

리플랜 앱에 "진짜 알림"이 없다. 사용자가 앱을 켜지 않으면 마감이 다가오는지, 리포트가 나왔는지,
실패한 할 일이 있는지 알 수가 없다. 그래서 **서버가 정해진 순간에 사용자 폰으로 알림을 밀어주는** 기능을 만든다.
클라이언트는 현재 PWA(웹앱)이고, 앞으로 앱스토어·플레이스토어에 네이티브 앱을 올릴 계획이 있다.

## 2. 무엇을 만드나 (보낼 알림 3종)

| 종류(코드명) | 탭(분류) | 제목 | 내용 | 언제 보내나 | 누르면 가는 곳 |
|------|------|------|------|------|------|
| 마감 임박 `TODO_DUE_SOON` | 투두 `TODO` | `'{투두 제목}' 투두` | 주요 투두로 설정한 투두의 마감 시간이 하루 남았어요. | 매일 자정, **핀 고정 + 미완료 + 내일 마감**인 투두마다 각각 1건 | 해당 투두 화면 |
| 실패 리플랜 `TODO_FAILED_REPLAN` | 투두 `TODO` | `오늘 실패한 투두 N개 있어요.` | 실패한 투두의 리플랜을 진행해보세요. | 매일 자정, **어제 마감 지났는데 못 끝낸** 투두 개수를 묶어 요약 1건 | 실패/리플랜 목록 화면 |
| 리포트 도착 `REPORT_READY` | 통계 `STATS` | 이번 달 리포트가 나왔어요. | {N}월 리포트를 확인해보세요. | 월간 리포트 배치가 리포트를 저장할 때 | 해당 리포트 화면 |

### 핵심 정의

- **"주요 투두"** = 핀 고정한 투두(`Todo.isPinned = true`). 마감 임박 알림은 핀 고정한 투두에만 보낸다.
- **"마감 하루 전"** = 자정 cron 기준, **마감일(dueDate)이 '내일'에 속하는** 미완료·활성 투두.
  (마감 시각이 분 단위까지 정확히 24시간 전이 아니라, "내일 마감인 것"을 자정에 알려주는 방식. 기획 의도 "자정 직후 발송"에 맞춘 단순화.)
- **"실패한 투두"** = 마감일(dueDate)이 **어제** 안에 있었고, 아직 **완료되지 않았고**(`isCompleted=false`),
  **활성 상태**(`isActive=true`)이며, **아직 리플랜이 연결되지 않은**(`replan IS NULL`) 투두. 핀 여부는 무관. (기획 확인 완료)
- 알림 시각은 모두 **자정(`0 0 0 * * *`, Asia/Seoul)** 발송. 기존 `RoutineTodoScheduler`와 동일한 시각대.

## 3. 어떻게 배달하나 (채널: FCM)

서버는 사용자 폰에 직접 알림을 못 꽂는다. 반드시 중간 배달부(구글/애플 푸시 서버)를 거친다.
배달부로 **FCM(Firebase Cloud Messaging)**을 쓴다. 이유:

- 웹(PWA) + 안드로이드 + 아이폰을 **하나의 시스템으로** 처리한다.
- 지금은 PWA만 쓰지만, 나중에 네이티브 앱을 올려도 **같은 인프라를 그대로 재사용**한다(두 번 일 안 함).

발송 흐름:

```text
① 사용자 브라우저가 알림 허락 → FCM이 그 기기 전용 '주소표(토큰)' 발급
② 프론트가 토큰을 우리 서버 등록 API로 전달 → device_token 표에 저장
③ 보낼 때가 되면 → 서버가 FCM에 "이 토큰으로 이 내용 보내줘" 요청
④ FCM이 사용자 기기에 푸시 표시
```

> 공통 한계(애플 정책): **아이폰은 PWA를 '홈 화면에 추가'로 설치한 경우(iOS 16.4+)에만** 푸시가 온다.
> 사파리 탭만 열어둔 상태에서는 안 온다. 안드로이드는 탭만 열어도 된다. → 프론트에서 설치 안내 필요.

## 4. 데이터 구조 (DB 변경)

새 표 **2개** + 기존 user 테이블에 **칼럼 3개** 추가. (설정값은 사용자당 한 줄뿐이라 별도 표 없이 user에 둔다.)

### 4-1. 새 표: device_token (폰 주소록)

한 사용자가 여러 기기를 가질 수 있으므로 1:N. 토큰은 자주 만료·교체되어 추가/삭제가 잦다.

| 칼럼 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| user_id | bigint FK → user | 누구의 기기인지 |
| token | text, **고유** | FCM이 발급한 기기 주소표. 같은 토큰 중복 저장 금지 |
| platform | varchar (enum: WEB / ANDROID / IOS) | 기기 종류 |
| created_at / updated_at | timestamp | 등록·갱신 시각 (BaseTimeEntity) |

- 같은 token이 다시 등록되면 새로 만들지 않고 **갱신(upsert)**한다.

### 4-2. 새 표: notification (앱 안 알림함)

시간이 갈수록 계속 쌓이는 기록이므로 1:N, 별도 표 필수.

| 칼럼 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| user_id | bigint FK → user | 받는 사람 |
| category | varchar (enum: TODO / STATS / ETC) | 알림함 탭 분류 |
| type | varchar (enum: TODO_DUE_SOON / TODO_FAILED_REPLAN / REPORT_READY) | 알림 종류 |
| title | varchar | 굵게 보이는 제목 |
| body | varchar | 회색 본문 |
| target_type | varchar (enum: TODO / REPORT / REPLAN), nullable | 누르면 갈 화면 종류 |
| target_id | bigint, nullable | 누르면 갈 대상 id (투두 id, 리포트 id 등). 요약 알림은 null |
| is_read | boolean, 기본 false | 읽음 여부(빨간 점) |
| created_at | timestamp | 생성 시각. 프론트가 "10시간 전" 계산에 사용 |

### 4-3. user 테이블에 칼럼 추가 (알림 설정 스위치 3개)

사용자당 1:1이라 표를 새로 파지 않고 user에 boolean 칼럼으로 둔다. 기본값 모두 켜짐(true).

| 칼럼 | 타입 | 기본값 | 설명 |
|------|------|------|------|
| notify_todo_due | boolean | true | 마감 임박 알림 받기 |
| notify_todo_failed | boolean | true | 실패 리플랜 알림 받기 |
| notify_report | boolean | true | 리포트 도착 알림 받기 |

## 5. API (notification 도메인 신설)

`domain/notification` 패키지를 새로 만든다. Controller는 프로젝트 규칙대로 Docs 인터페이스 + 구현 분리.

| 메서드 | 경로 | 설명 |
|------|------|------|
| POST | `/api/notifications/tokens` | 기기 주소표(FCM 토큰) 등록/갱신. body: `{ token, platform }` |
| DELETE | `/api/notifications/tokens` | 기기 주소표 삭제(로그아웃 시). body: `{ token }` |
| GET | `/api/notifications` | 알림함 목록. query: `category`(선택, 없으면 전체), `cursor`, `size`. 무한스크롤 |
| GET | `/api/notifications/unread-count` | 안 읽은 알림 개수(빨간 점/배지용) |
| PATCH | `/api/notifications/{id}/read` | 알림 1건 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 처리 |
| GET | `/api/notifications/settings` | 내 알림 설정(스위치 3개) 조회 |
| PATCH | `/api/notifications/settings` | 알림 설정 변경 |

> 무한스크롤·Swagger 문서는 `.claude/rules/swagger.md` 규칙을 따른다(JSON 타입 표기, 필수/선택 이모지, 발생 가능한 모든 에러 예시 등).

## 6. 보내는 엔진 (NotificationService)

모든 알림은 한 진입점 `NotificationService.send(user, type, title, body, targetType, targetId)`를 통한다.

순서:

1. 해당 user의 **설정 스위치 확인** — 꺼져 있으면 아무것도 안 함.
2. **notification 표에 한 줄 저장** (앱 안 알림함).
3. 그 user의 **device_token 전부 조회 → FCM으로 발송**.
4. FCM이 "이 토큰은 죽었다(UNREGISTERED/INVALID_ARGUMENT)"고 응답하면 **해당 토큰 자동 삭제**.

원칙:

- **푸시 발송 실패가 트리거(배치·스케줄러)를 깨면 안 된다.** FCM 호출은 try/catch로 감싸고, 실패해도
  알림함 저장과 다음 사용자 처리는 계속된다.
- FCM 연동은 `FcmClient`(Firebase Admin SDK 래퍼)로 분리해 교체·테스트가 쉽게 한다.

## 7. 언제 부르나 (트리거 3곳)

### 7-1. 자정 스케줄러 (마감 임박 + 실패 리플랜)

`NotificationScheduler`를 신설. `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`.

- **마감 임박**: 모든 사용자에 대해 `핀 고정 + 미완료 + 내일 마감`인 투두를 찾아, 투두마다 `TODO_DUE_SOON` 1건씩 발송.
  - 새 쿼리 필요: 핀 고정 + 미완료 + 활성 + dueDate가 [내일 00:00, 모레 00:00) 범위.
- **실패 리플랜**: 모든 사용자에 대해 `어제 마감 + 미완료 + 활성 + 리플랜 없음`인 투두 **개수**를 세어,
  개수 > 0이면 `TODO_FAILED_REPLAN` 요약 1건 발송.
  - 새 쿼리 필요: 위 조건의 count(또는 목록).

> 기존 `RoutineTodoScheduler`도 자정에 돈다. 둘은 보는 날짜가 달라(오늘 생성 / 내일·어제 조회) 서로 독립이지만,
> 실행 순서를 보장할 필요가 없는지 구현 시 한 번 점검한다.

### 7-2. 리포트 도착 (이벤트로 느슨하게 연결)

월간 리포트는 Spring Batch(`MonthlyReportItemWriter`)가 사용자별로 저장한다.
배치 코드가 notification 도메인을 직접 의존하지 않도록 **이벤트**로 연결한다.

- 리포트 저장 시 `MonthlyReportCreatedEvent(userId, reportId, reportMonth)` 발행.
- notification 도메인의 `@TransactionalEventListener`(커밋 후)가 받아 `REPORT_READY` 발송.

## 8. 코드 밖 준비 (사람이 콘솔에서 직접)

이게 "뭐부터 하냐"의 실제 출발점이다. 코드 짜기 전에 아래가 있어야 로컬 테스트가 된다.

1. **Firebase 프로젝트 생성**(무료) → Cloud Messaging 활성화.
2. **서버용 서비스 계정 비밀키(JSON) 발급** → 서버에 비밀값(env/secret)으로 저장.
   서버가 FCM에 "정당한 발송자"임을 증명하는 데 쓴다. **절대 git에 커밋하지 않는다.**
3. **웹푸시용 공개키(VAPID 키쌍) 발급** → 프론트에 전달.

## 9. 출시 로드맵 (확정: 안드로이드 + 아이폰 둘 다 정식 출시)

기획 확정 사항: 두 스토어 모두 정식 출시한다. 그래서 아이폰 네이티브 푸시(APNs)는 "혹시"가 아니라 **확정 로드맵**이다.
다만 **백엔드는 FCM 하나로 웹·안드·아이폰을 다 커버**하므로 서버 코드는 추가 분기 없이 그대로 재사용한다.
`device_token.platform`(WEB/ANDROID/IOS) 칸이 이 확장을 위한 자리다.

| 단계 | 클라이언트 작업 | 백엔드 영향 | 행정 |
|------|------|------|------|
| 지금 (PWA) | 서비스워커 + 웹푸시 토큰 등록 | 본 설계서 그대로 | 무료 |
| 안드로이드 출시 | PWA를 TWA로 감싸기(웹푸시 그대로 동작) | 없음 | $25 1회 + 신원확인 + (신규계정)테스터 12명·14일 |
| 아이폰 출시 | 네이티브로 감싸기 + **네이티브 푸시 플러그인(APNs)** 토큰 등록 | 없음(Firebase에 APNs 인증키 업로드만) | $99/년 + 신원확인 |

## 9-1. 프론트(PWA) 작업 — 백엔드와 별개

1. `firebase-messaging-sw.js` 서비스워커 추가 + Firebase JS SDK 초기화.
2. 알림 권한 요청(`Notification.requestPermission()`) → 토큰 발급(`getToken()`) → 우리 **등록 API** 호출.
3. 알림을 누르면 `target_type`/`target_id`로 해당 화면 이동(딥링크).
4. 앱 안 알림함 UI: 탭(투두/통계/기타) + 목록 무한스크롤 + 빨간 점(안읽음) + "N시간 전" 표시.
5. 아이폰 사용자에게 "홈 화면에 추가" 설치 안내(PWA 단계 한정).

## 10. 범위에서 빼는 것 (YAGNI)

- 조용한 시간대(밤에 안 보내기), 이메일·문자 알림, 알림 묶음/그룹핑 등은 v1 범위 밖.
- 알림 보관 기간 정리(오래된 알림 자동 삭제)도 v1에서는 안 한다(데이터 쌓이면 나중에 배치 추가).

## 11. 구현 단계 제안 (플랜에서 쪼갤 단위)

1. **1단계 — 인프라/주소록/엔진**: FCM 연동(FcmClient), device_token 표·등록/삭제 API, NotificationService 기본 발송.
2. **2단계 — 알림함/설정**: notification 표, 목록/안읽음/읽음 API, user 설정 칼럼 + 설정 조회/변경 API.
3. **3단계 — 트리거**: 자정 스케줄러(마감 임박·실패 요약) + 리포트 이벤트 연결.

각 단계마다 로컬 서버에서 실제 발송까지 전수 테스트한다(CLAUDE.md 작업 프로세스 준수).
